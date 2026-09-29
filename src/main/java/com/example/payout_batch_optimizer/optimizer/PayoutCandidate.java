package com.example.payout_batch_optimizer.optimizer;

import java.math.BigDecimal;

public record PayoutCandidate(
    String    requestReference,
    BigDecimal payoutAmount,
    BigDecimal agentCommission
) {}
