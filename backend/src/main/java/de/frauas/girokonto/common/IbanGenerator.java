package de.frauas.girokonto.common;

import java.math.BigInteger;
import org.springframework.stereotype.Component;

/**
 * Erzeugt und prüft deutsche IBANs nach ISO 13616 (Pflichtenheft Kap. 8.1).
 * Aufbau: DE + 2 Prüfziffern + 8-stellige BLZ + 10-stellige Kontonummer = 22 Zeichen.
 */
@Component
public class IbanGenerator {

    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

    public String generate(String blz, long accountNumber) {
        if (blz == null || !blz.matches("\\d{8}")) {
            throw new IllegalArgumentException("BLZ muss genau 8 Ziffern haben");
        }
        if (accountNumber < 0 || accountNumber > 9_999_999_999L) {
            throw new IllegalArgumentException("Kontonummer muss 0 bis 10 Ziffern haben");
        }
        String bban = blz + String.format("%010d", accountNumber);
        // "DE00" ans Ende, Buchstaben in Zahlen umwandeln: D = 13, E = 14
        int check = 98 - new BigInteger(bban + "131400").mod(NINETY_SEVEN).intValue();
        return "DE" + String.format("%02d", check) + bban;
    }

    public boolean isValid(String iban) {
        if (iban == null) {
            return false;
        }
        String s = iban.replace(" ", "").toUpperCase();
        if (!s.matches("[A-Z]{2}\\d{2}[A-Z0-9]{1,30}")) {
            return false;
        }
        String rearranged = s.substring(4) + s.substring(0, 4);
        StringBuilder digits = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            if (Character.isLetter(c)) {
                digits.append(c - 'A' + 10);
            } else {
                digits.append(c);
            }
        }
        return new BigInteger(digits.toString()).mod(NINETY_SEVEN).intValue() == 1;
    }
}
