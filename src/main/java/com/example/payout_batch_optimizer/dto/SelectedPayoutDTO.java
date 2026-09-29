package com.example.payout_batch_optimizer.dto;

import java.math.BigDecimal;

public record SelectedPayoutDTO(
    String requestReference,
    BigDecimal payoutAmount,
    BigDecimal agentCommission
) {}
