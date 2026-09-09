package com.kasi.backend.promotion.service;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.promotion.service.impl.PromotionProjectCoverStorageServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("推广项目封面文件存储")
class PromotionProjectCoverStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsEmptyOversizedAndForgedFiles() {
        PromotionProjectCoverStorageService service = service();

        assertThatThrownBy(() -> service.store(new MockMultipartFile(
                "coverFile", "empty.png", "image/png", new byte[0])))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode())
                                .isEqualTo(ErrorCode.PROMOTION_PROJECT_IMAGE_INVALID.getCode()));
        assertThatThrownBy(() -> service.store(new MockMultipartFile(
                "coverFile", "large.png", "image/png", new byte[2 * 1024 * 1024 + 1])))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode())
                                .isEqualTo(ErrorCode.PROMOTION_PROJECT_IMAGE_TOO_LARGE.getCode()));
        assertThatThrownBy(() -> service.store(new MockMultipartFile(
                "coverFile", "fake.png", "image/png", "not-an-image".getBytes())))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode())
                                .isEqualTo(ErrorCode.PROMOTION_PROJECT_IMAGE_INVALID.getCode()));
    }

    @Test
    void storesSupportedImagesWithGeneratedNames() throws Exception {
        PromotionProjectCoverStorageService service = service();

        String pngUrl = service.store(new MockMultipartFile(
                "coverFile", "../../client.png", "text/plain", pngBytes()));
        String webpUrl = service.store(new MockMultipartFile(
                "coverFile", "cover.webp", "application/octet-stream", webpBytes()));

        assertThat(pngUrl).matches("/uploads/promotion-projects/[0-9a-f-]{36}\\.png");
        assertThat(webpUrl).matches("/uploads/promotion-projects/[0-9a-f-]{36}\\.webp");
        assertThat(Files.exists(resolve(pngUrl))).isTrue();
        assertThat(Files.exists(resolve(webpUrl))).isTrue();
    }

    @Test
    void onlyDeletesFilesInsideProjectCoverDirectory() throws Exception {
        PromotionProjectCoverStorageService service = service();
        String coverUrl = service.store(new MockMultipartFile(
                "coverFile", "cover.png", "image/png", pngBytes()));
        Path outside = tempDir.resolve("outside.png");
        Files.write(outside, new byte[]{1});

        service.deleteIfLocal("https://example.com/cover.png");
        service.deleteIfLocal("/uploads/promotion-projects/../outside.png");
        service.deleteIfLocal(coverUrl);

        assertThat(Files.exists(resolve(coverUrl))).isFalse();
        assertThat(Files.exists(outside)).isTrue();
    }

    private PromotionProjectCoverStorageService service() {
        return new PromotionProjectCoverStorageServiceImpl(tempDir.toString());
    }

    private Path resolve(String coverUrl) {
        return tempDir.resolve("promotion-projects")
                .resolve(coverUrl.substring(coverUrl.lastIndexOf('/') + 1));
    }

    private byte[] pngBytes() {
        return Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
    }

    private byte[] webpBytes() {
        return new byte[]{
                'R', 'I', 'F', 'F', 12, 0, 0, 0,
                'W', 'E', 'B', 'P', 'V', 'P', '8', 'L',
                0, 0, 0, 0
        };
    }
}
