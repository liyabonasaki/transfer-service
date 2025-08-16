package com.sun.transfer_service.service;

import com.sun.transfer_service.model.IdempotencyKey;
import com.sun.transfer_service.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class IdempotencyCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupJob.class);
    private static final Duration TTL = Duration.ofHours(24);

    private final IdempotencyKeyRepository repo;

    /** Run hourly to trim old keys */
    @Scheduled(fixedDelay = 60 * 60 * 1000L)
    public void cleanup() {
        var now = LocalDateTime.now().minus(TTL);
        // Simple scan + delete (ok for demo); in prod, write a custom query.
        var all = repo.findAll();
        long removed = all.stream()
                .filter(k -> k.getCreatedAt() != null && k.getCreatedAt().isBefore(now))
                .peek(repo::delete)
                .count();
        if (removed > 0) {
            log.info("Idempotency cleanup removed {} entries", removed);
        }
    }
}

