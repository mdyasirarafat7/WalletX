package com.walletx;

import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    public WalletService(WalletRepository walletRepository, TransactionRepository transactionRepository) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public Wallet transfer(Long fromWalletId, TransferRequest request) {
        Long firstId, secondId;

        // Lock both wallets in ascending ID order to prevent deadlocks
        if (fromWalletId < request.toWalletId()) {
            firstId = fromWalletId;
            secondId = request.toWalletId();
        } else {
            firstId = request.toWalletId();
            secondId = fromWalletId;
        }

        Wallet firstWallet = walletRepository.findWithLockById(firstId);
        if (firstWallet == null) {
            throw new WalletNotFoundException("Wallet not found: " + firstId);
        }

        Wallet secondWallet = walletRepository.findWithLockById(secondId);
        if (secondWallet == null) {
            throw new WalletNotFoundException("Wallet not found: " + secondId);
        }

        // Determine sender and receiver after locks are acquired
        Wallet sender = fromWalletId < request.toWalletId() ? firstWallet : secondWallet;
        Wallet receiver = fromWalletId < request.toWalletId() ? secondWallet : firstWallet;

        if (sender.getId().equals(receiver.getId())) {
            throw new IllegalArgumentException("Cannot transfer to the same wallet");
        }

        BigDecimal amount = request.amount();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be greater than zero");
        }

        if (sender.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient balance");
        }

        sender.setBalance(sender.getBalance().subtract(amount));
        receiver.setBalance(receiver.getBalance().add(amount));

        walletRepository.save(sender);
        walletRepository.save(receiver);

        // Record transaction history atomically within the same transaction.
        // If history creation fails, balance changes roll back too.
        transactionRepository.save(new Transaction(
                sender, receiver, amount,
                Transaction.TransactionType.TRANSFER_OUT,
                Transaction.TransactionStatus.COMPLETED
        ));

        return sender;
    }

    @Transactional
    public Wallet deposit(Long walletId, BigDecimal amount) {
        Wallet wallet = walletRepository.findWithLockById(walletId);
        if (wallet == null) {
            throw new WalletNotFoundException("Wallet not found: " + walletId);
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be greater than zero");
        }

        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

        transactionRepository.save(new Transaction(
                null,
                wallet,
                amount,
                Transaction.TransactionType.DEPOSIT,
                Transaction.TransactionStatus.COMPLETED
        ));

        return wallet;
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactionHistory(Long walletId, Pageable pageable) {
        if (!walletRepository.existsById(walletId)) {
            throw new WalletNotFoundException("Wallet not found: " + walletId);
        }
        return transactionRepository.findByWalletId(walletId, pageable)
                .map(t -> TransactionResponse.from(t, walletId));
    }
}