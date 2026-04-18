package com.marioreis.ledger.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marioreis.ledger.api.CommandResponse;
import com.marioreis.ledger.api.CreateAccountRequest;
import com.marioreis.ledger.api.EventReceipt;
import com.marioreis.ledger.api.MoneyCommandRequest;
import com.marioreis.ledger.api.TransferRequest;
import com.marioreis.ledger.api.UpdateAccountStatusRequest;
import com.marioreis.ledger.domain.AccountAggregate;
import com.marioreis.ledger.domain.AccountStatus;
import com.marioreis.ledger.domain.LedgerEventType;
import com.marioreis.ledger.domain.LedgerException;
import com.marioreis.ledger.persistence.IdempotencyRecord;
import com.marioreis.ledger.persistence.IdempotencyRepository;
import com.marioreis.ledger.persistence.LedgerRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class LedgerCommandService {

    private final LedgerRepository ledgerRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final KafkaEventPublisher kafkaEventPublisher;

    public LedgerCommandService(LedgerRepository ledgerRepository,
                                IdempotencyRepository idempotencyRepository,
                                ObjectMapper objectMapper,
                                TransactionTemplate transactionTemplate,
                                KafkaEventPublisher kafkaEventPublisher) {
        this.ledgerRepository = ledgerRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.kafkaEventPublisher = kafkaEventPublisher;
    }

    public StoredCommandResponse createAccount(UUID idempotencyKey, CreateAccountRequest request) {
        return executeIdempotent(idempotencyKey, "CREATE_ACCOUNT", request, 201,
                () -> handleCreateAccount(idempotencyKey, request));
    }

    public StoredCommandResponse creditAccount(UUID idempotencyKey, UUID accountId, MoneyCommandRequest request) {
        return executeIdempotent(idempotencyKey, "ACCOUNT_CREDIT",
                new AccountScopedMoneyRequest(accountId, request.amount(), request.currency(), request.reference()), 200,
                () -> handleCredit(idempotencyKey, accountId, request));
    }

    public StoredCommandResponse debitAccount(UUID idempotencyKey, UUID accountId, MoneyCommandRequest request) {
        return executeIdempotent(idempotencyKey, "ACCOUNT_DEBIT",
                new AccountScopedMoneyRequest(accountId, request.amount(), request.currency(), request.reference()), 200,
                () -> handleDebit(idempotencyKey, accountId, request));
    }

    public StoredCommandResponse transfer(UUID idempotencyKey, TransferRequest request) {
        return executeIdempotent(idempotencyKey, "TRANSFER", request, 200,
                () -> handleTransfer(idempotencyKey, request));
    }

    public StoredCommandResponse changeStatus(UUID idempotencyKey, UUID accountId, UpdateAccountStatusRequest request) {
        return executeIdempotent(idempotencyKey, "ACCOUNT_STATUS_CHANGE",
                new AccountScopedStatusRequest(accountId, request.status()), 200,
                () -> handleStatusChange(idempotencyKey, accountId, request));
    }

    private StoredCommandResponse executeIdempotent(UUID idempotencyKey,
                                                    String operation,
                                                    Object fingerprintSource,
                                                    int successStatus,
                                                    Supplier<CommandResponse> action) {
        String requestHash = hash(operation, fingerprintSource);
        IdempotencyRecord existing = idempotencyRepository.findByKey(idempotencyKey).orElse(null);
        if (existing != null) {
            return replayExistingResponse(existing, requestHash);
        }

        try {
            CommandResponse response = transactionTemplate.execute(status -> {
                OffsetDateTime now = OffsetDateTime.now();
                idempotencyRepository.reserve(idempotencyKey, operation, requestHash, now);
                CommandResponse created = action.get();
                idempotencyRepository.complete(idempotencyKey, successStatus, toJson(created), OffsetDateTime.now());
                return created;
            });
            if (response == null) {
                throw new IllegalStateException("Command execution returned no response");
            }
            return new StoredCommandResponse(successStatus, response);
        } catch (DataIntegrityViolationException exception) {
            IdempotencyRecord record = idempotencyRepository.findByKey(idempotencyKey)
                    .orElseThrow(() -> exception);
            return replayExistingResponse(record, requestHash);
        }
    }

    private StoredCommandResponse replayExistingResponse(IdempotencyRecord record, String requestHash) {
        if (!record.requestHash().equals(requestHash)) {
            throw LedgerException.conflict("Idempotency key has already been used for a different request payload");
        }
        if (!record.completed()) {
            throw LedgerException.conflict("A request with this idempotency key is already being processed");
        }
        return new StoredCommandResponse(record.responseStatus(), fromJson(record.responseBody()));
    }

    private CommandResponse handleCreateAccount(UUID idempotencyKey, CreateAccountRequest request) {
        UUID accountId = UUID.randomUUID();
        OffsetDateTime occurredAt = OffsetDateTime.now();
        String currency = normalizeCurrency(request.currency());

        ledgerRepository.insertAccount(accountId, request.ownerId(), AccountStatus.ACTIVE, currency, occurredAt);
        EventReceipt receipt = appendEventAndOutbox(
                accountId,
                LedgerEventType.ACCOUNT_CREATED,
                new AccountCreatedPayload(accountId, request.ownerId(), currency, AccountStatus.ACTIVE),
                1L,
                occurredAt,
                idempotencyKey
        );

        return new CommandResponse(
                UUID.randomUUID(),
                idempotencyKey,
                "CREATE_ACCOUNT",
                "ACCEPTED",
                accountId,
                List.of(receipt),
                occurredAt
        );
    }

    private CommandResponse handleCredit(UUID idempotencyKey, UUID accountId, MoneyCommandRequest request) {
        AccountAggregate account = loadRequiredAccount(accountId);
        ensureActive(account);
        String currency = normalizeCurrency(request.currency());
        ensureCurrencyMatches(account, currency);

        OffsetDateTime occurredAt = OffsetDateTime.now();
        long nextVersion = account.version() + 1L;
        EventReceipt receipt = appendEventAndOutbox(
                account.id(),
                LedgerEventType.ACCOUNT_CREDITED,
                new MoneyMovementPayload(account.id(), request.amount(), currency, request.reference()),
                nextVersion,
                occurredAt,
                idempotencyKey
        );

        return new CommandResponse(
                UUID.randomUUID(),
                idempotencyKey,
                "ACCOUNT_CREDIT",
                "ACCEPTED",
                account.id(),
                List.of(receipt),
                occurredAt
        );
    }

    private CommandResponse handleDebit(UUID idempotencyKey, UUID accountId, MoneyCommandRequest request) {
        AccountAggregate account = loadRequiredAccount(accountId);
        ensureActive(account);
        String currency = normalizeCurrency(request.currency());
        ensureCurrencyMatches(account, currency);
        ensureSufficientBalance(account, request.amount());

        OffsetDateTime occurredAt = OffsetDateTime.now();
        long nextVersion = account.version() + 1L;
        EventReceipt receipt = appendEventAndOutbox(
                account.id(),
                LedgerEventType.ACCOUNT_DEBITED,
                new MoneyMovementPayload(account.id(), request.amount(), currency, request.reference()),
                nextVersion,
                occurredAt,
                idempotencyKey
        );

        return new CommandResponse(
                UUID.randomUUID(),
                idempotencyKey,
                "ACCOUNT_DEBIT",
                "ACCEPTED",
                account.id(),
                List.of(receipt),
                occurredAt
        );
    }

    private CommandResponse handleTransfer(UUID idempotencyKey, TransferRequest request) {
        if (request.sourceAccountId().equals(request.targetAccountId())) {
            throw LedgerException.unprocessable("Transfers require different source and target accounts");
        }

        AccountAggregate source = loadRequiredAccount(request.sourceAccountId());
        AccountAggregate target = loadRequiredAccount(request.targetAccountId());
        ensureActive(source);
        ensureActive(target);

        String currency = normalizeCurrency(request.currency());
        ensureCurrencyMatches(source, currency);
        ensureCurrencyMatches(target, currency);
        ensureSufficientBalance(source, request.amount());

        UUID transferId = UUID.randomUUID();
        OffsetDateTime occurredAt = OffsetDateTime.now();

        EventReceipt initiated = appendEventAndOutbox(
                transferId,
                LedgerEventType.TRANSFER_INITIATED,
                new TransferPayload(transferId, source.id(), target.id(), request.amount(), currency),
                1L,
                occurredAt,
                idempotencyKey
        );
        EventReceipt debited = appendEventAndOutbox(
                source.id(),
                LedgerEventType.ACCOUNT_DEBITED,
                new MoneyMovementPayload(source.id(), request.amount(), currency, "transfer:" + transferId),
                source.version() + 1L,
                occurredAt,
                idempotencyKey
        );
        EventReceipt credited = appendEventAndOutbox(
                target.id(),
                LedgerEventType.ACCOUNT_CREDITED,
                new MoneyMovementPayload(target.id(), request.amount(), currency, "transfer:" + transferId),
                target.version() + 1L,
                occurredAt,
                idempotencyKey
        );

        return new CommandResponse(
                UUID.randomUUID(),
                idempotencyKey,
                "TRANSFER",
                "ACCEPTED",
                transferId,
                List.of(initiated, debited, credited),
                occurredAt
        );
    }

    private CommandResponse handleStatusChange(UUID idempotencyKey, UUID accountId, UpdateAccountStatusRequest request) {
        AccountAggregate account = loadRequiredAccount(accountId);
        AccountStatus targetStatus = request.status();

        if (account.status() == targetStatus) {
            throw LedgerException.unprocessable("Account already has status " + targetStatus.name());
        }
        if (account.status() == AccountStatus.CLOSED) {
            throw LedgerException.unprocessable("Closed accounts cannot transition to another status");
        }

        OffsetDateTime occurredAt = OffsetDateTime.now();
        ledgerRepository.updateAccountStatus(account.id(), targetStatus);
        EventReceipt receipt = appendEventAndOutbox(
                account.id(),
                LedgerEventType.ACCOUNT_STATUS_CHANGED,
                new StatusChangedPayload(account.id(), account.status(), targetStatus),
                account.version() + 1L,
                occurredAt,
                idempotencyKey
        );

        return new CommandResponse(
                UUID.randomUUID(),
                idempotencyKey,
                "ACCOUNT_STATUS_CHANGE",
                "ACCEPTED",
                account.id(),
                List.of(receipt),
                occurredAt
        );
    }

    private AccountAggregate loadRequiredAccount(UUID accountId) {
        return ledgerRepository.findAccount(accountId)
                .orElseThrow(() -> LedgerException.notFound("Account %s was not found".formatted(accountId)));
    }

    private void ensureActive(AccountAggregate account) {
        if (!account.isActive()) {
            throw LedgerException.unprocessable("Account %s is not ACTIVE".formatted(account.id()));
        }
    }

    private void ensureCurrencyMatches(AccountAggregate account, String currency) {
        if (!account.currency().equals(currency)) {
            throw LedgerException.unprocessable("Currency mismatch for account %s".formatted(account.id()));
        }
    }

    private void ensureSufficientBalance(AccountAggregate account, BigDecimal amount) {
        if (account.balance().compareTo(amount) < 0) {
            throw LedgerException.unprocessable("Insufficient balance for account %s".formatted(account.id()));
        }
    }

    private EventReceipt appendEventAndOutbox(UUID accountId,
                                              LedgerEventType eventType,
                                              Object payload,
                                              long version,
                                              OffsetDateTime occurredAt,
                                              UUID idempotencyKey) {
        String jsonPayload = toJson(payload);
        try {
            ledgerRepository.appendEvent(accountId, eventType, jsonPayload, version, occurredAt, idempotencyKey);
        } catch (DataIntegrityViolationException exception) {
            throw LedgerException.conflict("Concurrent modification detected for aggregate %s".formatted(accountId));
        }
        ledgerRepository.appendOutbox(accountId, eventType, jsonPayload);
        kafkaEventPublisher.publish(accountId, eventType.name(), jsonPayload, occurredAt);
        return new EventReceipt(accountId, eventType.name(), version);
    }

    private String normalizeCurrency(String currency) {
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private String hash(String operation, Object fingerprintSource) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(operation.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) ':');
            digest.update(toJson(fingerprintSource).getBytes(StandardCharsets.UTF_8));
            byte[] bytes = digest.digest();
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize payload", exception);
        }
    }

    private CommandResponse fromJson(String json) {
        try {
            return objectMapper.readValue(json, CommandResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize cached response", exception);
        }
    }

    private record AccountScopedMoneyRequest(UUID accountId, BigDecimal amount, String currency, String reference) {
    }

    private record AccountScopedStatusRequest(UUID accountId, AccountStatus status) {
    }

    private record AccountCreatedPayload(UUID accountId, UUID ownerId, String currency, AccountStatus status) {
    }

    private record MoneyMovementPayload(UUID accountId, BigDecimal amount, String currency, String reference) {
    }

    private record TransferPayload(UUID transferId, UUID sourceAccountId, UUID targetAccountId, BigDecimal amount,
                                   String currency) {
    }

    private record StatusChangedPayload(UUID accountId, AccountStatus fromStatus, AccountStatus toStatus) {
    }
}

