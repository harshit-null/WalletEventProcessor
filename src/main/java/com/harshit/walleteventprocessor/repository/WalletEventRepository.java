package com.harshit.walleteventprocessor.repository;

import com.harshit.walleteventprocessor.entity.EventType;
import com.harshit.walleteventprocessor.entity.TransactionStatus;
import com.harshit.walleteventprocessor.entity.Wallet;
import com.harshit.walleteventprocessor.entity.WalletEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletEventRepository extends JpaRepository<WalletEvent, UUID> {

    /**
        find event by idempotency key, uaed prevent and detect duplicate processing
        @param idempotencyKey the unique key for this request
        @return The existing event if found, empty if this is a new reques

     */
    Optional<WalletEvent> findByIdempotencyKey(String idempotencyKey);

    /**
      find all events for a specific wallet
      useful for transaction history and debugging
     */
    List<WalletEvent> findByWallet(Wallet wallet);

    /**
     * Find all successful events for a wallet
     */
    List<WalletEvent> findByWalletAndStatus(Wallet wallet, TransactionStatus status);

    /**
     * Find all events for a wallet with a specific event type
     */
    List<WalletEvent> findByWalletAndEventType(Wallet wallet, EventType eventType);

    /**
     * count successful debits for a wallet
     */
    @Query("SELECT COUNT(e) FROM WalletEvent e WHERE e.wallet = :wallet AND e.eventType = 'DEBIT' AND e.status = 'SUCCESS'")
    long countSuccessfulDebits(@Param("wallet") Wallet wallet);

    /**
     * count successful credits for a wallet
     */
    @Query("SELECT COUNT(e) FROM WalletEvent e WHERE e.wallet = :wallet AND e.eventType = 'CREDIT' AND e.status = 'SUCCESS'")
    long countSuccessfulCredits(@Param("wallet") Wallet wallet);

    /**
     * find all pending events (not yet fully processed)
     * useful for retry mechanisms
     */
    List<WalletEvent> findByStatus(TransactionStatus status);

    /**
     * Check if an idempotency key already exists
     */
    boolean existsByIdempotencyKey(String idempotencyKey);
}

