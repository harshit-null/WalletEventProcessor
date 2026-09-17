package com.harshit.walleteventprocessor.dto;

import com.harshit.walleteventprocessor.entity.EventType;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequest(

        @NotNull(message = "Transaction ID is required")
        UUID transactionId,

        @NotNull(message = "User ID is required")
        UUID userId,

        @NotNull(message = "Amount is required")
        BigDecimal amount,

        @NotNull(message = "Transaction type is required")
        EventType type

) {
}