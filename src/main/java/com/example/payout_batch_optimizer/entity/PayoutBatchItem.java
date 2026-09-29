package com.example.payout_batch_optimizer.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayoutBatchItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payout_batch_id", nullable = false)
    private PayoutBatch payoutBatch;

    @Column(name = "request_reference", nullable = false)
    private String requestReference;

    @Column(name = "payout_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal payoutAmount;

    @Column(name = "agent_commission", nullable = false, precision = 19, scale = 2)
    private BigDecimal agentCommission;

    @Column(nullable = false)
    private boolean selected;

    PayoutBatchItem(PayoutBatch payoutBatch, String requestReference,
                    BigDecimal payoutAmount, BigDecimal agentCommission, boolean selected) {
        this.payoutBatch = payoutBatch;
        this.requestReference = requestReference;
        this.payoutAmount = payoutAmount;
        this.agentCommission = agentCommission;
        this.selected = selected;
    }
}
