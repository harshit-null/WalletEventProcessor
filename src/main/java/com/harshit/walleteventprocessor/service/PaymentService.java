package com.harshit.walleteventprocessor.service;

import com.harshit.walleteventprocessor.dto.PaymentRequest;
import com.harshit.walleteventprocessor.dto.PaymentResponse;

public interface PaymentService {
    PaymentResponse processPayment(PaymentRequest request);
}
