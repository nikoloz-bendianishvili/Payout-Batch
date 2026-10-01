package com.example.payout_batch_optimizer.api;

import com.example.payout_batch_optimizer.dto.PayoutBatchRequestDTO;
import com.example.payout_batch_optimizer.dto.PayoutBatchResponseDTO;
import com.example.payout_batch_optimizer.dto.SelectedPayoutDTO;
import com.example.payout_batch_optimizer.exception.PayoutBatchNotFoundException;
import com.example.payout_batch_optimizer.service.PayoutBatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PayoutBatchController.class)
class PayoutBatchControllerTest {

    private static final String BASE_URL = "/api/v1/payout-batches";
    private static final UUID BATCH_ID = UUID.fromString("9c8b7a6d-1e2f-4a3b-8c9d-0e1f2a3b4c5d");

    private static final String VALID_ITEM =
            "{'requestReference': 'PO-1', 'payoutAmount': 100, 'agentCommission': 5}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PayoutBatchService payoutBatchService;

    @Test
    void optimize_validRequest_returns201WithResult() throws Exception {
        when(payoutBatchService.createPayoutBatch(any(), any())).thenReturn(sampleResponse());

        mockMvc.perform(post(BASE_URL + "/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {
                                  'availablePayoutFloat': 12000,
                                  'payoutRequests': [
                                    { 'requestReference': 'PO-3001', 'payoutAmount': 4000, 'agentCommission': 90 },
                                    { 'requestReference': 'PO-3002', 'payoutAmount': 6000, 'agentCommission': 150 }
                                  ]
                                }
                                """)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.batchId").value(BATCH_ID.toString()))
                .andExpect(jsonPath("$.selectedPayouts", hasSize(2)))
                .andExpect(jsonPath("$.selectedPayouts[0].requestReference").value("PO-3002"))
                .andExpect(jsonPath("$.selectedPayouts[0].payoutAmount").value(6000))
                .andExpect(jsonPath("$.selectedPayouts[0].agentCommission").value(150))
                .andExpect(jsonPath("$.totalFloatConsumed").value(11000))
                .andExpect(jsonPath("$.totalAgentCommission").value(265))
                .andExpect(jsonPath("$.createdAt").value("2026-09-07T09:00:00Z"));
    }

    @Test
    void optimize_nothingSelected_returns200WithEmptyList() throws Exception {
        when(payoutBatchService.createPayoutBatch(any(), any())).thenReturn(new PayoutBatchResponseDTO(
                BATCH_ID, List.of(), new BigDecimal("0.00"), new BigDecimal("0.00"),
                Instant.parse("2026-09-07T09:00:00Z")));

        mockMvc.perform(post(BASE_URL + "/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("100", "[" + VALID_ITEM + "]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedPayouts", hasSize(0)))
                .andExpect(jsonPath("$.totalAgentCommission").value(0));
    }

    @Test
    void optimize_passesParsedRequestToService() throws Exception {
        when(payoutBatchService.createPayoutBatch(any(), any())).thenReturn(sampleResponse());

        mockMvc.perform(post(BASE_URL + "/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("12000.50", "[" + VALID_ITEM + "]")))
                .andExpect(status().isCreated());

        ArgumentCaptor<PayoutBatchRequestDTO> captor = ArgumentCaptor.forClass(PayoutBatchRequestDTO.class);
        verify(payoutBatchService).createPayoutBatch(captor.capture(), isNull());

        PayoutBatchRequestDTO received = captor.getValue();
        assertThat(received.availablePayoutFloat()).isEqualByComparingTo("12000.50");
        assertThat(received.payoutRequests()).hasSize(1);
        assertThat(received.payoutRequests().get(0).requestReference()).isEqualTo("PO-1");
        assertThat(received.payoutRequests().get(0).payoutAmount()).isEqualByComparingTo("100");
        assertThat(received.payoutRequests().get(0).agentCommission()).isEqualByComparingTo("5");
    }

    @Test
    void optimize_withIdempotencyKey_passesKeyToService() throws Exception {
        when(payoutBatchService.createPayoutBatch(any(), any())).thenReturn(sampleResponse());

        mockMvc.perform(post(BASE_URL + "/optimize")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("100", "[" + VALID_ITEM + "]")))
                .andExpect(status().isCreated());

        verify(payoutBatchService).createPayoutBatch(any(), eq("key-1"));
    }

    @Test
    void optimize_idempotencyKeyLongerThan255Chars_returns400() throws Exception {
        mockMvc.perform(post(BASE_URL + "/optimize")
                        .header("Idempotency-Key", "A".repeat(256))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("100", "[" + VALID_ITEM + "]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Idempotency-Key must be at most 255 characters"));

        verifyNoInteractions(payoutBatchService);
    }

    @Test
    void optimize_sameKeyInsertedConcurrently_returns409() throws Exception {
        when(payoutBatchService.createPayoutBatch(any(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        mockMvc.perform(post(BASE_URL + "/optimize")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("100", "[" + VALID_ITEM + "]")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A request with this Idempotency-Key is already being processed"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidOptimizeRequests")
    void optimize_invalidRequest_returns400(String description, String body) throws Exception {
        mockMvc.perform(post(BASE_URL + "/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(payoutBatchService);
    }

    @Test
    void optimize_invalidRequest_returnsDescriptiveMessage() throws Exception {
        mockMvc.perform(post(BASE_URL + "/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("-1", "[" + VALID_ITEM + "]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value("availablePayoutFloat: must be greater than or equal to 0"));
    }

    static Stream<Arguments> invalidOptimizeRequests() {
        return Stream.of(
                Arguments.of("float missing",
                        json("{'payoutRequests': [" + VALID_ITEM + "]}")),
                Arguments.of("float negative",
                        request("-1", "[" + VALID_ITEM + "]")),
                Arguments.of("float with 3 decimals",
                        request("100.123", "[" + VALID_ITEM + "]")),

                Arguments.of("payoutRequests missing",
                        json("{'availablePayoutFloat': 100}")),
                Arguments.of("payoutRequests empty",
                        request("100", "[]")),
                Arguments.of("payoutRequests contains null",
                        request("100", "[null]")),

                Arguments.of("requestReference missing",
                        request("100", "[{'payoutAmount': 100, 'agentCommission': 5}]")),
                Arguments.of("requestReference blank",
                        request("100", "[{'requestReference': '   ', 'payoutAmount': 100, 'agentCommission': 5}]")),
                Arguments.of("requestReference longer than 255 chars",
                        request("100", "[{'requestReference': '" + "A".repeat(256)
                                + "', 'payoutAmount': 100, 'agentCommission': 5}]")),

                Arguments.of("payoutAmount zero",
                        request("100", "[{'requestReference': 'PO-1', 'payoutAmount': 0, 'agentCommission': 5}]")),
                Arguments.of("payoutAmount negative",
                        request("100", "[{'requestReference': 'PO-1', 'payoutAmount': -10, 'agentCommission': 5}]")),
                Arguments.of("payoutAmount with 3 decimals",
                        request("100", "[{'requestReference': 'PO-1', 'payoutAmount': 10.005, 'agentCommission': 5}]")),
                Arguments.of("payoutAmount too many digits",
                        request("100", "[{'requestReference': 'PO-1', 'payoutAmount': 12345678901234567890, 'agentCommission': 5}]")),

                Arguments.of("agentCommission missing",
                        request("100", "[{'requestReference': 'PO-1', 'payoutAmount': 100}]")),
                Arguments.of("agentCommission negative",
                        request("100", "[{'requestReference': 'PO-1', 'payoutAmount': 100, 'agentCommission': -5}]")),

                Arguments.of("malformed JSON",
                        json("{'availablePayoutFloat': 100,")),
                Arguments.of("amount is not a number",
                        request("100", "[{'requestReference': 'PO-1', 'payoutAmount': 'abc', 'agentCommission': 5}]"))
        );
    }

    @Test
    void getById_existingBatch_returns200() throws Exception {
        when(payoutBatchService.getPayoutBatchById(BATCH_ID)).thenReturn(sampleResponse());

        mockMvc.perform(get(BASE_URL + "/{batchId}", BATCH_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value(BATCH_ID.toString()))
                .andExpect(jsonPath("$.selectedPayouts", hasSize(2)))
                .andExpect(jsonPath("$.totalAgentCommission").value(265));
    }

    @Test
    void getById_unknownBatch_returns404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(payoutBatchService.getPayoutBatchById(unknownId))
                .thenThrow(new PayoutBatchNotFoundException("Payout batch not found: " + unknownId));

        mockMvc.perform(get(BASE_URL + "/{batchId}", unknownId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Payout batch not found: " + unknownId));
    }

    @Test
    void getById_invalidUuid_returns400() throws Exception {
        mockMvc.perform(get(BASE_URL + "/not-a-uuid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(payoutBatchService);
    }

    @Test
    void getAll_defaultParams_usesFirstPageOfTen() throws Exception {
        when(payoutBatchService.getAllPayoutBatches(0, 10))
                .thenReturn(new PageImpl<>(List.of(sampleResponse()), PageRequest.of(0, 10), 1));

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].batchId").value(BATCH_ID.toString()));

        verify(payoutBatchService).getAllPayoutBatches(0, 10);
    }

    @Test
    void getAll_customParams_arePassedToService() throws Exception {
        when(payoutBatchService.getAllPayoutBatches(2, 5))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 5), 0));

        mockMvc.perform(get(BASE_URL).param("page", "2").param("size", "5"))
                .andExpect(status().isOk());

        verify(payoutBatchService).getAllPayoutBatches(2, 5);
    }

    @ParameterizedTest(name = "{0}={1}")
    @CsvSource({
            "page, -1",
            "size, 0",
            "page, abc"
    })
    void getAll_invalidParams_returns400(String param, String value) throws Exception {
        mockMvc.perform(get(BASE_URL).param(param, value))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(payoutBatchService);
    }

    private static PayoutBatchResponseDTO sampleResponse() {
        return new PayoutBatchResponseDTO(
                BATCH_ID,
                List.of(
                        new SelectedPayoutDTO("PO-3002", new BigDecimal("6000"), new BigDecimal("150")),
                        new SelectedPayoutDTO("PO-3004", new BigDecimal("5000"), new BigDecimal("115"))),
                new BigDecimal("11000"),
                new BigDecimal("265"),
                Instant.parse("2026-09-07T09:00:00Z"));
    }

    private static String request(String availablePayoutFloat, String payoutRequests) {
        return json("{'availablePayoutFloat': " + availablePayoutFloat
                + ", 'payoutRequests': " + payoutRequests + "}");
    }

    private static String json(String singleQuoted) {
        return singleQuoted.replace('\'', '"');
    }
}