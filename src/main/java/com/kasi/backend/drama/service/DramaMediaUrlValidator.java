package com.kasi.backend.drama.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Component
public class DramaMediaUrlValidator {
    private final List<String> allowedHosts;

    public DramaMediaUrlValidator(@Value("${app.goodshort.media-hosts:}") String allowedHosts) {
        this.allowedHosts = Arrays.stream(allowedHosts.split(","))
                .map(String::trim)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(value -> !value.isEmpty())
                .toList();
    }

    public boolean isAllowed(String value) {
        if (value == null || value.isBlank() || allowedHosts.isEmpty()) return false;
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();
            if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) return false;
            if (host == null || uri.getUserInfo() != null) return false;
            if (port != -1 && port != 80 && port != 443) return false;
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            if (isUnsafeLiteral(normalizedHost)) return false;
            return allowedHosts.stream().anyMatch(allowed -> normalizedHost.equals(allowed)
                    || normalizedHost.endsWith("." + allowed));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isUnsafeLiteral(String host) {
        if ("localhost".equals(host) || host.endsWith(".localhost")) return true;
        if (!host.matches("[0-9.]+") && !host.contains(":")) return false;
        try {
            InetAddress address = InetAddress.getByName(host);
            return address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                    || address.isMulticastAddress();
        } catch (Exception exception) {
            return true;
        }
    }
}
