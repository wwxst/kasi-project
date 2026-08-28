package com.kasi.backend.drama.download.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class HttpDramaMediaDownloaderTest {
    @TempDir Path tempDir;
    private HttpServer server;
    private String baseUrl;
    private final HttpDramaMediaDownloader downloader = new HttpDramaMediaDownloader(
            Duration.ofSeconds(1), Duration.ofMillis(100));

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/video.mp4", exchange -> {
            byte[] body = "video".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/expired.m3u8", exchange -> {
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
        });
        server.createContext("/slow.mp4", exchange -> {
            exchange.sendResponseHeaders(200, 5);
            try {
                Thread.sleep(500);
                exchange.getResponseBody().write("video".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    @DisplayName("普通视频以流方式保存到临时文件")
    void downloadsDirectMediaStream() throws Exception {
        Path result = downloader.download(baseUrl + "/video.mp4", tempDir.resolve("episode.part"), "ffmpeg");

        assertThat(Files.readString(result)).isEqualTo("video");
    }

    @Test
    @DisplayName("HLS清单返回403时报告资源地址已失效")
    void reportsExpiredHlsManifestBeforeFfmpeg() {
        assertThatThrownBy(() -> downloader.download(
                baseUrl + "/expired.m3u8", tempDir.resolve("episode.part"), "missing-ffmpeg"))
                .isInstanceOf(DramaMediaExpiredException.class);
    }

    @Test
    @DisplayName("响应体长时间无数据时触发读取超时")
    void timesOutWhenResponseBodyStalls() {
        assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
                assertThatThrownBy(() -> downloader.download(
                        baseUrl + "/slow.mp4", tempDir.resolve("slow.part"), "ffmpeg"))
                        .isInstanceOf(SocketTimeoutException.class));
    }
}
