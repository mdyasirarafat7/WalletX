package com.walletx;

import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/wallets")
public class WalletController {

    private final WalletRepository walletRepository;
    private final WalletService walletService;

    public WalletController(WalletRepository walletRepository, WalletService walletService) {
        this.walletRepository = walletRepository;
        this.walletService = walletService;
    }

    @PostMapping
    public ResponseEntity<Wallet> createWallet(@Valid @RequestBody CreateWalletRequest request) {
        Wallet wallet = new Wallet();
        wallet.setOwnerName(request.ownerName());
        Wallet saved = walletRepository.save(wallet);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Wallet> getWallet(@PathVariable Long id) {
        return walletRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + id));
    }

    @GetMapping
    public ResponseEntity<java.util.List<Wallet>> getAllWallets() {
        return ResponseEntity.ok(walletRepository.findAll());
    }

    @PostMapping("/{id}/deposit")
    public ResponseEntity<Wallet> deposit(@PathVariable Long id, @Valid @RequestBody DepositRequest request) {
        return ResponseEntity.ok(walletService.deposit(id, request.amount()));
    }

    @PostMapping("/{id}/transfer")
    public ResponseEntity<Wallet> transfer(@PathVariable Long id, @Valid @RequestBody TransferRequest request) {
        return ResponseEntity.ok(walletService.transfer(id, request));
    }

    @GetMapping("/{id}/transactions")
    public ResponseEntity<Page<TransactionResponse>> getTransactionHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        // Parse sort parameter (format: "property,direction" or just "property")
        String[] sortParts = sort.split(",");
        String sortBy = sortParts[0];
        String direction = sortParts.length > 1 ? sortParts[1] : "desc";

        org.springframework.data.domain.Sort.Direction sortDirection =
                direction.equalsIgnoreCase("asc") ? org.springframework.data.domain.Sort.Direction.ASC
                        : org.springframework.data.domain.Sort.Direction.DESC;

        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by(sortDirection, sortBy));

        return ResponseEntity.ok(walletService.getTransactionHistory(id, pageable));
    }
}