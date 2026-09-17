package com.harshit.walleteventprocessor.controller;

import com.harshit.walleteventprocessor.dto.PaymentRequest;
import com.harshit.walleteventprocessor.dto.PaymentResponse;
import com.harshit.walleteventprocessor.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final PaymentService paymentService;

    public TransactionController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/process")
    public ResponseEntity<PaymentResponse> processPayment(
            @Valid @RequestBody PaymentRequest request) {

        PaymentResponse response =
                paymentService.processPayment(request);

        return ResponseEntity.ok(response);
    }
}