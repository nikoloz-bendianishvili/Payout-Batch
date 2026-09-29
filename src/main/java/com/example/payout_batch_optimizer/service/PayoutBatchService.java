package com.example.payout_batch_optimizer.service;

import com.example.payout_batch_optimizer.dto.PayoutBatchRequestDTO;
import com.example.payout_batch_optimizer.dto.PayoutBatchResponseDTO;
import com.example.payout_batch_optimizer.dto.PayoutRequestDTO;
import com.example.payout_batch_optimizer.dto.SelectedPayoutDTO;
import com.example.payout_batch_optimizer.entity.PayoutBatch;
import com.example.payout_batch_optimizer.entity.PayoutBatchItem;
import com.example.payout_batch_optimizer.exception.PayoutBatchNotFoundException;
import com.example.payout_batch_optimizer.optimizer.Optimizer;
import com.example.payout_batch_optimizer.optimizer.PayoutCandidate;
import com.example.payout_batch_optimizer.optimizer.PayoutDecision;
import com.example.payout_batch_optimizer.persistence.PayoutBatchRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PayoutBatchService {

    private final PayoutBatchRepository payoutBatchRepository;
    private final Optimizer optimizer;

    @Transactional
    public PayoutBatchResponseDTO createPayoutBatch(PayoutBatchRequestDTO request) {
        List<PayoutDecision> payoutDecisions = optimizer.optimizePayoutBatch(
                request.payoutRequests().stream()
                        .map(this::toPayoutCandidate)
                        .toList(),
                request.availablePayoutFloat()
        );

        PayoutBatch payoutBatch = PayoutBatch.create(request.availablePayoutFloat());
        for (PayoutDecision decision : payoutDecisions) {
            PayoutCandidate payoutCandidate = decision.payoutCandidate();
            payoutBatch.addPayoutBatchItem(
                    payoutCandidate.requestReference(),
                    payoutCandidate.payoutAmount(),
                    payoutCandidate.agentCommission(),
                    decision.selected()
            );
        }
        payoutBatchRepository.save(payoutBatch);

        return toResponse(payoutBatch);
    }

    @Transactional(readOnly = true)
    public PayoutBatchResponseDTO getPayoutBatchById(UUID batchId) {
        PayoutBatch payoutBatch = payoutBatchRepository.findById(batchId)
                .orElseThrow(() -> new PayoutBatchNotFoundException("Payout batch not found: " + batchId));
        return toResponse(payoutBatch);
    }

    @Transactional(readOnly = true)
    public Page<PayoutBatchResponseDTO> getAllPayoutBatches(int page, int size) {
        return payoutBatchRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
                .map(this::toResponse);
    }

    private PayoutCandidate toPayoutCandidate(PayoutRequestDTO request) {
        return new PayoutCandidate(
                request.requestReference(),
                request.payoutAmount(),
                request.agentCommission()
        );
    }

    private PayoutBatchResponseDTO toResponse(PayoutBatch payoutBatch) {
        return new PayoutBatchResponseDTO(
                payoutBatch.getId(),
                payoutBatch.getPayoutBatchItems().stream().filter(PayoutBatchItem::isSelected)
                        .map(this::toSelectedPayoutsDTO)
                        .toList(),
                payoutBatch.getTotalFloatConsumed(),
                payoutBatch.getTotalAgentCommission(),
                payoutBatch.getCreatedAt()
        );
    }

    private SelectedPayoutDTO toSelectedPayoutsDTO(PayoutBatchItem item) {
        return new SelectedPayoutDTO(
                item.getRequestReference(),
                item.getPayoutAmount(),
                item.getAgentCommission()
        );
    }
}
