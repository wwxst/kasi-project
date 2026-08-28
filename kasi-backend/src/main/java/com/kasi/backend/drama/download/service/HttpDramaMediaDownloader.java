package com.kasi.backend.drama.download.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class HttpDramaMediaDownloader implements DramaMediaDownloader {
    private static final long MAX_EPISODE_BYTES = 2L * 1024 * 1024 * 1024;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    public HttpDramaMediaDownloader(
            @Value("${app.drama.download.connect-timeout:10s}") Duration connectTimeout,
            @Value("${app.drama.download.read-timeout:30s}") Duration readTimeout) {
        this.connectTimeoutMillis = Math.toIntExact(connectTimeout.toMillis());
        this.readTimeoutMillis = Math.toIntExact(readTimeout.toMillis());
    }

    @Override
    public Path download(String url, Path target, String ffmpegPath) throws IOException, InterruptedException {
        if (url.toLowerCase(java.util.Locale.ROOT).contains(".m3u8")) {
            verifyHlsManifest(url);
            Path output = target.resolveSibling(target.getFileName().toString().replaceFirst("\\.part$", ".mp4"));
            Process process = new ProcessBuilder(ffmpegPath, "-y", "-i", url, "-c", "copy", output.toString())
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(30, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IOException("ffmpeg timed out");
            }
            if (process.exitValue() != 0) throw new IOException("ffmpeg failed");
            if (!Files.isRegularFile(output)) throw new IOException("ffmpeg output missing");
            if (Files.size(output) > MAX_EPISODE_BYTES) {
                Files.deleteIfExists(output);
                throw new IOException("episode exceeds size limit");
            }
            return output;
        }
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(connectTimeoutMillis);
        connection.setReadTimeout(readTimeoutMillis);
        connection.setInstanceFollowRedirects(true);
        Path output = target.resolveSibling(directFilename(target, url));
        try {
            int status = connection.getResponseCode();
            if (status == 403 || status == 404) throw new DramaMediaExpiredException();
            if (status < 200 || status >= 300) throw new IOException("remote media rejected");
            Files.createDirectories(target.getParent());
            try (InputStream input = connection.getInputStream(); var stream = Files.newOutputStream(output)) {
                byte[] buffer = new byte[8192];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    total += count;
                    if (total > MAX_EPISODE_BYTES) throw new IOException("episode exceeds size limit");
                    stream.write(buffer, 0, count);
                }
            } catch (IOException exception) {
                Files.deleteIfExists(output);
                throw exception;
            }
            return output;
        } finally {
            connection.disconnect();
        }
    }

    private void verifyHlsManifest(String url) throws IOException, InterruptedException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(connectTimeoutMillis);
        connection.setReadTimeout(readTimeoutMillis);
        connection.setInstanceFollowRedirects(true);
        try {
            int status = connection.getResponseCode();
            if (status == 403 || status == 404) throw new DramaMediaExpiredException();
            if (status < 200 || status >= 300) throw new IOException("remote HLS manifest rejected");
        } finally {
            connection.disconnect();
        }
    }

    private String directFilename(Path target, String url) {
        String path = URI.create(url).getPath();
        int dot = path == null ? -1 : path.lastIndexOf('.');
        String extension = dot >= 0 && path.length() - dot <= 8 ? path.substring(dot) : ".mp4";
        return target.getFileName().toString().replaceFirst("\\.part$", extension);
    }
}
