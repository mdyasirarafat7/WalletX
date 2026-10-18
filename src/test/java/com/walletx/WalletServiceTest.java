package com.walletx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    WalletRepository walletRepository;

    @Mock
    TransactionRepository transactionRepository;

    @InjectMocks
    WalletService walletService;

    private void setId(Wallet wallet, Long id) {
        try {
            Field field = Wallet.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(wallet, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void transfer_successfulTransfer_decreasesSenderIncreasesReceiver() {
        Wallet sender = new Wallet();
        setId(sender, 1L);
        sender.setOwnerName("Alice");
        sender.setBalance(new BigDecimal("100.00"));

        Wallet receiver = new Wallet();
        setId(receiver, 2L);
        receiver.setOwnerName("Bob");
        receiver.setBalance(new BigDecimal("50.00"));

        TransferRequest request = new TransferRequest(2L, new BigDecimal("30.00"));

        when(walletRepository.findWithLockById(1L)).thenReturn(sender);
        when(walletRepository.findWithLockById(2L)).thenReturn(receiver);
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Wallet result = walletService.transfer(1L, request);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getBalance()).isEqualByComparingTo("70.00");
        assertThat(receiver.getBalance()).isEqualByComparingTo("80.00");

        verify(walletRepository).save(sender);
        verify(walletRepository).save(receiver);
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void transfer_insufficientBalance_throwsExceptionAndDoesNotSave() {
        Wallet sender = new Wallet();
        setId(sender, 1L);
        sender.setOwnerName("Alice");
        sender.setBalance(new BigDecimal("10.00"));

        Wallet receiver = new Wallet();
        setId(receiver, 2L);
        receiver.setOwnerName("Bob");
        receiver.setBalance(new BigDecimal("50.00"));

        TransferRequest request = new TransferRequest(2L, new BigDecimal("30.00"));

        when(walletRepository.findWithLockById(1L)).thenReturn(sender);
        when(walletRepository.findWithLockById(2L)).thenReturn(receiver);

        assertThatThrownBy(() -> walletService.transfer(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Insufficient balance");

        assertThat(sender.getBalance()).isEqualByComparingTo("10.00");
        assertThat(receiver.getBalance()).isEqualByComparingTo("50.00");

        verify(walletRepository, never()).save(any(Wallet.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void transfer_senderNotFound_throwsWalletNotFoundException() {
        TransferRequest request = new TransferRequest(2L, new BigDecimal("30.00"));

        when(walletRepository.findWithLockById(1L)).thenReturn(null);

        assertThatThrownBy(() -> walletService.transfer(1L, request))
                .isInstanceOf(WalletNotFoundException.class)
                .hasMessage("Wallet not found: 1");

        verify(walletRepository, never()).save(any(Wallet.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void transfer_receiverNotFound_throwsWalletNotFoundException() {
        Wallet sender = new Wallet();
        setId(sender, 1L);
        sender.setOwnerName("Alice");
        sender.setBalance(new BigDecimal("100.00"));

        TransferRequest request = new TransferRequest(999L, new BigDecimal("30.00"));

        when(walletRepository.findWithLockById(1L)).thenReturn(sender);
        when(walletRepository.findWithLockById(999L)).thenReturn(null);

        assertThatThrownBy(() -> walletService.transfer(1L, request))
                .isInstanceOf(WalletNotFoundException.class)
                .hasMessage("Wallet not found: 999");

        verify(walletRepository, never()).save(any(Wallet.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void transfer_sameWallet_throwsExceptionAndDoesNotSave() {
        Wallet wallet = new Wallet();
        setId(wallet, 1L);
        wallet.setOwnerName("Alice");
        wallet.setBalance(new BigDecimal("100.00"));

        TransferRequest request = new TransferRequest(1L, new BigDecimal("30.00"));

        when(walletRepository.findWithLockById(1L)).thenReturn(wallet);

        assertThatThrownBy(() -> walletService.transfer(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot transfer to the same wallet");

        assertThat(wallet.getBalance()).isEqualByComparingTo("100.00");

        verify(walletRepository, never()).save(any(Wallet.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void transfer_zeroAmount_throwsException() {
        Wallet sender = new Wallet();
        setId(sender, 1L);
        sender.setOwnerName("Alice");
        sender.setBalance(new BigDecimal("100.00"));

        Wallet receiver = new Wallet();
        setId(receiver, 2L);
        receiver.setOwnerName("Bob");
        receiver.setBalance(new BigDecimal("50.00"));

        TransferRequest request = new TransferRequest(2L, BigDecimal.ZERO);

        when(walletRepository.findWithLockById(1L)).thenReturn(sender);
        when(walletRepository.findWithLockById(2L)).thenReturn(receiver);

        assertThatThrownBy(() -> walletService.transfer(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Transfer amount must be greater than zero");

        verify(walletRepository, never()).save(any(Wallet.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void transfer_negativeAmount_throwsException() {
        Wallet sender = new Wallet();
        setId(sender, 1L);
        sender.setOwnerName("Alice");
        sender.setBalance(new BigDecimal("100.00"));

        Wallet receiver = new Wallet();
        setId(receiver, 2L);
        receiver.setOwnerName("Bob");
        receiver.setBalance(new BigDecimal("50.00"));

        TransferRequest request = new TransferRequest(2L, new BigDecimal("-10.00"));

        when(walletRepository.findWithLockById(1L)).thenReturn(sender);
        when(walletRepository.findWithLockById(2L)).thenReturn(receiver);

        assertThatThrownBy(() -> walletService.transfer(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Transfer amount must be greater than zero");

        verify(walletRepository, never()).save(any(Wallet.class));
    }
}