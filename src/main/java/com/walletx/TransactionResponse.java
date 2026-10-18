package com.walletx;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        Long id,
        Long senderId,
        Long receiverId,
        BigDecimal amount,
        String type,
        String status,
        LocalDateTime createdAt
) {
    public static TransactionResponse from(Transaction transaction, Long walletId) {
        // Determine display type based on whether wallet is sender or receiver
        String displayType;
        if (transaction.getSender() != null && transaction.getSender().getId().equals(walletId)) {
            // Wallet is sender
            if (transaction.getType() == Transaction.TransactionType.TRANSFER_OUT) {
                displayType = "TRANSFER_OUT";
            } else {
                displayType = transaction.getType().name();
            }
        } else if (transaction.getReceiver() != null && transaction.getReceiver().getId().equals(walletId)) {
            // Wallet is receiver
            if (transaction.getType() == Transaction.TransactionType.TRANSFER_OUT) {
                displayType = "TRANSFER_IN";
            } else {
                displayType = transaction.getType().name();
            }
        } else {
            // Fallback (shouldn't happen with proper query)
            displayType = transaction.getType().name();
        }
        
        return new TransactionResponse(
                transaction.getId(),
                transaction.getSender() != null ? transaction.getSender().getId() : null,
                transaction.getReceiver() != null ? transaction.getReceiver().getId() : null,
                transaction.getAmount(),
                displayType,
                transaction.getStatus().name(),
                transaction.getCreatedAt()
        );
    }
}