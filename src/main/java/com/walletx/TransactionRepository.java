package com.walletx;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("SELECT t FROM Transaction t WHERE t.sender.id = :walletId OR t.receiver.id = :walletId")
    Page<Transaction> findByWalletId(@Param("walletId") Long walletId, Pageable pageable);
}