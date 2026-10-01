package com.example.payout_batch_optimizer.service;

import com.example.payout_batch_optimizer.dto.PayoutBatchRequestDTO;
import com.example.payout_batch_optimizer.dto.PayoutBatchResponseDTO;
import com.example.payout_batch_optimizer.dto.PayoutRequestDTO;
import com.example.payout_batch_optimizer.dto.SelectedPayoutDTO;
import com.example.payout_batch_optimizer.entity.PayoutBatch;
import com.example.payout_batch_optimizer.entity.PayoutBatchItem;
import com.example.payout_batch_optimizer.exception.PayoutBatchNotFoundException;
import com.example.payout_batch_optimizer.optimizer.Optimizer;
import com.example.payout_batch_optimizer.persistence.PayoutBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayoutBatchServiceTest {

    @Mock
    private PayoutBatchRepository payoutBatchRepository;

    private PayoutBatchService payoutBatchService;

    @BeforeEach
    void setUp() {
        payoutBatchService = new PayoutBatchService(payoutBatchRepository, new Optimizer());
    }

    @Test
    void createPayoutBatch_specExample_returnsSelectedPayoutsAndTotals() {
        PayoutBatchRequestDTO request = new PayoutBatchRequestDTO(new BigDecimal("12000"), List.of(
                payoutRequest("PO-3001", "4000", "90"),
                payoutRequest("PO-3002", "6000", "150"),
                payoutRequest("PO-3003", "2500", "55"),
                payoutRequest("PO-3004", "5000", "115")));

        PayoutBatchResponseDTO response = payoutBatchService.createPayoutBatch(request, null);

        assertThat(response.selectedPayouts())
                .extracting(SelectedPayoutDTO::requestReference)
                .containsExactly("PO-3002", "PO-3004");
        assertThat(response.totalFloatConsumed()).isEqualByComparingTo("11000");
        assertThat(response.totalAgentCommission()).isEqualByComparingTo("265");
        assertThat(response.createdAt()).isNotNull();
    }

    @Test
    void createPayoutBatch_savesInputAndAllCandidatesWithSelectedFlag() {
        PayoutBatchRequestDTO request = new PayoutBatchRequestDTO(new BigDecimal("12000"), List.of(
                payoutRequest("PO-3001", "4000", "90"),
                payoutRequest("PO-3002", "6000", "150"),
                payoutRequest("PO-3003", "2500", "55"),
                payoutRequest("PO-3004", "5000", "115")));

        payoutBatchService.createPayoutBatch(request, null);

        PayoutBatch saved = captureSavedBatch();
        assertThat(saved.getAvailablePayoutFloat()).isEqualByComparingTo("12000");
        assertThat(saved.getPayoutBatchItems())
                .extracting(PayoutBatchItem::getRequestReference)
                .containsExactly("PO-3001", "PO-3002", "PO-3003", "PO-3004");
        assertThat(saved.getPayoutBatchItems())
                .extracting(PayoutBatchItem::isSelected)
                .containsExactly(false, true, false, true);
    }

    @Test
    void createPayoutBatch_nothingFits_returnsEmptyListAndZeroTotalsAndStillSaves() {
        PayoutBatchRequestDTO request = new PayoutBatchRequestDTO(new BigDecimal("100"), List.of(
                payoutRequest("PO-1", "500", "50"),
                payoutRequest("PO-2", "600", "60")));

        PayoutBatchResponseDTO response = payoutBatchService.createPayoutBatch(request, null);

        assertThat(response.selectedPayouts()).isEmpty();
        assertThat(response.totalFloatConsumed()).isEqualByComparingTo("0");
        assertThat(response.totalAgentCommission()).isEqualByComparingTo("0");

        PayoutBatch saved = captureSavedBatch();
        assertThat(saved.getPayoutBatchItems()).hasSize(2);
        assertThat(saved.getPayoutBatchItems()).noneMatch(PayoutBatchItem::isSelected);
    }

    @Test
    void createPayoutBatch_newIdempotencyKey_savesBatchWithKey() {
        PayoutBatchRequestDTO request = new PayoutBatchRequestDTO(new BigDecimal("100"), List.of(
                payoutRequest("PO-1", "50", "5")));
        when(payoutBatchRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());

        payoutBatchService.createPayoutBatch(request, "key-1");

        assertThat(captureSavedBatch().getIdempotencyKey()).isEqualTo("key-1");
    }

    @Test
    void createPayoutBatch_existingIdempotencyKey_returnsStoredBatchWithoutSaving() {
        PayoutBatch stored = PayoutBatch.create(new BigDecimal("100"), "key-1");
        stored.addPayoutBatchItem("PO-1", new BigDecimal("50"), new BigDecimal("5"), true);
        when(payoutBatchRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(stored));

        PayoutBatchRequestDTO request = new PayoutBatchRequestDTO(new BigDecimal("100"), List.of(
                payoutRequest("PO-1", "50", "5")));
        PayoutBatchResponseDTO response = payoutBatchService.createPayoutBatch(request, "key-1");

        assertThat(response.createdAt()).isEqualTo(stored.getCreatedAt());
        assertThat(response.selectedPayouts())
                .extracting(SelectedPayoutDTO::requestReference)
                .containsExactly("PO-1");
        verify(payoutBatchRepository, never()).save(any());
    }

    @Test
    void getPayoutBatchById_found_returnsOnlySelectedPayouts() {
        UUID id = UUID.randomUUID();
        PayoutBatch batch = PayoutBatch.create(new BigDecimal("12000"), null);
        batch.addPayoutBatchItem("PO-3001", new BigDecimal("4000"), new BigDecimal("90"), false);
        batch.addPayoutBatchItem("PO-3002", new BigDecimal("6000"), new BigDecimal("150"), true);
        when(payoutBatchRepository.findById(id)).thenReturn(Optional.of(batch));

        PayoutBatchResponseDTO response = payoutBatchService.getPayoutBatchById(id);

        assertThat(response.selectedPayouts()).hasSize(1);
        SelectedPayoutDTO selected = response.selectedPayouts().get(0);
        assertThat(selected.requestReference()).isEqualTo("PO-3002");
        assertThat(selected.payoutAmount()).isEqualByComparingTo("6000");
        assertThat(selected.agentCommission()).isEqualByComparingTo("150");
        assertThat(response.totalFloatConsumed()).isEqualByComparingTo("6000");
        assertThat(response.totalAgentCommission()).isEqualByComparingTo("150");
        assertThat(response.createdAt()).isEqualTo(batch.getCreatedAt());
    }

    @Test
    void getPayoutBatchById_unknownId_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(payoutBatchRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> payoutBatchService.getPayoutBatchById(id))
                .isInstanceOf(PayoutBatchNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void getAllPayoutBatches_passesPageAndSizeAndMapsEachBatch() {
        PageRequest pageRequest = PageRequest.of(1, 5);
        PayoutBatch batch = PayoutBatch.create(new BigDecimal("100"), null);
        batch.addPayoutBatchItem("PO-1", new BigDecimal("50"), new BigDecimal("5"), true);
        when(payoutBatchRepository.findAllByOrderByCreatedAtDesc(pageRequest))
                .thenReturn(new PageImpl<>(List.of(batch), pageRequest, 6));

        Page<PayoutBatchResponseDTO> result = payoutBatchService.getAllPayoutBatches(1, 5);

        assertThat(result.getTotalElements()).isEqualTo(6);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).selectedPayouts())
                .extracting(SelectedPayoutDTO::requestReference)
                .containsExactly("PO-1");
    }

    private PayoutBatch captureSavedBatch() {
        ArgumentCaptor<PayoutBatch> captor = ArgumentCaptor.forClass(PayoutBatch.class);
        verify(payoutBatchRepository).save(captor.capture());
        return captor.getValue();
    }

    private static PayoutRequestDTO payoutRequest(String reference, String amount, String commission) {
        return new PayoutRequestDTO(reference, new BigDecimal(amount), new BigDecimal(commission));
    }
}
