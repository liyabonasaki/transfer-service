package com.sun.transfer_service.controller;

import com.sun.transfer_service.dto.TransferRequest;
import com.sun.transfer_service.dto.TransferResponse;
import com.sun.transfer_service.model.Transfer;
import com.sun.transfer_service.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;

@RestController
@RequestMapping(value = "/transfer", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Transfer Service", description = "APIs for creating and querying transfers")
public class TransferController {

    private final TransferService transferService;

    @Operation(
            summary = "Create a single transfer",
            description = "Creates a single transfer between two accounts with idempotency support",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Transfer completed successfully",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TransferResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Validation error or missing Idempotency-Key",
                            content = @Content)
            }
    )
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public TransferResponse createTransfer(
            @Valid @RequestBody TransferRequest request,
            @Parameter(description = "Idempotency key to prevent duplicate transfers", required = true)
            @RequestHeader(name = "Idempotency-Key", required = true) String idempotencyKey) {

        if (!StringUtils.hasText(idempotencyKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required");
        }
        return transferService.createTransfer(request, idempotencyKey);
    }

    @Operation(
            summary = "Get transfer status",
            description = "Retrieves the status of a transfer by its ID",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Transfer found",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Transfer.class))),
                    @ApiResponse(responseCode = "404", description = "Transfer not found",
                            content = @Content)
            }
    )
    @GetMapping("/{id}")
    public Transfer getStatus(
            @Parameter(description = "Transfer ID", required = true)
            @PathVariable("id") String transferId) {

        return transferService.getByTransferId(transferId);
    }

    @Operation(
            summary = "Create batch transfers",
            description = "Creates multiple transfers in a batch with idempotency support. Max batch size is 20",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Batch processed successfully",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TransferResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Validation error, missing Idempotency-Key, or batch size exceeded",
                            content = @Content)
            }
    )
    @PostMapping(value = "/batch", consumes = MediaType.APPLICATION_JSON_VALUE)
    public List<TransferResponse> createBatch(
            @RequestBody List<@Valid TransferRequest> requests,
            @Parameter(description = "Idempotency key to prevent duplicate batch processing", required = true)
            @RequestHeader(name = "Idempotency-Key", required = true) String idempotencyKey) {

        if (!StringUtils.hasText(idempotencyKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required");
        }
        return transferService.processBatch(requests, idempotencyKey);
    }
}
