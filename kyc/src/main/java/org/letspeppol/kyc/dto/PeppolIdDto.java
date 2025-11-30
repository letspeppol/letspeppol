package org.letspeppol.kyc.dto;

public record PeppolIdDto(
        String scheme,
        String value
) {

    public static PeppolIdDto parse(String s) {
        if (s == null) return new PeppolIdDto(null, null);
        s = s.trim();
        int i = s.indexOf(':');
        if (i < 0) return new PeppolIdDto(null, s.isEmpty() ? null : s);
        String scheme = s.substring(0, i).trim();
        String value  = s.substring(i + 1).trim();
        return new PeppolIdDto(scheme.isEmpty() ? null : scheme, value.isEmpty() ? null : value);
    }

    public static String format(PeppolIdDto id) {
        if (id == null) return null;
        return (id.scheme() == null || id.scheme().isBlank())
                ? id.value()
                : id.scheme() + ":" + (id.value() == null ? "" : id.value());
    }

}
