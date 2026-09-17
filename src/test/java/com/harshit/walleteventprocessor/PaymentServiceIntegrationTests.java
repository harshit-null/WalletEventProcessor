package com.harshit.walleteventprocessor;

import com.harshit.walleteventprocessor.dto.PaymentRequest;
import com.harshit.walleteventprocessor.dto.PaymentResponse;
import com.harshit.walleteventprocessor.entity.EventType;
import com.harshit.walleteventprocessor.entity.Wallet;
import com.harshit.walleteventprocessor.exception.DuplicateTransactionException;
import com.harshit.walleteventprocessor.repository.WalletEventRepository;
import com.harshit.walleteventprocessor.repository.WalletRepository;
import com.harshit.walleteventprocessor.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class PaymentServiceIntegrationTests {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletEventRepository walletEventRepository;

    private UUID userId;
    private UUID transactionId;

    @BeforeEach
    void setUp() {

        walletEventRepository.deleteAll();
        walletRepository.deleteAll();

        userId = UUID.randomUUID();
        transactionId = UUID.randomUUID();

        Wallet wallet = Wallet.builder()
                .userId(userId)
                .balance(new BigDecimal("1000.00"))
                .build();

        walletRepository.save(wallet);
    }

    @Test
    @DisplayName("Should successfully process a valid debit transaction")
    void testHappyPathSingleValidDebitTransaction() {

        PaymentRequest request = new PaymentRequest(
                transactionId,
                userId,
                new BigDecimal("100.00"),
                EventType.DEBIT
        );

        PaymentResponse response =
                paymentService.processPayment(request);

        assertNotNull(response);

        assertEquals(
                "SUCCESS",
                response.status()
        );

        assertEquals(
                new BigDecimal("900.00"),
                response.newBalance()
        );

        assertEquals(
                new BigDecimal("100.00"),
                response.amount()
        );

        Wallet wallet =
                walletRepository.findByUserId(userId)
                        .orElseThrow();

        assertEquals(
                new BigDecimal("900.00"),
                wallet.getBalance()
        );

        assertTrue(
                walletEventRepository
                        .existsByIdempotencyKey(
                                transactionId.toString()
                        )
        );
    }

    @Test
    @DisplayName("Should reject duplicate transaction when 3 identical requests arrive concurrently")
    void testIdempotency3IdenticalTransactionIdsConcurrently()
            throws InterruptedException {

        PaymentRequest request = new PaymentRequest(
                transactionId,
                userId,
                new BigDecimal("100.00"),
                EventType.DEBIT
        );

        int numberOfRequests = 3;

        CountDownLatch startLatch =
                new CountDownLatch(1);

        CountDownLatch endLatch =
                new CountDownLatch(numberOfRequests);

        ExecutorService executor =
                Executors.newFixedThreadPool(numberOfRequests);

        AtomicInteger successCount =
                new AtomicInteger(0);

        AtomicInteger conflictCount =
                new AtomicInteger(0);

        AtomicReference<Throwable> unexpectedException =
                new AtomicReference<>();

        for (int i = 0; i < numberOfRequests; i++) {

            executor.submit(() -> {

                try {

                    startLatch.await();

                    paymentService.processPayment(request);

                    successCount.incrementAndGet();

                } catch (DuplicateTransactionException e) {

                    conflictCount.incrementAndGet();

                    assertTrue(
                            e.getMessage()
                                    .contains("already processed")
                    );

                } catch (Throwable e) {

                    unexpectedException.compareAndSet(
                            null,
                            e
                    );

                } finally {

                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();

        endLatch.await();

        executor.shutdown();

        assertNull(
                unexpectedException.get(),
                "No unexpected exception should occur"
        );

        assertEquals(
                1,
                successCount.get(),
                "Exactly 1 request should succeed"
        );

        assertEquals(
                2,
                conflictCount.get(),
                "Exactly 2 duplicate requests should fail"
        );

        Wallet wallet =
                walletRepository.findByUserId(userId)
                        .orElseThrow();

        assertEquals(
                new BigDecimal("900.00"),
                wallet.getBalance(),
                "Balance should be deducted only once"
        );

        assertEquals(
                1,
                walletEventRepository.count(),
                "Only one wallet event should be created"
        );
    }

    @Test
    @DisplayName("Should process 10 concurrent debits without causing negative balance")
    void testConcurrency10ConcurrentDebits()
            throws InterruptedException {

        // Reset wallet to ₹500
        walletEventRepository.deleteAll();
        walletRepository.deleteAll();

        Wallet wallet = Wallet.builder()
                .userId(userId)
                .balance(new BigDecimal("500.00"))
                .build();

        walletRepository.save(wallet);

        int numberOfRequests = 10;

        BigDecimal debitAmount =
                new BigDecimal("100.00");

        CountDownLatch startLatch =
                new CountDownLatch(1);

        CountDownLatch endLatch =
                new CountDownLatch(numberOfRequests);

        ExecutorService executor =
                Executors.newFixedThreadPool(numberOfRequests);

        AtomicInteger successCount =
                new AtomicInteger(0);

        AtomicInteger insufficientFundsCount =
                new AtomicInteger(0);

        AtomicReference<Throwable> unexpectedException =
                new AtomicReference<>();

        for (int i = 0; i < numberOfRequests; i++) {

            executor.submit(() -> {

                try {

                    startLatch.await();

                    PaymentRequest request =
                            new PaymentRequest(
                                    UUID.randomUUID(),
                                    userId,
                                    debitAmount,
                                    EventType.DEBIT
                            );

                    PaymentResponse response =
                            paymentService.processPayment(request);

                    if ("SUCCESS".equals(response.status())) {

                        successCount.incrementAndGet();

                    } else if ("FAILED".equals(response.status())) {

                        insufficientFundsCount.incrementAndGet();

                    } else {

                        unexpectedException.compareAndSet(
                                null,
                                new AssertionError(
                                        "Unexpected transaction status: "
                                                + response.status()
                                )
                        );
                    }

                } catch (Throwable e) {

                    unexpectedException.compareAndSet(
                            null,
                            e
                    );

                } finally {

                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();

        endLatch.await();

        executor.shutdown();

        assertNull(
                unexpectedException.get(),
                "No unexpected exception should occur"
        );

        assertEquals(
                5,
                successCount.get(),
                "Exactly 5 debits should succeed"
        );

        assertEquals(
                5,
                insufficientFundsCount.get(),
                "Exactly 5 debits should fail due to insufficient funds"
        );

        Wallet finalWallet =
                walletRepository.findByUserId(userId)
                        .orElseThrow();

        assertEquals(
                new BigDecimal("0.00"),
                finalWallet.getBalance(),
                "Final balance should be ₹0.00"
        );

        long successfulTransactions =
                walletEventRepository.findAll()
                        .stream()
                        .filter(event ->
                                "SUCCESS".equals(
                                        event.getStatus().name()
                                )
                        )
                        .count();

        assertEquals(
                5,
                successfulTransactions,
                "Exactly 5 successful transactions should be recorded"
        );
    }
}