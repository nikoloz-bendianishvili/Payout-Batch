package com.example.payout_batch_optimizer.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayoutBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "available_payout_float", nullable = false, precision = 19, scale = 2)
    private BigDecimal availablePayoutFloat;

    @Column(name = "total_float_consumed", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalFloatConsumed;

    @Column(name = "total_agent_commission", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAgentCommission;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @OneToMany(mappedBy = "payoutBatch", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<PayoutBatchItem> payoutBatchItems = new ArrayList<>();

    public static PayoutBatch create(BigDecimal availablePayoutFloat, String idempotencyKey) {
        PayoutBatch payoutBatch = new PayoutBatch();
        payoutBatch.totalFloatConsumed = BigDecimal.ZERO.setScale(2);
        payoutBatch.totalAgentCommission = BigDecimal.ZERO.setScale(2);
        payoutBatch.availablePayoutFloat = availablePayoutFloat.setScale(2);
        payoutBatch.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        payoutBatch.idempotencyKey = idempotencyKey;
        return payoutBatch;
    }

    public void addPayoutBatchItem(String requestReference, BigDecimal payoutAmount,
                                   BigDecimal agentCommission, boolean selected) {
        payoutAmount = payoutAmount.setScale(2);
        agentCommission = agentCommission.setScale(2);
        if(selected) {
            totalFloatConsumed = totalFloatConsumed.add(payoutAmount);
            totalAgentCommission = totalAgentCommission.add(agentCommission);
        }

        payoutBatchItems.add(new PayoutBatchItem(this, requestReference, payoutAmount, agentCommission, selected));
    }
}
