package com.harshit.walleteventprocessor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentResponse(
        UUID transactionId,

        UUID userId,

        String eventType,

        BigDecimal amount,

        String status,

        BigDecimal newBalance,

        LocalDateTime timestamp

) {
}
