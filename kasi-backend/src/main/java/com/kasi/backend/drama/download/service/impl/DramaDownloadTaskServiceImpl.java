package com.kasi.backend.drama.download.service.impl;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.drama.download.dto.CreateDramaDownloadTaskDTO;
import com.kasi.backend.drama.download.entity.DramaDownloadTask;
import com.kasi.backend.drama.download.enums.DramaDownloadTaskStatus;
import com.kasi.backend.drama.download.mapper.DramaDownloadTaskMapper;
import com.kasi.backend.drama.download.service.DramaDownloadTaskService;
import com.kasi.backend.drama.download.service.DramaMediaDownloader;
import com.kasi.backend.drama.download.service.DramaMediaExpiredException;
import com.kasi.backend.drama.service.UserPromotionDramaService;
import com.kasi.backend.drama.vo.DramaContentResourceVO;
import com.kasi.backend.drama.download.vo.DramaDownloadTaskVO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class DramaDownloadTaskServiceImpl implements DramaDownloadTaskService {
    private static final long MAX_TASK_BYTES = 10L * 1024 * 1024 * 1024;
    private final DramaDownloadTaskMapper taskMapper;
    private final UserPromotionDramaService dramaService;
    private final TaskExecutor taskExecutor;
    private final DramaMediaDownloader mediaDownloader;
    private final Path downloadDir;
    private final String ffmpegPath;
    private final int maxEpisodes;
    private final int expireHours;
    private final Clock clock;
    private final JsonMapper objectMapper = JsonMapper.builder().build();

    public DramaDownloadTaskServiceImpl(DramaDownloadTaskMapper taskMapper,
                                        UserPromotionDramaService dramaService,
                                        @Qualifier("dramaDownloadTaskExecutor") TaskExecutor taskExecutor,
                                        DramaMediaDownloader mediaDownloader,
                                        @Value("${app.drama.download.dir:./var/downloads}") String downloadDir,
                                        @Value("${app.drama.download.ffmpeg-path:ffmpeg}") String ffmpegPath,
                                        @Value("${app.drama.download.max-episodes:100}") int maxEpisodes,
                                        @Value("${app.drama.download.expire-hours:24}") int expireHours,
                                        Clock clock) {
        this.taskMapper = taskMapper;
        this.dramaService = dramaService;
        this.taskExecutor = taskExecutor;
        this.mediaDownloader = mediaDownloader;
        this.downloadDir = Path.of(downloadDir).toAbsolutePath().normalize();
        this.ffmpegPath = ffmpegPath;
        this.maxEpisodes = maxEpisodes;
        this.expireHours = expireHours;
        this.clock = clock;
    }

    @Override
    @Transactional
    public DramaDownloadTaskVO create(Long userId, Long dramaId, CreateDramaDownloadTaskDTO request) {
        List<Long> contentIds = request.getContentIds().stream().distinct().toList();
        if (contentIds.isEmpty() || contentIds.size() > maxEpisodes) {
            throw new BusinessException(ErrorCode.DRAMA_DOWNLOAD_RESOURCE_UNAVAILABLE);
        }
        Map<Long, DramaContentResourceVO> resources = dramaService.getFreeContent(dramaId).stream()
                .collect(Collectors.toMap(DramaContentResourceVO::getId, resource -> resource, (a, b) -> a));
        if (contentIds.stream().anyMatch(id -> {
            DramaContentResourceVO resource = resources.get(id);
            return resource == null || !resource.isFree() || !isHttpUrl(resource.getDownloadUrl());
        })) {
            throw new BusinessException(ErrorCode.DRAMA_DOWNLOAD_RESOURCE_UNAVAILABLE);
        }

        DramaDownloadTask task = new DramaDownloadTask();
        task.setUserId(userId);
        task.setDramaId(dramaId);
        task.setStatus(DramaDownloadTaskStatus.PENDING);
        task.setTotalCount(contentIds.size());
        task.setCompletedCount(0);
        task.setExpiresAt(LocalDateTime.now(clock).plusHours(expireHours));
        try {
            task.setContentIdsJson(objectMapper.writeValueAsString(contentIds));
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        taskMapper.insert(task);
        Long taskId = Objects.requireNonNull(task.getId(), "download task id");
        enqueueAfterCommit(taskId);
        return toVO(task);
    }

    @Override
    @Transactional(readOnly = true)
    public DramaDownloadTaskVO get(Long userId, Long taskId) {
        DramaDownloadTask task = ownedTask(userId, taskId);
        return toVO(task);
    }

    @Override
    @Transactional(readOnly = true)
    public Path getFile(Long userId, Long taskId) {
        DramaDownloadTask task = ownedTask(userId, taskId);
        if (task.getStatus() != DramaDownloadTaskStatus.SUCCESS) {
            throw new BusinessException(ErrorCode.DRAMA_DOWNLOAD_RESOURCE_UNAVAILABLE);
        }
        if (task.getExpiresAt() == null || task.getExpiresAt().isBefore(LocalDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.DRAMA_DOWNLOAD_TASK_EXPIRED);
        }
        Path path = safePath(task.getFilePath());
        if (path == null || !Files.isRegularFile(path)) {
            throw new BusinessException(ErrorCode.DRAMA_DOWNLOAD_TASK_NOT_FOUND);
        }
        return path;
    }

    @Override
    public void process(Long taskId) {
        DramaDownloadTask task = taskMapper.findById(taskId);
        if (task == null || task.getStatus() != DramaDownloadTaskStatus.PENDING) return;
        taskMapper.markRunning(taskId);
        Path workDir = downloadDir.resolve(String.valueOf(task.getUserId())).resolve(String.valueOf(taskId));
        try {
            Files.createDirectories(workDir);
            List<Long> contentIds = readContentIds(task.getContentIdsJson());
            Map<Long, DramaContentResourceVO> resources = dramaService.getFreeContent(task.getDramaId()).stream()
                    .collect(Collectors.toMap(DramaContentResourceVO::getId, resource -> resource, (a, b) -> a));
            Path zipPath = workDir.resolve("drama-" + task.getDramaId() + "-" + taskId + ".zip");
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipPath))) {
                int completed = 0;
                long totalBytes = 0;
                for (Long contentId : contentIds) {
                    DramaContentResourceVO resource = resources.get(contentId);
                    if (resource == null || !resource.isFree() || !isHttpUrl(resource.getDownloadUrl())) {
                        throw new IOException("resource unavailable");
                    }
                    Path part = workDir.resolve("episode-" + resource.getSequenceNo() + ".part");
                    Path media = downloadWithRetry(task.getDramaId(), contentId,
                            resource.getDownloadUrl(), part);
                    totalBytes += Files.size(media);
                    if (totalBytes > MAX_TASK_BYTES) throw new IOException("task exceeds size limit");
                    String filename = "episode-" + resource.getSequenceNo() + extension(media);
                    zip.putNextEntry(new ZipEntry(filename));
                    Files.copy(media, zip);
                    zip.closeEntry();
                    completed++;
                    taskMapper.updateProgress(taskId, completed);
                }
            }
            Path finalPath = downloadDir.resolve(String.valueOf(task.getUserId()))
                    .resolve("drama-" + task.getDramaId() + "-" + taskId + ".zip");
            Files.createDirectories(finalPath.getParent());
            Files.move(zipPath, finalPath, StandardCopyOption.REPLACE_EXISTING);
            taskMapper.markSuccess(taskId, finalPath.toString(), finalPath.getFileName().toString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            taskMapper.markFailed(taskId, "素材下载失败，请稍后重试");
        } catch (Exception exception) {
            taskMapper.markFailed(taskId, "素材下载失败，请稍后重试");
        } finally {
            deleteDirectory(workDir);
        }
    }

    @Override
    @Transactional
    public int cleanupExpired() {
        LocalDateTime now = LocalDateTime.now(clock);
        taskMapper.findExpired(now).forEach(task -> {
            Path file = safePath(task.getFilePath());
            if (file != null) deleteIfExists(file);
        });
        return taskMapper.deleteExpired(now);
    }

    private DramaDownloadTask ownedTask(Long userId, Long taskId) {
        DramaDownloadTask task = taskMapper.findByIdAndUserId(taskId, userId);
        if (task == null) throw new BusinessException(ErrorCode.DRAMA_DOWNLOAD_TASK_NOT_FOUND);
        return task;
    }

    private DramaDownloadTaskVO toVO(DramaDownloadTask task) {
        return DramaDownloadTaskVO.builder().taskId(task.getId()).status(task.getStatus())
                .totalCount(task.getTotalCount()).completedCount(task.getCompletedCount())
                .downloadUrl(task.getStatus() == DramaDownloadTaskStatus.SUCCESS
                        ? "/api/user/promotion/downloads/" + task.getId() + "/file" : null)
                .errorMessage(task.getErrorMessage()).expiresAt(task.getExpiresAt()).build();
    }

    private List<Long> readContentIds(String json) throws IOException {
        try {
            long[] values = objectMapper.readValue(json, long[].class);
            return Arrays.stream(values).boxed().toList();
        } catch (JacksonException exception) {
            throw new IOException("invalid task content ids", exception);
        }
    }

    private boolean isHttpUrl(String value) {
        return value != null && (value.startsWith("https://") || value.startsWith("http://"));
    }

    private Path downloadWithRetry(Long dramaId, Long contentId, String initialUrl, Path target)
            throws IOException, InterruptedException {
        String url = initialUrl;
        boolean refreshed = false;
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return mediaDownloader.download(url, target, ffmpegPath);
            } catch (DramaMediaExpiredException exception) {
                if (refreshed) throw exception;
                refreshed = true;
                DramaContentResourceVO fresh = dramaService.getFreeContent(dramaId, true).stream()
                        .filter(resource -> contentId.equals(resource.getId()))
                        .findFirst().orElse(null);
                if (fresh == null || !fresh.isFree() || !isHttpUrl(fresh.getDownloadUrl())) {
                    throw exception;
                }
                url = fresh.getDownloadUrl();
            } catch (IOException exception) {
                lastFailure = exception;
            }
        }
        throw Objects.requireNonNull(lastFailure);
    }

    private void enqueueAfterCommit(Long taskId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            taskExecutor.execute(() -> process(taskId));
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                taskExecutor.execute(() -> process(taskId));
            }
        });
    }

    private String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : ".mp4";
    }

    private Path safePath(String raw) {
        if (raw == null || raw.isBlank()) return null;
        Path path = Path.of(raw).toAbsolutePath().normalize();
        return path.startsWith(downloadDir) ? path : null;
    }

    private void deleteDirectory(Path directory) {
        try {
            if (!Files.exists(directory)) return;
            try (var paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(this::deleteIfExists);
            }
        } catch (IOException ignored) {
            // Cleanup failure must not change the persisted task result.
        }
    }

    private void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best effort cleanup.
        }
    }
}
