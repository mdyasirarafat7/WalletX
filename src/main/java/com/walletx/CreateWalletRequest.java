package com.walletx;

import jakarta.validation.constraints.NotBlank;

public record CreateWalletRequest(@NotBlank String ownerName) {
}