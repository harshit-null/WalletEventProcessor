package com.harshit.walleteventprocessor.repository;

import com.harshit.walleteventprocessor.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    /**
         * find wallet by userId (without locking)
         * used when you only need to read wallet data
     */
    Optional<Wallet> findByUserId(UUID userId);

    /**
     * find wallet by userId with pessimistic write lock
     * this is critical for concurrent updates and prevents race conditions
     * usage: lock the wallet before updating balance
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.userId = :userId")
    Optional<Wallet> findByUserIdWithLock(@Param("userId") UUID userId);

    /**
     * find wallet by id with pessimistic write lock
     * used when you have the uuid and need to lock for updates
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Optional<Wallet> findByIdWithLock(@Param("id") UUID id);

    /**
     * Check if wallet exists for a user
     * Useful for validation before operations
     */
    boolean existsByUserId(UUID userId);

}
