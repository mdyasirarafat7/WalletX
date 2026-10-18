package com.walletx;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
public class Transaction {

    public enum TransactionType {
        TRANSFER_OUT,
        TRANSFER_IN,
        DEPOSIT
    }

    public enum TransactionStatus {
        COMPLETED,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Wallet sender;

    @ManyToOne(fetch = FetchType.LAZY)
    private Wallet receiver;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionType type;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected Transaction() {
    }

    public Transaction(Wallet sender, Wallet receiver, BigDecimal amount,
                       TransactionType type, TransactionStatus status) {
        this.sender = sender;
        this.receiver = receiver;
        this.amount = amount;
        this.type = type;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Wallet getSender() {
        return sender;
    }

    public Wallet getReceiver() {
        return receiver;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionType getType() {
        return type;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}