package com.example.payout_batch_optimizer.optimizer;

public record PayoutDecision(
    PayoutCandidate payoutCandidate,
    boolean selected
) {}
