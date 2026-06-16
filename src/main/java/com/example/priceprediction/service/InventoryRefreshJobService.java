package com.example.priceprediction.service;

import com.example.priceprediction.entity.InventoryRefreshJobEntity;
import com.example.priceprediction.entity.InventoryRefreshJobStatus;
import com.example.priceprediction.repository.InventoryRefreshJobRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryRefreshJobService {

    private static final Duration PROCESSING_TIMEOUT = Duration.ofMinutes(10);
    private static final int MAX_ERROR_LENGTH = 4000;

    private final InventoryRefreshJobRepository jobRepository;

    @Transactional
    public BeginResult begin(String requestId, String steamId) {
        LocalDateTime now = LocalDateTime.now();

        int inserted = jobRepository.insertProcessingIfAbsent(requestId, steamId);
        if (inserted > 0) {
            return BeginResult.start(JobStartReason.CREATED);
        }

        InventoryRefreshJobEntity existing = jobRepository.findWithLockByRequestId(requestId)
                .orElseThrow(() -> new IllegalStateException("库存刷新任务唯一约束冲突后未找到记录: " + requestId));

        if (existing.getStatus() == InventoryRefreshJobStatus.DONE) {
            return BeginResult.skip(JobSkipReason.ALREADY_DONE);
        }

        if (existing.getStatus() == InventoryRefreshJobStatus.FAILED) {
            markProcessing(existing, steamId, now);
            return BeginResult.start(JobStartReason.RETRY_FAILED);
        }

        if (isProcessingTimedOut(existing, now)) {
            markProcessing(existing, steamId, now);
            return BeginResult.start(JobStartReason.TAKEOVER_TIMEOUT);
        }

        return BeginResult.skip(JobSkipReason.PROCESSING);
    }

    @Transactional
    public void markDone(String requestId) {
        InventoryRefreshJobEntity job = jobRepository.findWithLockByRequestId(requestId)
                .orElseThrow(() -> new IllegalStateException("库存刷新任务不存在: " + requestId));
        job.setStatus(InventoryRefreshJobStatus.DONE);
        job.setFinishedAt(LocalDateTime.now());
        job.setLastError(null);
    }

    @Transactional
    public void markFailed(String requestId, Exception exception) {
        InventoryRefreshJobEntity job = jobRepository.findWithLockByRequestId(requestId)
                .orElseThrow(() -> new IllegalStateException("库存刷新任务不存在: " + requestId));
        job.setStatus(InventoryRefreshJobStatus.FAILED);
        job.setFinishedAt(LocalDateTime.now());
        job.setLastError(truncateError(exception));
    }

    private void markProcessing(InventoryRefreshJobEntity job, String steamId, LocalDateTime now) {
        job.setSteamId(steamId);
        job.setStatus(InventoryRefreshJobStatus.PROCESSING);
        job.setAttempts(job.getAttempts() + 1);
        job.setStartedAt(now);
        job.setFinishedAt(null);
        job.setLastError(null);
    }

    private boolean isProcessingTimedOut(InventoryRefreshJobEntity job, LocalDateTime now) {
        LocalDateTime startedAt = job.getStartedAt();
        return startedAt == null || startedAt.plus(PROCESSING_TIMEOUT).isBefore(now);
    }

    private String truncateError(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getName();
        }
        if (message.length() <= MAX_ERROR_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_LENGTH);
    }

    @Getter
    @RequiredArgsConstructor
    public static class BeginResult {
        private final boolean shouldProcess;
        private final JobStartReason startReason;
        private final JobSkipReason skipReason;

        static BeginResult start(JobStartReason reason) {
            return new BeginResult(true, reason, null);
        }

        static BeginResult skip(JobSkipReason reason) {
            return new BeginResult(false, null, reason);
        }
    }

    public enum JobStartReason {
        CREATED,
        RETRY_FAILED,
        TAKEOVER_TIMEOUT
    }

    public enum JobSkipReason {
        ALREADY_DONE,
        PROCESSING
    }
}
