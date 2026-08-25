package com.kasi.backend.admin.service;

import com.kasi.backend.admin.service.impl.AdminAvatarStorageServiceImpl;
import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("管理员头像文件存储")
class AdminAvatarStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("空文件和超过2MB的文件被拒绝")
    void storeRejectsEmptyAndOversizedFiles() {
        AdminAvatarStorageService service = service();

        assertThatThrownBy(() -> service.store(new MockMultipartFile(
                "file", "empty.png", "image/png", new byte[0])))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo(ErrorCode.ADMIN_AVATAR_INVALID.getCode()));

        assertThatThrownBy(() -> service.store(new MockMultipartFile(
                "file", "large.png", "image/png", new byte[2 * 1024 * 1024 + 1])))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo(ErrorCode.ADMIN_AVATAR_TOO_LARGE.getCode()));
    }

    @Test
    @DisplayName("文件内容不是支持的图片时被拒绝")
    void storeRejectsUnsupportedContent() {
        AdminAvatarStorageService service = service();

        assertThatThrownBy(() -> service.store(new MockMultipartFile(
                "file", "fake.png", "image/png", "not-an-image".getBytes())))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo(ErrorCode.ADMIN_AVATAR_INVALID.getCode()));
        assertThatThrownBy(() -> service.store(new MockMultipartFile(
                "file", "truncated.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a})))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo(ErrorCode.ADMIN_AVATAR_INVALID.getCode()));
    }

    @Test
    @DisplayName("合法PNG和WebP使用服务端文件名保存")
    void storeSupportedImagesWithGeneratedNames() throws Exception {
        AdminAvatarStorageService service = service();

        String pngUrl = service.store(new MockMultipartFile(
                "file", "../../client-name.png", "text/plain",
                pngBytes()));
        String webpUrl = service.store(new MockMultipartFile(
                "file", "avatar.webp", "application/octet-stream",
                webpBytes()));

        assertThat(pngUrl).matches("/uploads/admin-avatars/[0-9a-f-]{36}\\.png");
        assertThat(webpUrl).matches("/uploads/admin-avatars/[0-9a-f-]{36}\\.webp");
        assertThat(Files.exists(resolve(pngUrl))).isTrue();
        assertThat(Files.exists(resolve(webpUrl))).isTrue();
    }

    @Test
    @DisplayName("只删除管理员头像目录内的本地文件")
    void deleteIfLocalDoesNotDeleteExternalPaths() throws Exception {
        AdminAvatarStorageService service = service();
        String avatarUrl = service.store(new MockMultipartFile(
                "file", "avatar.png", "image/png",
                pngBytes()));
        Path outside = tempDir.resolve("outside.png");
        Files.write(outside, new byte[]{1});

        service.deleteIfLocal("https://example.com/avatar.png");
        service.deleteIfLocal("/uploads/admin-avatars/../outside.png");
        service.deleteIfLocal(avatarUrl);

        assertThat(Files.exists(resolve(avatarUrl))).isFalse();
        assertThat(Files.exists(outside)).isTrue();
    }

    private AdminAvatarStorageService service() {
        return new AdminAvatarStorageServiceImpl(tempDir.toString());
    }

    private Path resolve(String avatarUrl) {
        return tempDir.resolve("admin-avatars").resolve(avatarUrl.substring(avatarUrl.lastIndexOf('/') + 1));
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
