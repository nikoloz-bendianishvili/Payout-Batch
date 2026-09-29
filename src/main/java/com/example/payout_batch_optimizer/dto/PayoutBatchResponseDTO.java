package com.example.payout_batch_optimizer.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PayoutBatchResponseDTO(
        UUID batchId,
        List<SelectedPayoutDTO> selectedPayouts,
        BigDecimal totalFloatConsumed,
        BigDecimal totalAgentCommission,
        Instant createdAt
) {}
