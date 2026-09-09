package com.kasi.backend.promotion.service.impl;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.promotion.service.PromotionProjectCoverStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class PromotionProjectCoverStorageServiceImpl implements PromotionProjectCoverStorageService {
    static final long MAX_FILE_SIZE = 2L * 1024 * 1024;
    private static final String PUBLIC_PREFIX = "/uploads/promotion-projects/";

    private final Path coverDirectory;

    public PromotionProjectCoverStorageServiceImpl(
            @Value("${app.upload.dir:./data/uploads}") String uploadDirectory) {
        this.coverDirectory = Path.of(uploadDirectory)
                .toAbsolutePath()
                .normalize()
                .resolve("promotion-projects");
    }

    @Override
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PROMOTION_PROJECT_IMAGE_INVALID);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.PROMOTION_PROJECT_IMAGE_TOO_LARGE);
        }

        String extension = detectExtension(file);
        String fileName = UUID.randomUUID() + "." + extension;
        Path target = coverDirectory.resolve(fileName);
        try {
            Files.createDirectories(coverDirectory);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return PUBLIC_PREFIX + fileName;
        } catch (IOException exception) {
            deleteQuietly(target);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "项目封面保存失败");
        }
    }

    @Override
    public void deleteIfLocal(String coverImageUrl) {
        if (coverImageUrl == null || !coverImageUrl.startsWith(PUBLIC_PREFIX)) {
            return;
        }
        String fileName = coverImageUrl.substring(PUBLIC_PREFIX.length());
        if (fileName.isBlank() || fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            return;
        }
        Path target = coverDirectory.resolve(fileName).normalize();
        if (!target.getParent().equals(coverDirectory)) {
            return;
        }
        deleteQuietly(target);
    }

    private String detectExtension(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(16);
            if (isPng(header) && canDecode(file)) {
                return "png";
            }
            if (isJpeg(header) && canDecode(file)) {
                return "jpg";
            }
            if (isWebp(header, file.getSize())) {
                return "webp";
            }
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.PROMOTION_PROJECT_IMAGE_INVALID);
        }
        throw new BusinessException(ErrorCode.PROMOTION_PROJECT_IMAGE_INVALID);
    }

    private boolean isPng(byte[] header) {
        byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        if (header.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (header[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean isJpeg(byte[] header) {
        return header.length >= 3
                && header[0] == (byte) 0xff
                && header[1] == (byte) 0xd8
                && header[2] == (byte) 0xff;
    }

    private boolean isWebp(byte[] header, long fileSize) {
        if (header.length < 16 || fileSize < 20) {
            return false;
        }
        boolean container = header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
        boolean imageChunk = header[12] == 'V' && header[13] == 'P' && header[14] == '8'
                && (header[15] == ' ' || header[15] == 'L' || header[15] == 'X');
        long declaredSize = (header[4] & 0xffL)
                | ((header[5] & 0xffL) << 8)
                | ((header[6] & 0xffL) << 16)
                | ((header[7] & 0xffL) << 24);
        return container && imageChunk && declaredSize + 8 == fileSize;
    }

    private boolean canDecode(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            return ImageIO.read(input) != null;
        } catch (IOException exception) {
            return false;
        }
    }

    private void deleteQuietly(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // File cleanup must not mask a completed database operation.
        }
    }
}
