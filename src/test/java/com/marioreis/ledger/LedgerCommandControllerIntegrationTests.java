package com.marioreis.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1)
@TestPropertySource(properties = {
    "ledger.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
class LedgerCommandControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("delete from ledger_events");
        jdbcTemplate.update("delete from outbox");
        jdbcTemplate.update("delete from idempotency_records");
        jdbcTemplate.update("delete from accounts");
    }

    @Test
    void createAccountPersistsAccountEventAndOutbox() throws Exception {
        String response = mockMvc.perform(post("/api/v1/accounts")
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ownerId": "3dc1f53a-a4e4-4b57-a855-f1bc15cdc52c",
                                  "currency": "usd"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operation").value("CREATE_ACCOUNT"))
                .andExpect(jsonPath("$.events[0].eventType").value("ACCOUNT_CREATED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        UUID accountId = UUID.fromString(json.get("accountId").asText());

        assertThat(count("select count(*) from accounts where id = ?", accountId)).isEqualTo(1);
        assertThat(count("select count(*) from ledger_events where account_id = ?", accountId)).isEqualTo(1);
        assertThat(count("select count(*) from outbox where account_id = ?", accountId)).isEqualTo(1);
        assertThat(count("select count(*) from idempotency_records")).isEqualTo(1);
    }

    @Test
    void createAccountIsIdempotentForRepeatedRequest() throws Exception {
        UUID idempotencyKey = UUID.randomUUID();
        String requestBody = """
                {
                  "ownerId": "bd65a703-7ea5-4459-a8fe-ac8516608dd0",
                  "currency": "EUR"
                }
                """;

        String firstResponse = mockMvc.perform(post("/api/v1/accounts")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String secondResponse = mockMvc.perform(post("/api/v1/accounts")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode firstJson = objectMapper.readTree(firstResponse);
        JsonNode secondJson = objectMapper.readTree(secondResponse);

        assertThat(secondJson.get("commandId").asText()).isEqualTo(firstJson.get("commandId").asText());
        assertThat(secondJson.get("idempotencyKey").asText()).isEqualTo(firstJson.get("idempotencyKey").asText());
        assertThat(secondJson.get("accountId").asText()).isEqualTo(firstJson.get("accountId").asText());
        assertThat(secondJson.get("operation").asText()).isEqualTo("CREATE_ACCOUNT");
        assertThat(secondJson.get("events")).isEqualTo(firstJson.get("events"));
        assertThat(count("select count(*) from accounts")).isEqualTo(1);
        assertThat(count("select count(*) from ledger_events")).isEqualTo(1);
        assertThat(count("select count(*) from outbox")).isEqualTo(1);
    }

    @Test
    void debitRejectsInsufficientBalance() throws Exception {
        UUID accountId = createAccount("USD");

        mockMvc.perform(post("/api/v1/accounts/{accountId}/debits", accountId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 10.00,
                                  "currency": "USD",
                                  "reference": "atm-withdrawal"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Insufficient balance for account %s".formatted(accountId)));

        assertThat(count("select count(*) from ledger_events where event_type = 'ACCOUNT_DEBITED'")).isZero();
    }

    @Test
    void transferWritesSagaStyleEventsAndStatusChangeBlocksNewCredits() throws Exception {
        UUID sourceId = createAccount("USD");
        UUID targetId = createAccount("USD");
        credit(sourceId, "75.00", "seed-funds");

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceAccountId": "%s",
                                  "targetAccountId": "%s",
                                  "amount": 20.00,
                                  "currency": "USD"
                                }
                                """.formatted(sourceId, targetId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("TRANSFER"))
                .andExpect(jsonPath("$.events.length()").value(3));

        assertThat(count("select count(*) from ledger_events where event_type = 'TRANSFER_INITIATED'")).isEqualTo(1);
        assertThat(count("select count(*) from ledger_events where event_type = 'ACCOUNT_DEBITED'")).isEqualTo(1);
        assertThat(count("select count(*) from ledger_events where event_type = 'ACCOUNT_CREDITED'")).isEqualTo(2);
        assertThat(count("select count(*) from outbox")).isEqualTo(6);

        mockMvc.perform(patch("/api/v1/accounts/{accountId}/status", targetId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "FROZEN"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].eventType").value("ACCOUNT_STATUS_CHANGED"));

        mockMvc.perform(post("/api/v1/accounts/{accountId}/credits", targetId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 1.00,
                                  "currency": "USD",
                                  "reference": "blocked-credit"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Account %s is not ACTIVE".formatted(targetId)));
    }

    @Test
    void swaggerUiIsExposed() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    private UUID createAccount(String currency) throws Exception {
        String response = mockMvc.perform(post("/api/v1/accounts")
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ownerId": "%s",
                                  "currency": "%s"
                                }
                                """.formatted(UUID.randomUUID(), currency)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("accountId").asText());
    }

    private void credit(UUID accountId, String amount, String reference) throws Exception {
        mockMvc.perform(post("/api/v1/accounts/{accountId}/credits", accountId)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": %s,
                                  "currency": "USD",
                                  "reference": "%s"
                                }
                                """.formatted(amount, reference)))
                .andExpect(status().isOk());
    }

    private int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}


