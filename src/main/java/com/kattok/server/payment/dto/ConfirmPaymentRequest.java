package com.kattok.server.payment.dto;

import com.kattok.server.payment.entity.PaymentRecordStatus;
import jakarta.validation.constraints.NotNull;

public record ConfirmPaymentRequest(@NotNull PaymentRecordStatus result, Long userCardId) {}
