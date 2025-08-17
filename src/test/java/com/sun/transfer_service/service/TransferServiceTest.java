package com.sun.transfer_service.service;

import com.sun.transfer_service.client.LedgerClient;
import com.sun.transfer_service.dto.TransferRequest;
import com.sun.transfer_service.dto.TransferResponse;
import com.sun.transfer_service.model.IdempotencyKey;
import com.sun.transfer_service.model.Transfer;
import com.sun.transfer_service.repository.IdempotencyKeyRepository;
import com.sun.transfer_service.repository.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TransferService with real behavior mocked for:
 * - LedgerClient
 * - TransferRepository
 * - IdempotencyKeyRepository
 * Uses a synchronous executor so batch tests run deterministically.
 */
class TransferServiceTest {

    // ---- Mocks for dependencies ----
    @Mock private TransferRepository transferRepository;
    @Mock private IdempotencyKeyRepository idempotencyKeyRepository;
    @Mock private LedgerClient ledgerClient;

    private TransferService transferService;

    /**
     * Simple executor that runs tasks on the calling thread.
     * Implements both Spring TaskExecutor and java.util.concurrent.Executor
     * so it's compatible with CompletableFuture.supplyAsync(..., executor).
     */
    static class DirectExecutor implements TaskExecutor, Executor {
        @Override public void execute(Runnable command) { command.run(); }
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TaskExecutor direct = new DirectExecutor();
        transferService = new TransferService(
                transferRepository,
                idempotencyKeyRepository,
                ledgerClient
        );
    }

    @Test
    void createTransfer_success_callsLedger_persists_andReturnsResponse() {
        // Arrange
        TransferRequest req = new TransferRequest(1L, 2L, new BigDecimal("100.00"));
        String idemKey = "idem-123";

        // No previous idempotency record
        when(idempotencyKeyRepository.findById(idemKey)).thenReturn(Optional.empty());

        // Ledger responds SUCCESS, echoing back the transferId it received
        when(ledgerClient.transferToLedger(anyString(), eq(1L), eq(2L), eq(new BigDecimal("100.00"))))
                .thenAnswer(inv -> {
                    String tid = inv.getArgument(0, String.class);
                    return TransferResponse.builder()
                            .transferId(tid)
                            .status("SUCCESS")
                            .message("Transfer completed")
                            .build();
                });

        // Persist returns the same entity passed in
        when(transferRepository.save(any(Transfer.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        when(idempotencyKeyRepository.save(any(IdempotencyKey.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Act
        TransferResponse resp = transferService.createTransfer(req, idemKey);

        // Assert
        assertNotNull(resp);
        assertEquals("SUCCESS", resp.getStatus());
        assertNotNull(resp.getTransferId());
        assertFalse(resp.getTransferId().isBlank());
        verify(ledgerClient, times(1))
                .transferToLedger(anyString(), eq(1L), eq(2L), eq(new BigDecimal("100.00")));
        verify(transferRepository, times(1)).save(any(Transfer.class));
        verify(idempotencyKeyRepository, times(1)).save(any(IdempotencyKey.class));
    }

    @Test
    void createTransfer_idempotentReplay_returnsPreviousResult_andSkipsLedger() {
        // Arrange
        TransferRequest req = new TransferRequest(1L, 2L, new BigDecimal("50.00"));
        String idemKey = "idem-replay";

        Transfer existing = Transfer.builder()
                .transferId("existing-tx-id")
                .fromAccountId(1L)
                .toAccountId(2L)
                .amount(new BigDecimal("50.00"))
                .status("SUCCESS")
                .message("Transfer completed")
                .build();

        IdempotencyKey key = IdempotencyKey.builder()
                .key(idemKey)
                .transfer(existing)
                .createdAt(LocalDateTime.now()) // not expired
                .build();

        when(idempotencyKeyRepository.findById(idemKey)).thenReturn(Optional.of(key));

        // Act
        TransferResponse resp = transferService.createTransfer(req, idemKey);

        // Assert
        assertNotNull(resp);
        assertEquals("existing-tx-id", resp.getTransferId());
        assertEquals("SUCCESS", resp.getStatus());
        assertEquals("Idempotent replay", resp.getMessage());
        verifyNoInteractions(ledgerClient);
        verify(transferRepository, never()).save(any());
    }

    @Test
    void getByTransferId_found_returnsEntity() {
        // Arrange
        String transferId = "abc-123";
        Transfer t = Transfer.builder()
                .transferId(transferId)
                .fromAccountId(10L)
                .toAccountId(20L)
                .amount(new BigDecimal("10.00"))
                .status("PENDING")
                .build();

        when(transferRepository.findByTransferId(transferId)).thenReturn(Optional.of(t));

        // Act
        Transfer result = transferService.getByTransferId(transferId);

        // Assert
        assertNotNull(result);
        assertEquals(10L, result.getFromAccountId());
        assertEquals("PENDING", result.getStatus());
        verify(transferRepository, times(1)).findByTransferId(transferId);
    }

    @Test
    void getByTransferId_notFound_throws404() {
        // Arrange
        String missing = "does-not-exist";
        when(transferRepository.findByTransferId(missing)).thenReturn(Optional.empty());

        // Act + Assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> transferService.getByTransferId(missing));
        assertEquals(404, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Transfer not found"));
        verify(transferRepository, times(1)).findByTransferId(missing);
    }

    @Test
    void processBatch_success_twoItems_runsConcurrentlyButDeterministically() {
        // Arrange
        String batchKey = "batch-123";
        List<TransferRequest> items = List.of(
                new TransferRequest(1L, 2L, new BigDecimal("10.00")),
                new TransferRequest(2L, 3L, new BigDecimal("20.00"))
        );

        // No idempotency hits during batch
        when(idempotencyKeyRepository.findById(anyString())).thenReturn(Optional.empty());
        when(idempotencyKeyRepository.save(any(IdempotencyKey.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Ledger: always success, echo transferId argument
        when(ledgerClient.transferToLedger(anyString(), anyLong(), anyLong(), any(BigDecimal.class)))
                .thenAnswer(inv -> {
                    String tid = inv.getArgument(0, String.class);
                    return TransferResponse.builder()
                            .transferId(tid)
                            .status("SUCCESS")
                            .message("Transfer completed")
                            .build();
                });

        when(transferRepository.save(any(Transfer.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Act
        List<TransferResponse> responses = transferService.processBatch(items, batchKey);

        // Assert
        assertEquals(2, responses.size());
        responses.forEach(r -> {
            assertEquals("SUCCESS", r.getStatus());
            assertNotNull(r.getTransferId());
            assertFalse(r.getTransferId().isBlank());
        });
        verify(ledgerClient, times(2)).transferToLedger(anyString(), anyLong(), anyLong(), any(BigDecimal.class));
        verify(transferRepository, times(2)).save(any(Transfer.class));
        verify(idempotencyKeyRepository, times(2)).save(any(IdempotencyKey.class));
    }
}
