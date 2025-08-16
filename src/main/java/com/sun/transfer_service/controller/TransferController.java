package com.sun.transfer_service.controller;

import com.sun.transfer_service.dto.TransferRequest;
import com.sun.transfer_service.dto.TransferResponse;
import com.sun.transfer_service.model.Transfer;
import com.sun.transfer_service.service.TransferService;
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
public class TransferController {

    private final TransferService transferService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public TransferResponse createTransfer(@Valid @RequestBody TransferRequest request,
                                           @RequestHeader(name = "Idempotency-Key", required = true) String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required");
        }
        return transferService.createTransfer(request, idempotencyKey);
    }

    @GetMapping("/{id}")
    public Transfer getStatus(@PathVariable("id") String transferId) {
        return transferService.getByTransferId(transferId);
    }

    @PostMapping(value = "/batch", consumes = MediaType.APPLICATION_JSON_VALUE)
    public List<TransferResponse> createBatch(@RequestBody List<@Valid TransferRequest> requests,
                                              @RequestHeader(name = "Idempotency-Key", required = true) String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required");
        }
        return transferService.processBatch(requests, idempotencyKey);
    }
}

