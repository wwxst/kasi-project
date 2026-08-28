package com.kasi.backend.drama.download.service;

import java.io.IOException;

public class DramaMediaExpiredException extends IOException {
    public DramaMediaExpiredException() {
        super("remote media URL expired");
    }
}
