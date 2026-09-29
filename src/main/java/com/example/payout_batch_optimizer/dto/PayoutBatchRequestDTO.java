package com.example.payout_batch_optimizer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

public record PayoutBatchRequestDTO(
    @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal availablePayoutFloat,
    @NotEmpty List<@Valid @NotNull PayoutRequestDTO> payoutRequests
) {}
