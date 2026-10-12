package de.frauas.girokonto.auth;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Zeitbasierte Einmal-Codes (TOTP, RFC 6238) wie in Google Authenticator, Microsoft Authenticator usw.:
 * HMAC-SHA1 über die Nummer des 30-Sekunden-Abschnitts, daraus 6 Ziffern (RFC 4226, "dynamic truncation").
 */
public final class Totp {

    public static final int DIGITS = 6;
    public static final int PERIOD_SECONDS = 30;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Totp() {
    }

    /** Neuer zufälliger Schlüssel: 20 Byte = 160 Bit, als Base32 (32 Zeichen). */
    public static String newSecret(SecureRandom random) {
        byte[] key = new byte[20];
        random.nextBytes(key);
        return base32Encode(key);
    }

    public static long step(Instant time) {
        return Math.floorDiv(time.getEpochSecond(), PERIOD_SECONDS);
    }

    /** Code für einen Zeitabschnitt. */
    public static String code(byte[] key, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            int otp = binary % (int) Math.pow(10, DIGITS);
            return String.format("%0" + DIGITS + "d", otp);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Prüft einen Code und erlaubt ±1 Abschnitt Abweichung (Uhren gehen nicht genau gleich).
     *
     * @param lastUsedStep zuletzt erfolgreich benutzter Abschnitt (oder null) – derselbe Code gilt nur einmal
     * @return der passende Abschnitt oder -1
     */
    public static long verify(String secretBase32, String code, Instant now, Long lastUsedStep) {
        if (code == null) {
            return -1;
        }
        String c = code.replaceAll("\\s", "");
        if (!c.matches("\\d{" + DIGITS + "}")) {
            return -1;
        }
        byte[] key = base32Decode(secretBase32);
        long current = step(now);
        for (long s = current - 1; s <= current + 1; s++) {
            if (lastUsedStep != null && s <= lastUsedStep) {
                continue;
            }
            if (constantTimeEquals(code(key, s), c)) {
                return s;
            }
        }
        return -1;
    }

    /** Link für die Authenticator-App (wird als QR-Code angezeigt). */
    public static String otpauthUri(String issuer, String account, String secretBase32) {
        String label = urlEncode(issuer + ":" + account);
        return "otpauth://totp/" + label + "?secret=" + secretBase32 + "&issuer=" + urlEncode(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + PERIOD_SECONDS;
    }

    static String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return sb.toString();
    }

    public static byte[] base32Decode(String s) {
        String clean = s.replaceAll("[\\s=-]", "").toUpperCase();
        ByteBuffer out = ByteBuffer.allocate(clean.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char ch : clean.toCharArray()) {
            int v = BASE32.indexOf(ch);
            if (v < 0) {
                throw new IllegalArgumentException("Ungültiges Base32-Zeichen");
            }
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) ((buffer >> (bits - 8)) & 0xff));
                bits -= 8;
            }
        }
        return java.util.Arrays.copyOf(out.array(), out.position());
    }

    private static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(a.getBytes(), b.getBytes());
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }
}
