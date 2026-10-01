package com.example.payout_batch_optimizer.persistence;

import com.example.payout_batch_optimizer.TestcontainersConfiguration;
import com.example.payout_batch_optimizer.entity.PayoutBatch;
import com.example.payout_batch_optimizer.entity.PayoutBatchItem;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class PayoutBatchRepositoryTest {

    @Autowired
    private PayoutBatchRepository payoutBatchRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void saveGeneratesId() {
        PayoutBatch saved = payoutBatchRepository.save(PayoutBatch.create(new BigDecimal("100"), null));

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    void saveStoresBatchAndItems() {
        PayoutBatch batch = PayoutBatch.create(new BigDecimal("12000"), null);
        batch.addPayoutBatchItem("PO-3001", new BigDecimal("4000"), new BigDecimal("90"), false);
        batch.addPayoutBatchItem("PO-3002", new BigDecimal("6000"), new BigDecimal("150"), true);

        UUID id = saveAndClear(batch);
        PayoutBatch found = payoutBatchRepository.findById(id).orElseThrow();

        assertThat(found.getAvailablePayoutFloat()).isEqualByComparingTo("12000");
        assertThat(found.getTotalFloatConsumed()).isEqualByComparingTo("6000");
        assertThat(found.getTotalAgentCommission()).isEqualByComparingTo("150");
        assertThat(found.getCreatedAt()).isEqualTo(batch.getCreatedAt());

        List<PayoutBatchItem> items = found.getPayoutBatchItems();
        assertThat(items).hasSize(2);

        assertThat(items.get(0).getRequestReference()).isEqualTo("PO-3001");
        assertThat(items.get(0).getPayoutAmount()).isEqualByComparingTo("4000");
        assertThat(items.get(0).getAgentCommission()).isEqualByComparingTo("90");
        assertThat(items.get(0).isSelected()).isFalse();

        assertThat(items.get(1).getRequestReference()).isEqualTo("PO-3002");
        assertThat(items.get(1).getPayoutAmount()).isEqualByComparingTo("6000");
        assertThat(items.get(1).getAgentCommission()).isEqualByComparingTo("150");
        assertThat(items.get(1).isSelected()).isTrue();
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        Optional<PayoutBatch> found = payoutBatchRepository.findById(UUID.randomUUID());

        assertThat(found).isEmpty();
    }

    @Test
    void findAllReturnsNewestFirst() throws InterruptedException {
        UUID first = saveBatch("100");
        Thread.sleep(5);
        UUID second = saveBatch("200");
        Thread.sleep(5);
        UUID third = saveBatch("300");

        Page<PayoutBatch> page = payoutBatchRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 10));

        assertThat(page.getContent())
                .extracting(PayoutBatch::getId)
                .containsExactly(third, second, first);
    }

    @Test
    void findAllSplitsResultsIntoPages() {
        saveBatch("100");
        saveBatch("200");
        saveBatch("300");

        Page<PayoutBatch> firstPage = payoutBatchRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 2));
        Page<PayoutBatch> secondPage = payoutBatchRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(1, 2));

        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(secondPage.getContent()).hasSize(1);
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
    }

    @Test
    void findByIdempotencyKeyReturnsTheBatchWithThatKey() {
        PayoutBatch batch = PayoutBatch.create(new BigDecimal("100"), "key-1");
        batch.addPayoutBatchItem("PO-1", new BigDecimal("50"), new BigDecimal("5"), true);
        UUID id = saveAndClear(batch);

        Optional<PayoutBatch> found = payoutBatchRepository.findByIdempotencyKey("key-1");

        assertThat(found).map(PayoutBatch::getId).contains(id);
        assertThat(payoutBatchRepository.findByIdempotencyKey("other-key")).isEmpty();
    }

    @Test
    void saveRejectsDuplicateIdempotencyKey() {
        PayoutBatch first = PayoutBatch.create(new BigDecimal("100"), "key-1");
        first.addPayoutBatchItem("PO-1", new BigDecimal("50"), new BigDecimal("5"), true);
        saveAndClear(first);

        PayoutBatch duplicate = PayoutBatch.create(new BigDecimal("200"), "key-1");
        duplicate.addPayoutBatchItem("PO-2", new BigDecimal("50"), new BigDecimal("5"), true);

        assertThatThrownBy(() -> payoutBatchRepository.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void saveAllowsManyBatchesWithoutIdempotencyKey() {
        saveAndClear(PayoutBatch.create(new BigDecimal("100"), null));
        saveAndClear(PayoutBatch.create(new BigDecimal("200"), null));

        assertThat(payoutBatchRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 10)).getTotalElements())
                .isEqualTo(2);
    }

    private UUID saveBatch(String availablePayoutFloat) {
        return payoutBatchRepository.save(PayoutBatch.create(new BigDecimal(availablePayoutFloat), null)).getId();
    }

    private UUID saveAndClear(PayoutBatch batch) {
        UUID id = payoutBatchRepository.save(batch).getId();
        entityManager.flush();
        entityManager.clear();
        return id;
    }
}