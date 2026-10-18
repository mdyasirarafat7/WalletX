package com.walletx;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.concurrent.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Concurrency integration test for WalletService.transfer().
 * 
 * This test verifies that pessimistic row locking prevents race conditions
 * when two concurrent transfers attempt to overspend the same sender balance.
 * 
 * Note: H2-based concurrency tests provide evidence that the locking mechanism
 * works correctly, but they do not guarantee identical behavior in PostgreSQL.
 * Differences may arise in lock timing, deadlock detection, or isolation level
 * implementation between database vendors.
 * 
 * To verify locking behavior against PostgreSQL without risking the development
 * database:
 * 1. Use a dedicated test database instance (not the development database)
 * 2. Run the same concurrency test against PostgreSQL test database
 * 3. For production-like verification, use staging environment with realistic load
 * 4. Consider using pgbench or similar tools to simulate concurrent workloads
 * 5. Monitor PostgreSQL logs for lock wait times and deadlocks
 */
@SpringBootTest
@ActiveProfiles("test")
// NOT @Transactional at class level - we need each thread to have its own transaction
// and setup data to be committed before concurrent transfers begin
class WalletConcurrencyIntegrationTest {

    @Autowired
    WalletService walletService;

    @Autowired
    WalletRepository walletRepository;

    @Autowired
    TransactionRepository transactionRepository;

    /**
     * Tests concurrent transfers from the same sender to two different receivers.
     * With sender balance of 100.00 and two transfers of 80.00 each:
     * - Exactly one transfer should succeed
     * - The other should fail due to insufficient balance
     * - Sender's final balance should be 20.00
     * - Receiver balances should sum to 80.00
     */
    @Test
    void concurrentTransfers_oneSucceeds_secondFails_insufficientBalance() throws Exception {
        // Setup: Create sender with 100.00 and two receivers with 0.00
        Wallet sender = new Wallet();
        sender.setOwnerName("Sender");
        sender.setBalance(new BigDecimal("100.00"));
        Wallet savedSender = walletRepository.saveAndFlush(sender);

        Wallet receiver1 = new Wallet();
        receiver1.setOwnerName("Receiver 1");
        receiver1.setBalance(new BigDecimal("0.00"));
        Wallet savedReceiver1 = walletRepository.saveAndFlush(receiver1);

        Wallet receiver2 = new Wallet();
        receiver2.setOwnerName("Receiver 2");
        receiver2.setBalance(new BigDecimal("0.00"));
        Wallet savedReceiver2 = walletRepository.saveAndFlush(receiver2);

        Long senderId = savedSender.getId();
        Long receiver1Id = savedReceiver1.getId();
        Long receiver2Id = savedReceiver2.getId();
        
        BigDecimal transferAmount = new BigDecimal("80.00");

        // Synchronization: Use CountDownLatch to start both threads simultaneously
        // This avoids arbitrary sleeps while ensuring practical overlap
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        
        try {
            // Submit two concurrent transfer tasks
            CompletableFuture<TransferResult> future1 = CompletableFuture.supplyAsync(() -> {
                try {
                    startLatch.await();
                    walletService.transfer(senderId, new TransferRequest(receiver1Id, transferAmount));
                    return new TransferResult(true, null); // Success
                } catch (Exception e) {
                    return new TransferResult(false, e); // Failure with exception
                }
            }, executor);

            CompletableFuture<TransferResult> future2 = CompletableFuture.supplyAsync(() -> {
                try {
                    startLatch.await();
                    walletService.transfer(senderId, new TransferRequest(receiver2Id, transferAmount));
                    return new TransferResult(true, null); // Success
                } catch (Exception e) {
                    return new TransferResult(false, e); // Failure with exception
                }
            }, executor);

            // Release both threads to start transfers simultaneously
            startLatch.countDown();

            // Wait for both transfers to complete (with timeout to prevent hanging)
            CompletableFuture.allOf(future1, future2).get(30, TimeUnit.SECONDS);

            // Get results from both transfers
            TransferResult result1 = future1.get();
            TransferResult result2 = future2.get();

            // Verify exactly one transfer succeeded
            int successCount = (result1.success ? 1 : 0) + (result2.success ? 1 : 0);
            assertEquals(1, successCount, "Exactly one transfer should succeed");

            // Verify the failed transfer was due to insufficient balance
            TransferResult failedTransfer = result1.success ? result2 : result1;
            assertNotNull(failedTransfer.exception, "Failed transfer should have an exception");
            
            // Unwrap exception to check the root cause
            Throwable cause = failedTransfer.exception;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            assertTrue(cause.getMessage().contains("Insufficient balance"),
                    "Failed transfer should be due to insufficient balance");

            // Verify final state: sender balance = 20.00
            Wallet finalSender = walletRepository.findById(senderId)
                    .orElseThrow(() -> new AssertionError("Sender wallet not found"));
            assertEquals(new BigDecimal("20.00").setScale(2), finalSender.getBalance().setScale(2),
                    "Sender balance should be 20.00 after one successful transfer of 80.00");

            // Verify final state: receiver balances sum to 80.00
            Wallet finalReceiver1 = walletRepository.findById(receiver1Id)
                    .orElseThrow(() -> new AssertionError("Receiver 1 wallet not found"));
            Wallet finalReceiver2 = walletRepository.findById(receiver2Id)
                    .orElseThrow(() -> new AssertionError("Receiver 2 wallet not found"));
            
            BigDecimal totalReceived = finalReceiver1.getBalance()
                    .add(finalReceiver2.getBalance())
                    .setScale(2);
            assertEquals(new BigDecimal("80.00"), totalReceived,
                    "Total received by both receivers should be 80.00");

            // Additional verification: one receiver has 80.00, the other has 0.00
            boolean receiver1GotFull = finalReceiver1.getBalance().compareTo(new BigDecimal("80.00")) == 0;
            boolean receiver2GotFull = finalReceiver2.getBalance().compareTo(new BigDecimal("80.00")) == 0;
            assertTrue(receiver1GotFull || receiver2GotFull, 
                    "One receiver should have received the full 80.00");
            
            boolean receiver1GotNone = finalReceiver1.getBalance().compareTo(BigDecimal.ZERO) == 0;
            boolean receiver2GotNone = finalReceiver2.getBalance().compareTo(BigDecimal.ZERO) == 0;
            assertTrue(receiver1GotNone || receiver2GotNone,
                    "One receiver should have received 0.00 (the failed transfer)");
        } finally {
            // Ensure executor is shut down reliably
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            // Clean up test data to prevent interference with other tests
            transactionRepository.deleteAll();
            walletRepository.deleteAll();
        }
    }

    /**
     * Helper record to capture transfer outcome.
     */
    record TransferResult(boolean success, Exception exception) {}
}