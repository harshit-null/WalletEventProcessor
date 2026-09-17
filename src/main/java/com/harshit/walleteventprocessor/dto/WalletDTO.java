package com.harshit.walleteventprocessor.dto;
 
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record WalletDTO(

    UUID walletId,

    UUID userId,

    BigDecimal balance

) {
}
 