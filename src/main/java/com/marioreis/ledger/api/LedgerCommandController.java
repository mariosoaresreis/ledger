package com.marioreis.ledger.api;

import com.marioreis.ledger.service.LedgerCommandService;
import com.marioreis.ledger.service.StoredCommandResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class LedgerCommandController {

    private final LedgerCommandService ledgerCommandService;

    public LedgerCommandController(LedgerCommandService ledgerCommandService) {
        this.ledgerCommandService = ledgerCommandService;
    }

    @PostMapping("/accounts")
    public ResponseEntity<CommandResponse> createAccount(@RequestHeader("Idempotency-Key") UUID idempotencyKey,
                                                         @Valid @RequestBody CreateAccountRequest request) {
        return toResponse(ledgerCommandService.createAccount(idempotencyKey, request));
    }

    @PostMapping("/accounts/{accountId}/credits")
    public ResponseEntity<CommandResponse> creditAccount(@RequestHeader("Idempotency-Key") UUID idempotencyKey,
                                                         @PathVariable UUID accountId,
                                                         @Valid @RequestBody MoneyCommandRequest request) {
        return toResponse(ledgerCommandService.creditAccount(idempotencyKey, accountId, request));
    }

    @PostMapping("/accounts/{accountId}/debits")
    public ResponseEntity<CommandResponse> debitAccount(@RequestHeader("Idempotency-Key") UUID idempotencyKey,
                                                        @PathVariable UUID accountId,
                                                        @Valid @RequestBody MoneyCommandRequest request) {
        return toResponse(ledgerCommandService.debitAccount(idempotencyKey, accountId, request));
    }

    @PostMapping("/transfers")
    public ResponseEntity<CommandResponse> transfer(@RequestHeader("Idempotency-Key") UUID idempotencyKey,
                                                    @Valid @RequestBody TransferRequest request) {
        return toResponse(ledgerCommandService.transfer(idempotencyKey, request));
    }

    @PatchMapping("/accounts/{accountId}/status")
    public ResponseEntity<CommandResponse> changeStatus(@RequestHeader("Idempotency-Key") UUID idempotencyKey,
                                                        @PathVariable UUID accountId,
                                                        @Valid @RequestBody UpdateAccountStatusRequest request) {
        return toResponse(ledgerCommandService.changeStatus(idempotencyKey, accountId, request));
    }

    private ResponseEntity<CommandResponse> toResponse(StoredCommandResponse storedCommandResponse) {
        return ResponseEntity.status(storedCommandResponse.httpStatus()).body(storedCommandResponse.response());
    }
}

