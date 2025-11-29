package org.letspeppol.kyc.util;

import java.text.Normalizer;
import java.util.regex.Pattern;

public class NameMatchUtil {
    private static final Pattern WS = Pattern.compile("\\s+");

    /** Returns true if fullName contains the surname (substring match)
     *  AND at least one given-name token appears as a whole word. */
    public static boolean matches(String givenName, String surName, String fullName) {
        String full = norm(fullName);
        if (full.isEmpty()) return false;

        boolean surnameOk = false;
        if (surName != null && !surName.isBlank()) {
            String sur = norm(surName);
            if (!sur.isEmpty()) {
                // substring to allow particles/compounds: "van der Meer" etc.
                surnameOk = full.contains(sur);
            }
        }

        boolean givenOk = false;
        if (givenName != null && !givenName.isBlank()) {
            String[] tokens = norm(givenName).split("[\\s\\-]+"); // split on spaces and hyphens
            for (String t : tokens) {
                if (t.length() < 2) continue;                       // skip tiny noise tokens
                if (containsWholeWord(full, t)) { givenOk = true; break; }
            }
        }

        return surnameOk && givenOk;
    }

    // normalize: lowercase, strip diacritics, trim, collapse spaces
    private static String norm(String s) {
        if (s == null)
            return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "") // remove accents
                .toLowerCase();
        return WS.matcher(n.trim()).replaceAll(" ");
    }

    private static boolean containsWholeWord(String haystack, String token) {
        return Pattern.compile("(?<!\\p{L})" + Pattern.quote(token) + "(?!\\p{L})").matcher(haystack).find();
    }
}
