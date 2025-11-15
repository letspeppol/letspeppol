package org.letspeppol.kyc.util;

public class CompanyNumberUtil {

    public static String normalizeVat(String input) {
        if (input == null) throw new IllegalArgumentException("VAT number required");
        String digits = input.replaceAll("[^0-9]", "");
        if (digits.length() != 10) {
            throw new IllegalArgumentException("Belgian enterprise number must have 10 digits after normalization");
        }
        return digits;
    }
}
