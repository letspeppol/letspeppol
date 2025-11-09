package io.tubs.kyc.util;

import java.util.Locale;

public final class LocaleUtil {
    private LocaleUtil() {}

    // Extract primary language (and optional country) from Accept-Language header, e.g. "nl-BE,nl;q=0.9,en;q=0.8"
    public static String extractLanguageTag(String acceptLanguageHeader) {
        if (acceptLanguageHeader == null || acceptLanguageHeader.isBlank()) return null;
        String first = acceptLanguageHeader.split(",", 2)[0].trim();
        // Remove any ;q= parts
        int sc = first.indexOf(';');
        if (sc >= 0) first = first.substring(0, sc).trim();
        if (first.isEmpty()) return null;
        // Normalize: language lower, country upper
        String[] parts = first.replace('_','-').split("-", 3);
        if (parts.length == 1) return parts[0].toLowerCase(Locale.ROOT);
        return parts[0].toLowerCase(Locale.ROOT) + "-" + parts[1].toUpperCase(Locale.ROOT);
    }
}

