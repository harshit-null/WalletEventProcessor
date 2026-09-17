package com.harshit.walleteventprocessor.service.impl;

import com.harshit.walleteventprocessor.dto.PaymentRequest;
import com.harshit.walleteventprocessor.dto.PaymentResponse;
import com.harshit.walleteventprocessor.entity.EventType;
import com.harshit.walleteventprocessor.entity.TransactionStatus;
import com.harshit.walleteventprocessor.entity.Wallet;
import com.harshit.walleteventprocessor.entity.WalletEvent;
import com.harshit.walleteventprocessor.exception.DuplicateTransactionException;
import com.harshit.walleteventprocessor.exception.WalletNotFoundException;
import com.harshit.walleteventprocessor.repository.WalletEventRepository;
import com.harshit.walleteventprocessor.repository.WalletRepository;
import com.harshit.walleteventprocessor.service.PaymentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PaymentServiceImpl implements PaymentService {

    private final WalletRepository walletRepository;
    private final WalletEventRepository walletEventRepository;

    public PaymentServiceImpl(
            WalletRepository walletRepository,
            WalletEventRepository walletEventRepository) {

        this.walletRepository = walletRepository;
        this.walletEventRepository = walletEventRepository;
    }

    @Override
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request) {

        // 1. Lock the wallet first.
        // Concurrent transactions for the same wallet
        // must wait for each other.
        Wallet wallet = walletRepository
                .findByUserIdWithLock(request.userId())
                .orElseThrow(() ->
                        new WalletNotFoundException("Wallet not found for user: " + request.userId()));

        // 2. Check idempotency after acquiring the lock.
        if (walletEventRepository
                .existsByIdempotencyKey(request.transactionId().toString())) {

            throw new DuplicateTransactionException(
                    "Transaction already processed with id: " + request.transactionId());
        }

        LocalDateTime timestamp = LocalDateTime.now();

        // 3. Handle DEBIT
        if (request.type() == EventType.DEBIT) {

            // Insufficient balance
            if (wallet.getBalance()
                    .compareTo(request.amount()) < 0) {

                WalletEvent failedEvent = WalletEvent.builder()
                        .idempotencyKey(
                                request.transactionId().toString())
                        .wallet(wallet)
                        .eventType(request.type())
                        .amount(request.amount())
                        .status(TransactionStatus.FAILED)
                        .createdAt(timestamp)
                        .build();

                walletEventRepository.save(failedEvent);

                return new PaymentResponse(
                        request.transactionId(),
                        request.userId(),
                        request.type().name(),
                        request.amount(),
                        TransactionStatus.FAILED.name(),
                        wallet.getBalance(),
                        timestamp
                );
            }

            // Sufficient balance
            wallet.setBalance(
                    wallet.getBalance()
                            .subtract(request.amount())
            );
        }

        // 4. Handle CREDIT
        else if (request.type() == EventType.CREDIT) {

            wallet.setBalance(
                    wallet.getBalance()
                            .add(request.amount())
            );
        }

        // 5. Save updated wallet
        walletRepository.save(wallet);

        // 6. Record successful transaction
        WalletEvent event = WalletEvent.builder()
                .idempotencyKey(
                        request.transactionId().toString())
                .wallet(wallet)
                .eventType(request.type())
                .amount(request.amount())
                .status(TransactionStatus.SUCCESS)
                .createdAt(timestamp)
                .build();

        walletEventRepository.save(event);

        // 7. Return response
        return new PaymentResponse(
                request.transactionId(),
                request.userId(),
                request.type().name(),
                request.amount(),
                TransactionStatus.SUCCESS.name(),
                wallet.getBalance(),
                timestamp
        );
    }
}