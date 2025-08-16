package com.sun.transfer_service.service;

import com.sun.transfer_service.client.LedgerClient;
import com.sun.transfer_service.dto.TransferRequest;
import com.sun.transfer_service.dto.TransferResponse;
import com.sun.transfer_service.model.IdempotencyKey;
import com.sun.transfer_service.model.Transfer;
import com.sun.transfer_service.repository.IdempotencyKeyRepository;
import com.sun.transfer_service.repository.TransferRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final TransferRepository transferRepository;
    private final IdempotencyKeyRepository keyRepository;
    private final LedgerClient ledgerClient;

    @Transactional
    public TransferResponse createTransfer(TransferRequest request, String idempotencyKey) {
        validateRequest(request);

        // Check idempotency
        IdempotencyKey existing = keyRepository.findById(idempotencyKey).orElse(null);
        if (existing != null) {
            if (existing.getCreatedAt() != null &&
                    existing.getCreatedAt().isBefore(LocalDateTime.now().minus(IDEMPOTENCY_TTL))) {
                keyRepository.delete(existing);
                log.info("Idempotency key expired; reprocessing. key={}", idempotencyKey);
            } else {
                Transfer t = existing.getTransfer();
                log.info("Idempotent replay. key={}, transferId={}, status={}",
                        idempotencyKey, t.getTransferId(), t.getStatus());
                return TransferResponse.builder()
                        .transferId(t.getTransferId())
                        .status(t.getStatus())
                        .message("Idempotent replay")
                        .build();
            }
        }

        String transferId = UUID.randomUUID().toString();

        // Call Ledger
        TransferResponse ledgerResp = ledgerClient.transferToLedger(
                transferId, request.getFromAccountId(), request.getToAccountId(), request.getAmount());

        // Persist Transfer
        Transfer saved = transferRepository.save(Transfer.builder()
                .fromAccountId(request.getFromAccountId())
                .toAccountId(request.getToAccountId())
                .amount(request.getAmount())
                .transferId(transferId)
                .status(ledgerResp.getStatus())
                .message(ledgerResp.getMessage())
                .build());

        // Bind idempotency key
        keyRepository.save(IdempotencyKey.builder()
                .key(idempotencyKey)
                .transfer(saved)
                .build());

        log.info("Transfer recorded. transferId={}, status={}", transferId, ledgerResp.getStatus());
        return ledgerResp;
    }

    public Transfer getByTransferId(String transferId) {
        return transferRepository.findByTransferId(transferId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transfer not found"));
    }

    /** Async batch processing using LedgerClient.transferToLedgerAsync */
    public List<TransferResponse> processBatch(List<TransferRequest> requests, String batchKey) {
        if (requests.size() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Batch size cannot exceed 20");
        }
        MessageDigest sha256 = getSha256();

        List<Mono<TransferResponse>> monos = requests.stream()
                .map(req -> {
                    String derivedKey = deriveKey(batchKey, req, sha256);

                    // Use async Ledger call
                    return Mono.fromCallable(() -> {
                        // Check idempotency & save Transfer like normal
                        return createTransfer(req, derivedKey);
                    });
                })
                .collect(Collectors.toList());

        // Run all in parallel and block until completion
        return Mono.zip(monos, results -> {
            return List.of(results).stream()
                    .map(r -> (TransferResponse) r)
                    .collect(Collectors.toList());
        }).block();
    }

    private void validateRequest(TransferRequest r) {
        Assert.notNull(r.getFromAccountId(), "fromAccountId is required");
        Assert.notNull(r.getToAccountId(), "toAccountId is required");
        Assert.isTrue(!r.getFromAccountId().equals(r.getToAccountId()), "from/to must differ");
        BigDecimal amt = r.getAmount();
        if (amt == null || amt.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be > 0");
        }
    }

    private static MessageDigest getSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Derive a stable key per item: header + SHA256(from|to|amount) */
    private static String deriveKey(String batchKey, TransferRequest r, MessageDigest md) {
        String payload = r.getFromAccountId() + "|" + r.getToAccountId() + "|" + r.getAmount();
        byte[] digest = md.digest(payload.getBytes());
        return batchKey + ":" + HexFormat.of().formatHex(digest);
    }
}
