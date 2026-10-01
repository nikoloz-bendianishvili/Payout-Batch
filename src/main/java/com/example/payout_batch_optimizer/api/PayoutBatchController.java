package com.example.payout_batch_optimizer.api;

import com.example.payout_batch_optimizer.dto.PayoutBatchRequestDTO;
import com.example.payout_batch_optimizer.dto.PayoutBatchResponseDTO;
import com.example.payout_batch_optimizer.exception.InvalidIdempotencyKeyException;
import com.example.payout_batch_optimizer.service.PayoutBatchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payout-batches")
@RequiredArgsConstructor
public class PayoutBatchController {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 255;

    private final PayoutBatchService payoutBatchService;

    @PostMapping("/optimize")
    public ResponseEntity<PayoutBatchResponseDTO> createPayoutBatch(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PayoutBatchRequestDTO request
    ) {
        if (idempotencyKey != null && idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new InvalidIdempotencyKeyException(
                    "Idempotency-Key must be at most " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
        PayoutBatchResponseDTO response = payoutBatchService.createPayoutBatch(request, idempotencyKey);
        HttpStatus status = response.selectedPayouts().isEmpty() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/{batchId}")
    public ResponseEntity<PayoutBatchResponseDTO> getPayoutBatchById(@PathVariable UUID batchId) {
        PayoutBatchResponseDTO response = payoutBatchService.getPayoutBatchById(batchId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<PayoutBatchResponseDTO>> getAllPayoutBatches(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) int size
    ) {
        Page<PayoutBatchResponseDTO> responsePage = payoutBatchService.getAllPayoutBatches(page, size);
        return ResponseEntity.ok(responsePage);
    }
}
