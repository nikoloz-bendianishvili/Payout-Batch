package com.example.payout_batch_optimizer.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PayoutRequestDTO(
    @NotBlank @Size(max = 255) String requestReference,
    @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal payoutAmount,
    @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal agentCommission
) {}
