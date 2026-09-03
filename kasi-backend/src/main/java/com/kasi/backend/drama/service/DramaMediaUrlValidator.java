package com.kasi.backend.drama.service;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;

@Component
public class DramaMediaUrlValidator {
    public boolean isAllowed(String value, String mediaRootDomain) {
        if (value == null || value.isBlank() || mediaRootDomain == null || mediaRootDomain.isBlank()) return false;
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
            String normalizedRoot = mediaRootDomain.trim().toLowerCase(Locale.ROOT);
            return normalizedHost.equals(normalizedRoot)
                    || normalizedHost.endsWith("." + normalizedRoot);
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
