package com.marioreis.ledger.api;

import com.marioreis.ledger.service.LedgerCommandService;
import com.marioreis.ledger.service.StoredCommandResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Ledger Commands", description = "CQRS command endpoints for ledger operations (account creation, transfers, debits, credits)")
public class LedgerCommandController {

    private final LedgerCommandService ledgerCommandService;

    public LedgerCommandController(LedgerCommandService ledgerCommandService) {
        this.ledgerCommandService = ledgerCommandService;
    }

    @PostMapping("/accounts")
    @Operation(
            summary = "Create a new account",
            description = "Creates a new account for the specified owner. Requires an Idempotency-Key header.",
            tags = {"Accounts"}
    )
    @ApiResponse(responseCode = "202", description = "Account creation initiated",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = CommandResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid request parameters")
    @ApiResponse(responseCode = "409", description = "Idempotency key conflict")
    public ResponseEntity<CommandResponse> createAccount(@RequestHeader("Idempotency-Key") UUID idempotencyKey,
                                                         @Valid @RequestBody(
                                                                 description = "Account creation parameters",
                                                                 required = true,
                                                                 content = @Content(schema = @Schema(implementation = CreateAccountRequest.class))
                                                         ) CreateAccountRequest request) {
        return toResponse(ledgerCommandService.createAccount(idempotencyKey, request));
    }

    @PostMapping("/accounts/{accountId}/credits")
    @Operation(
            summary = "Credit an account",
            description = "Credits an account with funds. Requires an Idempotency-Key header for idempotent execution.",
            tags = {"Accounts"}
    )
    @ApiResponse(responseCode = "202", description = "Credit operation accepted",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = CommandResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid amount or parameters")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @ApiResponse(responseCode = "409", description = "Account is frozen or closed")
    public ResponseEntity<CommandResponse> creditAccount(@RequestHeader("Idempotency-Key") UUID idempotencyKey,
                                                         @PathVariable UUID accountId,
                                                         @Valid @RequestBody(
                                                                 description = "Credit transaction parameters",
                                                                 required = true,
                                                                 content = @Content(schema = @Schema(implementation = MoneyCommandRequest.class))
                                                         ) MoneyCommandRequest request) {
        return toResponse(ledgerCommandService.creditAccount(idempotencyKey, accountId, request));
    }

    @PostMapping("/accounts/{accountId}/debits")
    @Operation(
            summary = "Debit an account",
            description = "Debits an account (withdraws funds). Requires an Idempotency-Key header. Account must have sufficient balance.",
            tags = {"Accounts"}
    )
    @ApiResponse(responseCode = "202", description = "Debit operation accepted",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = CommandResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid amount or parameters")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @ApiResponse(responseCode = "409", description = "Insufficient balance, account frozen/closed, or idempotency conflict")
    public ResponseEntity<CommandResponse> debitAccount(@RequestHeader("Idempotency-Key") UUID idempotencyKey,
                                                        @PathVariable UUID accountId,
                                                        @Valid @RequestBody(
                                                                description = "Debit transaction parameters",
                                                                required = true,
                                                                content = @Content(schema = @Schema(implementation = MoneyCommandRequest.class))
                                                        ) MoneyCommandRequest request) {
        return toResponse(ledgerCommandService.debitAccount(idempotencyKey, accountId, request));
    }

    @PostMapping("/transfers")
    @Operation(
            summary = "Initiate a transfer",
            description = "Transfers funds from one account to another (choreography saga pattern). Requires an Idempotency-Key header.",
            tags = {"Transfers"}
    )
    @ApiResponse(responseCode = "202", description = "Transfer initiated (saga started)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = CommandResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid request parameters")
    @ApiResponse(responseCode = "404", description = "Source or target account not found")
    @ApiResponse(responseCode = "409", description = "Insufficient balance, account frozen/closed, or idempotency conflict")
    public ResponseEntity<CommandResponse> transfer(@RequestHeader("Idempotency-Key") UUID idempotencyKey,
                                                    @Valid @RequestBody(
                                                            description = "Transfer parameters",
                                                            required = true,
                                                            content = @Content(schema = @Schema(implementation = TransferRequest.class))
                                                    ) TransferRequest request) {
        return toResponse(ledgerCommandService.transfer(idempotencyKey, request));
    }

    @PatchMapping("/accounts/{accountId}/status")
    @Operation(
            summary = "Update account status",
            description = "Changes the status of an account (ACTIVE, FROZEN, CLOSED). Requires an Idempotency-Key header.",
            tags = {"Accounts"}
    )
    @ApiResponse(responseCode = "202", description = "Status update initiated",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = CommandResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid status value")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @ApiResponse(responseCode = "409", description = "Invalid status transition")
    public ResponseEntity<CommandResponse> changeStatus(@RequestHeader("Idempotency-Key") UUID idempotencyKey,
                                                        @PathVariable UUID accountId,
                                                        @Valid @RequestBody(
                                                                description = "New account status",
                                                                required = true,
                                                                content = @Content(schema = @Schema(implementation = UpdateAccountStatusRequest.class))
                                                        ) UpdateAccountStatusRequest request) {
        return toResponse(ledgerCommandService.changeStatus(idempotencyKey, accountId, request));
    }

    private ResponseEntity<CommandResponse> toResponse(StoredCommandResponse storedCommandResponse) {
        return ResponseEntity.status(storedCommandResponse.httpStatus()).body(storedCommandResponse.response());
    }
}

