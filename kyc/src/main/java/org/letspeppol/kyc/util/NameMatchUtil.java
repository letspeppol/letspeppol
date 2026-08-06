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
            // Require every significant surname token (>=2 chars) to appear as a WHOLE WORD.
            // This still allows compounds/particles like "van der Meer" (each token whole-word)
            // but stops substring false-positives (e.g. surname "Li" matching "Charlie").
            String[] surTokens = norm(surName).split("[\\s\\-]+");
            boolean anySignificant = false;
            surnameOk = true;
            for (String t : surTokens) {
                if (t.length() < 2) continue;
                anySignificant = true;
                if (!containsWholeWord(full, t)) { surnameOk = false; break; }
            }
            if (!anySignificant) surnameOk = false;
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
