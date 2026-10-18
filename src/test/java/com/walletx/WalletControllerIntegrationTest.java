package com.walletx;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WalletControllerIntegrationTest {

    @Autowired
    WebApplicationContext context;

    @Autowired
    WalletRepository walletRepository;

    MockMvc mockMvc;

    @org.junit.jupiter.api.BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void createWallet_returnsCreatedWithWallet() throws Exception {
        String requestBody = """
                {
                  "ownerName": "Integration Test User"
                }
                """;

        mockMvc.perform(post("/api/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.ownerName", is("Integration Test User")))
                .andExpect(jsonPath("$.balance", is(0)));
    }

    @Test
    void createWallet_invalidOwnerName_returnsBadRequest() throws Exception {
        String requestBody = """
                {
                  "ownerName": ""
                }
                """;

        mockMvc.perform(post("/api/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Validation Failed")));
    }

    @Test
    void getWallet_existingId_returnsWallet() throws Exception {
        Wallet wallet = new Wallet();
        wallet.setOwnerName("Test User");
        wallet.setBalance(new BigDecimal("100.00"));
        Wallet saved = walletRepository.save(wallet);

        mockMvc.perform(get("/api/wallets/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(saved.getId().intValue())))
                .andExpect(jsonPath("$.ownerName", is("Test User")))
                .andExpect(jsonPath("$.balance", is(100.00)));
    }

    @Test
    void getWallet_nonexistentId_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/wallets/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", is("Wallet not found: 999999")));
    }

    @Test
    void getAllWallets_returnsList() throws Exception {
        walletRepository.deleteAll();

        Wallet wallet1 = new Wallet();
        wallet1.setOwnerName("User 1");
        wallet1.setBalance(new BigDecimal("50.00"));
        walletRepository.save(wallet1);

        Wallet wallet2 = new Wallet();
        wallet2.setOwnerName("User 2");
        wallet2.setBalance(new BigDecimal("75.00"));
        walletRepository.save(wallet2);

        mockMvc.perform(get("/api/wallets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].ownerName", is("User 1")))
                .andExpect(jsonPath("$[1].ownerName", is("User 2")));
    }

    @Test
    void deposit_existingWallet_increasesBalance() throws Exception {
        Wallet wallet = new Wallet();
        wallet.setOwnerName("Deposit User");
        wallet.setBalance(new BigDecimal("100.00"));
        Wallet saved = walletRepository.save(wallet);

        String requestBody = """
                {
                  "amount": "50.00"
                }
                """;

        mockMvc.perform(post("/api/wallets/{id}/deposit", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(saved.getId().intValue())))
                .andExpect(jsonPath("$.balance", is(150.00)));
    }

    @Test
    void deposit_nonexistentWallet_returnsNotFound() throws Exception {
        String requestBody = """
                {
                  "amount": "50.00"
                }
                """;

        mockMvc.perform(post("/api/wallets/999999/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Not Found")));
    }

    @Test
    void deposit_invalidAmount_returnsBadRequest() throws Exception {
        Wallet wallet = new Wallet();
        wallet.setOwnerName("Test User");
        wallet.setBalance(new BigDecimal("100.00"));
        Wallet saved = walletRepository.save(wallet);

        String requestBody = """
                {
                  "amount": "-10.00"
                }
                """;

        mockMvc.perform(post("/api/wallets/{id}/deposit", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Validation Failed")));
    }

    @Test
    void transfer_successfulTransfer_updatesBothBalances() throws Exception {
        Wallet sender = new Wallet();
        sender.setOwnerName("Sender");
        sender.setBalance(new BigDecimal("200.00"));
        Wallet savedSender = walletRepository.save(sender);

        Wallet receiver = new Wallet();
        receiver.setOwnerName("Receiver");
        receiver.setBalance(new BigDecimal("50.00"));
        Wallet savedReceiver = walletRepository.save(receiver);

        String requestBody = """
                {
                  "toWalletId": %d,
                  "amount": "75.00"
                }
                """.formatted(savedReceiver.getId());

        mockMvc.perform(post("/api/wallets/{id}/transfer", savedSender.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(savedSender.getId().intValue())))
                .andExpect(jsonPath("$.balance", is(125.00)));

        Wallet updatedReceiver = walletRepository.findById(savedReceiver.getId()).orElseThrow();
        assert updatedReceiver.getBalance().compareTo(new BigDecimal("125.00")) == 0;
    }

    @Test
    void transfer_insufficientBalance_returnsBadRequest() throws Exception {
        Wallet sender = new Wallet();
        sender.setOwnerName("Poor Sender");
        sender.setBalance(new BigDecimal("10.00"));
        Wallet savedSender = walletRepository.save(sender);

        Wallet receiver = new Wallet();
        receiver.setOwnerName("Rich Receiver");
        receiver.setBalance(new BigDecimal("100.00"));
        Wallet savedReceiver = walletRepository.save(receiver);

        String requestBody = """
                {
                  "toWalletId": %d,
                  "amount": "50.00"
                }
                """.formatted(savedReceiver.getId());

        mockMvc.perform(post("/api/wallets/{id}/transfer", savedSender.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("Insufficient balance")));

        Wallet unchangedSender = walletRepository.findById(savedSender.getId()).orElseThrow();
        assert unchangedSender.getBalance().compareTo(new BigDecimal("10.00")) == 0;
    }

    @Test
    void transfer_senderNotFound_returnsNotFound() throws Exception {
        Wallet receiver = new Wallet();
        receiver.setOwnerName("Receiver");
        receiver.setBalance(new BigDecimal("100.00"));
        Wallet savedReceiver = walletRepository.save(receiver);

        String requestBody = """
                {
                  "toWalletId": %d,
                  "amount": "50.00"
                }
                """.formatted(savedReceiver.getId());

        mockMvc.perform(post("/api/wallets/999999/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", is("Wallet not found: 999999")));
    }

    @Test
    void transfer_receiverNotFound_returnsNotFound() throws Exception {
        Wallet sender = new Wallet();
        sender.setOwnerName("Sender");
        sender.setBalance(new BigDecimal("100.00"));
        Wallet savedSender = walletRepository.save(sender);

        String requestBody = """
                {
                  "toWalletId": 999999,
                  "amount": "50.00"
                }
                """;

        mockMvc.perform(post("/api/wallets/{id}/transfer", savedSender.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", is("Wallet not found: 999999")));
    }

    @Test
    void transfer_sameWallet_returnsBadRequest() throws Exception {
        Wallet wallet = new Wallet();
        wallet.setOwnerName("Same User");
        wallet.setBalance(new BigDecimal("100.00"));
        Wallet saved = walletRepository.save(wallet);

        String requestBody = """
                {
                  "toWalletId": %d,
                  "amount": "50.00"
                }
                """.formatted(saved.getId());

        mockMvc.perform(post("/api/wallets/{id}/transfer", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("Cannot transfer to the same wallet")));

        Wallet unchanged = walletRepository.findById(saved.getId()).orElseThrow();
        assert unchanged.getBalance().compareTo(new BigDecimal("100.00")) == 0;
    }

    @Test
    void transfer_invalidAmount_returnsBadRequest() throws Exception {
        Wallet sender = new Wallet();
        sender.setOwnerName("Sender");
        sender.setBalance(new BigDecimal("100.00"));
        Wallet savedSender = walletRepository.save(sender);

        Wallet receiver = new Wallet();
        receiver.setOwnerName("Receiver");
        receiver.setBalance(new BigDecimal("50.00"));
        Wallet savedReceiver = walletRepository.save(receiver);

        String requestBody = """
                {
                  "toWalletId": %d,
                  "amount": 0
                }
                """.formatted(savedReceiver.getId());

        mockMvc.perform(post("/api/wallets/{id}/transfer", savedSender.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Validation Failed")));
    }
}