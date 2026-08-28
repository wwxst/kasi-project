package com.kasi.backend.drama.download.service;

import java.io.IOException;
import java.nio.file.Path;

public interface DramaMediaDownloader {
    Path download(String url, Path target, String ffmpegPath) throws IOException, InterruptedException;
}
