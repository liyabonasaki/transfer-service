package com.sun.transfer_service.client;

import com.sun.transfer_service.dto.TransferResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LedgerClient {

    private static final Logger log = LoggerFactory.getLogger(LedgerClient.class);

    private final WebClient ledgerWebClient;

    @CircuitBreaker(name = "ledger", fallbackMethod = "fallbackTransfer")
    public TransferResponse transferToLedger(String transferId,
                                             Long fromAccountId,
                                             Long toAccountId,
                                             BigDecimal amount) {
        log.info("Calling Ledger: transferId={}, from={}, to={}, amount={}",
                transferId, fromAccountId, toAccountId, amount);

        return ledgerWebClient.post()
                .uri("/ledger/transfer")
                .bodyValue(Map.of(
                        "transferId", transferId,
                        "fromAccountId", fromAccountId,
                        "toAccountId", toAccountId,
                        "amount", amount
                ))
                .retrieve()
                .bodyToMono(TransferResponse.class)
                .block();
    }

    /** Non-blocking version for batch transfers */
    @CircuitBreaker(name = "ledger", fallbackMethod = "fallbackTransferAsync")
    public Mono<TransferResponse> transferToLedgerAsync(String transferId,
                                                        Long fromAccountId,
                                                        Long toAccountId,
                                                        BigDecimal amount) {
        log.info("Calling Ledger async: transferId={}, from={}, to={}, amount={}",
                transferId, fromAccountId, toAccountId, amount);

        return ledgerWebClient.post()
                .uri("/ledger/transfer")
                .bodyValue(Map.of(
                        "transferId", transferId,
                        "fromAccountId", fromAccountId,
                        "toAccountId", toAccountId,
                        "amount", amount
                ))
                .retrieve()
                .bodyToMono(TransferResponse.class);
    }

    @SuppressWarnings("unused")
    private TransferResponse fallbackTransfer(String transferId,
                                              Long fromAccountId,
                                              Long toAccountId,
                                              BigDecimal amount,
                                              Throwable ex) {
        log.error("Ledger call failed (circuit/failure). transferId={}, error={}",
                transferId, ex.toString());
        return TransferResponse.builder()
                .transferId(transferId)
                .status("FAILURE")
                .message("Ledger service unavailable")
                .build();
    }

    @SuppressWarnings("unused")
    private Mono<TransferResponse> fallbackTransferAsync(String transferId,
                                                         Long fromAccountId,
                                                         Long toAccountId,
                                                         BigDecimal amount,
                                                         Throwable ex) {
        log.error("Ledger async call failed (circuit/failure). transferId={}, error={}",
                transferId, ex.toString());
        return Mono.just(TransferResponse.builder()
                .transferId(transferId)
                .status("FAILURE")
                .message("Ledger service unavailable")
                .build());
    }
}
