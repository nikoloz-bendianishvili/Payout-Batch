package com.example.payout_batch_optimizer.persistence;

import com.example.payout_batch_optimizer.entity.PayoutBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.repository.Repository;

import org.springframework.data.domain.Pageable;
import java.util.Optional;
import java.util.UUID;

public interface PayoutBatchRepository extends Repository<PayoutBatch, UUID> {

    PayoutBatch save(PayoutBatch batch);

    Optional<PayoutBatch> findById(UUID id);

    Optional<PayoutBatch> findByIdempotencyKey(String idempotencyKey);

    Page<PayoutBatch> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
