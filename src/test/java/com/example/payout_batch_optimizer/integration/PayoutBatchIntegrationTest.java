package com.example.payout_batch_optimizer.integration;

import com.example.payout_batch_optimizer.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PayoutBatchIntegrationTest {

    private static final String BASE_URL = "/api/v1/payout-batches";

    private static final String SPEC_EXAMPLE = json("""
            {
              'availablePayoutFloat': 12000,
              'payoutRequests': [
                { 'requestReference': 'PO-3001', 'payoutAmount': 4000, 'agentCommission': 90 },
                { 'requestReference': 'PO-3002', 'payoutAmount': 6000, 'agentCommission': 150 },
                { 'requestReference': 'PO-3003', 'payoutAmount': 2500, 'agentCommission': 55 },
                { 'requestReference': 'PO-3004', 'payoutAmount': 5000, 'agentCommission': 115 }
              ]
            }
            """);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM payout_batch_item");
        jdbcTemplate.update("DELETE FROM payout_batch");
    }

    @Test
    void optimize_thenGetById_thenList_returnTheSameBatch() throws Exception {
        String postBody = mockMvc.perform(post(BASE_URL + "/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SPEC_EXAMPLE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.selectedPayouts", hasSize(2)))
                .andExpect(jsonPath("$.selectedPayouts[0].requestReference").value("PO-3002"))
                .andExpect(jsonPath("$.selectedPayouts[1].requestReference").value("PO-3004"))
                .andExpect(jsonPath("$.totalFloatConsumed").value(11000.0))
                .andExpect(jsonPath("$.totalAgentCommission").value(265.0))
                .andReturn().getResponse().getContentAsString();

        String batchId = JsonPath.read(postBody, "$.batchId");
        String createdAt = JsonPath.read(postBody, "$.createdAt");

        mockMvc.perform(get(BASE_URL + "/{batchId}", batchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value(batchId))
                .andExpect(jsonPath("$.selectedPayouts", hasSize(2)))
                .andExpect(jsonPath("$.selectedPayouts[0].requestReference").value("PO-3002"))
                .andExpect(jsonPath("$.selectedPayouts[0].payoutAmount").value(6000.0))
                .andExpect(jsonPath("$.selectedPayouts[0].agentCommission").value(150.0))
                .andExpect(jsonPath("$.selectedPayouts[1].requestReference").value("PO-3004"))
                .andExpect(jsonPath("$.totalFloatConsumed").value(11000.0))
                .andExpect(jsonPath("$.totalAgentCommission").value(265.0))
                .andExpect(jsonPath("$.createdAt").value(createdAt));

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].batchId").value(batchId))
                .andExpect(jsonPath("$.content[0].totalAgentCommission").value(265.0));

        Integer itemRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM payout_batch_item WHERE payout_batch_id = ?", Integer.class, UUID.fromString(batchId));
        assertThat(itemRows).isEqualTo(4);
    }

    @Test
    void optimize_nothingFits_returns200WithEmptyListAndIsStillPersisted() throws Exception {
        String postBody = mockMvc.perform(post(BASE_URL + "/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {
                                  'availablePayoutFloat': 100,
                                  'payoutRequests': [
                                    { 'requestReference': 'PO-1', 'payoutAmount': 500, 'agentCommission': 50 }
                                  ]
                                }
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedPayouts", hasSize(0)))
                .andExpect(jsonPath("$.totalFloatConsumed").value(0.0))
                .andExpect(jsonPath("$.totalAgentCommission").value(0.0))
                .andReturn().getResponse().getContentAsString();

        String batchId = JsonPath.read(postBody, "$.batchId");

        mockMvc.perform(get(BASE_URL + "/{batchId}", batchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedPayouts", hasSize(0)));
    }

    @Test
    void optimize_zeroCommission_isStoredButNotSelected() throws Exception {
        String postBody = mockMvc.perform(post(BASE_URL + "/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {
                                  'availablePayoutFloat': 100,
                                  'payoutRequests': [
                                    { 'requestReference': 'PO-1', 'payoutAmount': 10, 'agentCommission': 0 }
                                  ]
                                }
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedPayouts", hasSize(0)))
                .andExpect(jsonPath("$.totalAgentCommission").value(0.0))
                .andReturn().getResponse().getContentAsString();

        String batchId = JsonPath.read(postBody, "$.batchId");
        Boolean selected = jdbcTemplate.queryForObject(
                "SELECT selected FROM payout_batch_item WHERE payout_batch_id = ?", Boolean.class, UUID.fromString(batchId));
        assertThat(selected).isFalse();
    }

    @Test
    void list_returnsNewestFirstAndIsPaginated() throws Exception {
        String first = createBatch();
        String second = createBatch();
        String third = createBatch();

        mockMvc.perform(get(BASE_URL).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].batchId").value(third))
                .andExpect(jsonPath("$.content[1].batchId").value(second));

        mockMvc.perform(get(BASE_URL).param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].batchId").value(first));
    }

    @Test
    void optimize_sameIdempotencyKeyTwice_returnsSameBatchAndSavesOnce() throws Exception {
        String firstBody = mockMvc.perform(post(BASE_URL + "/optimize")
                        .header("Idempotency-Key", "retry-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SPEC_EXAMPLE))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String secondBody = mockMvc.perform(post(BASE_URL + "/optimize")
                        .header("Idempotency-Key", "retry-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SPEC_EXAMPLE))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String firstId = JsonPath.read(firstBody, "$.batchId");
        String secondId = JsonPath.read(secondBody, "$.batchId");
        assertThat(secondId).isEqualTo(firstId);
        assertThat(countBatches()).isEqualTo(1);
    }

    @Test
    void getById_unknownId_returns404() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{batchId}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void optimize_invalidInput_returns400AndSavesNothing() throws Exception {
        mockMvc.perform(post(BASE_URL + "/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {
                                  'availablePayoutFloat': 100,
                                  'payoutRequests': [
                                    { 'requestReference': 'PO-1', 'payoutAmount': 10.005, 'agentCommission': 5 }
                                  ]
                                }
                                """)))
                .andExpect(status().isBadRequest());

        assertThat(countBatches()).isZero();
    }

    @Test
    void optimize_floatTooLargeForOptimizer_returns400AndSavesNothing() throws Exception {
        mockMvc.perform(post(BASE_URL + "/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {
                                  'availablePayoutFloat': 5000000,
                                  'payoutRequests': [
                                    { 'requestReference': 'PO-1', 'payoutAmount': 100, 'agentCommission': 5 },
                                    { 'requestReference': 'PO-2', 'payoutAmount': 200, 'agentCommission': 8 }
                                  ]
                                }
                                """)))
                .andExpect(status().isBadRequest());

        assertThat(countBatches()).isZero();
    }

    private String createBatch() throws Exception {
        String body = mockMvc.perform(post(BASE_URL + "/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SPEC_EXAMPLE))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.batchId");
    }

    private int countBatches() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM payout_batch", Integer.class);
    }

    private static String json(String singleQuoted) {
        return singleQuoted.replace('\'', '"');
    }
}
