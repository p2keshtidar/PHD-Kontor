package de.frauas.girokonto.contract;

import de.frauas.girokonto.audit.AuditLog;
import de.frauas.girokonto.card.CardCrypto;
import de.frauas.girokonto.common.ApiException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dokumente zu Verträgen (Rechnungen, Vertragsunterlagen): nur PDF, PNG oder JPEG bis 5 MB, Typ wird am Inhalt
 * geprüft (nicht an der Endung). Der Inhalt wird AES-256-GCM-verschlüsselt gespeichert, nur der Besitzer kann ihn laden.
 * Eine Virenprüfung gibt es nicht – deshalb nur diese drei Formate und Herunterladen immer als Anhang.
 */
@Service
public class DocumentService {

    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public static final int MAX_DOCUMENTS = 50;

    public record DocumentDto(Long id, Long contractId, String title, String fileName, String contentType,
                              int sizeBytes, Instant createdAt) {}

    public record Content(String fileName, String contentType, byte[] bytes) {}

    private final JdbcTemplate jdbc;
    private final CardCrypto crypto;
    private final ContractService contracts;
    private final Clock clock;

    private final AuditLog audit;

    public DocumentService(JdbcTemplate jdbc, CardCrypto crypto, ContractService contracts, Clock clock, AuditLog audit) {
        this.audit = audit;
        this.jdbc = jdbc;
        this.crypto = crypto;
        this.contracts = contracts;
        this.clock = clock;
    }

    /** Erkennt den Typ an den ersten Bytes. */
    static String detect(byte[] b) {
        if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') {
            return "application/pdf";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "image/png";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        return null;
    }

    /** Dateiname ohne Pfad und Sonderzeichen, Endung passend zum erkannten Typ. */
    static String safeName(String original, String type) {
        String ext = switch (type) {
            case "application/pdf" -> ".pdf";
            case "image/png" -> ".png";
            default -> ".jpg";
        };
        String n = original == null ? "" : original;
        n = n.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1);
        int dot = n.lastIndexOf('.');
        if (dot > 0) {
            n = n.substring(0, dot);
        }
        n = n.replaceAll("[^A-Za-z0-9ÄÖÜäöüß _.-]", "_").replaceAll("^[ ._]+", "").trim();
        if (n.isEmpty()) {
            n = "dokument";
        }
        if (n.length() > 100) {
            n = n.substring(0, 100);
        }
        return n + ext;
    }

    @Transactional
    public DocumentDto upload(Long userId, String originalName, byte[] bytes, String title, Long contractId) {
        if (bytes == null || bytes.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "Die Datei ist leer.");
        }
        if (bytes.length > MAX_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE", "Die Datei ist größer als 5 MB.");
        }
        String type = detect(bytes);
        if (type == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_FILE", "Nur PDF, PNG oder JPEG sind erlaubt.");
        }
        if (contractId != null) {
            contracts.get(userId, contractId);        // 404, wenn nicht der eigene Vertrag
        }
        Integer count = jdbc.queryForObject("SELECT count(*) FROM document WHERE user_id = ?", Integer.class, userId);
        if (count != null && count >= MAX_DOCUMENTS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_DOCUMENTS", "Höchstens " + MAX_DOCUMENTS + " Dokumente.");
        }
        String name = safeName(originalName, type);
        String t = title == null || title.isBlank() ? name : title.trim();
        if (t.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TITLE", "Der Titel darf höchstens 100 Zeichen haben.");
        }
        String sha = sha256(bytes);
        byte[] encrypted = crypto.encryptBytes(bytes);
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(c -> {
            var ps = c.prepareStatement("INSERT INTO document (user_id, contract_id, title, file_name, content_type, "
                    + "size_bytes, sha256, content, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", new String[] {"id"});
            ps.setLong(1, userId);
            ps.setObject(2, contractId);
            ps.setString(3, t);
            ps.setString(4, name);
            ps.setString(5, type);
            ps.setInt(6, bytes.length);
            ps.setString(7, sha);
            ps.setBytes(8, encrypted);
            ps.setTimestamp(9, Timestamp.from(Instant.now(clock)));
            return ps;
        }, kh);
        audit.record(userId, "DOCUMENT_UPLOADED", "document:" + kh.getKey().longValue(), type);   // ohne Dateiname/Inhalt
        return list(userId, null).stream().filter(d -> d.id() == kh.getKey().longValue()).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<DocumentDto> list(Long userId, Long contractId) {
        String sql = "SELECT id, contract_id, title, file_name, content_type, size_bytes, created_at FROM document "
                + "WHERE user_id = ?" + (contractId == null ? "" : " AND contract_id = ?") + " ORDER BY created_at DESC, id DESC";
        Object[] args = contractId == null ? new Object[] {userId} : new Object[] {userId, contractId};
        return jdbc.query(sql, (rs, n) -> new DocumentDto(rs.getLong(1), (Long) rs.getObject(2, Long.class),
                rs.getString(3), rs.getString(4), rs.getString(5), rs.getInt(6), rs.getTimestamp(7).toInstant()), args);
    }

    @Transactional(readOnly = true)
    public Content content(Long userId, Long id) {
        List<Content> l = jdbc.query("SELECT file_name, content_type, content, sha256 FROM document WHERE id = ? AND user_id = ?",
                (rs, n) -> {
                    byte[] plain = crypto.decryptBytes(rs.getBytes(3));
                    if (!sha256(plain).equals(rs.getString(4))) {
                        throw new IllegalStateException("Prüfsumme des Dokuments stimmt nicht");
                    }
                    return new Content(rs.getString(1), rs.getString(2), plain);
                }, id, userId);
        if (l.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Dokument nicht gefunden.");
        }
        audit.record(userId, "DOCUMENT_DOWNLOADED", "document:" + id, null);
        return l.get(0);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        if (jdbc.update("DELETE FROM document WHERE id = ? AND user_id = ?", id, userId) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Dokument nicht gefunden.");
        }
        audit.record(userId, "DOCUMENT_DELETED", "document:" + id, null);
    }

    static String sha256(byte[] b) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
