package de.frauas.girokonto.card;

import java.security.SecureRandom;

/**
 * Erzeugt 16-stellige Kartennummern mit gültiger Luhn-Prüfziffer.
 * Präfix 999999 ist keiner echten Bank zugeordnet.
 */
public final class CardNumberGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private CardNumberGenerator() {
    }

    public static String generate(String bin) {
        StringBuilder sb = new StringBuilder(bin);
        while (sb.length() < 15) {
            sb.append(RANDOM.nextInt(10));
        }
        sb.append(luhnCheckDigit(sb.toString()));
        return sb.toString();
    }

    public static String randomCvc() {
        return String.format("%03d", RANDOM.nextInt(1000));
    }

    /** Prüfziffer für die Ziffern ohne Prüfziffer. */
    static int luhnCheckDigit(String withoutCheck) {
        int sum = 0;
        boolean dbl = true; // von rechts: die letzte vorhandene Ziffer wird verdoppelt
        for (int i = withoutCheck.length() - 1; i >= 0; i--) {
            int d = withoutCheck.charAt(i) - '0';
            if (dbl) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            dbl = !dbl;
        }
        return (10 - sum % 10) % 10;
    }

    public static boolean isValid(String number) {
        if (number == null || !number.matches("\\d{12,19}")) {
            return false;
        }
        int check = number.charAt(number.length() - 1) - '0';
        return luhnCheckDigit(number.substring(0, number.length() - 1)) == check;
    }
}
