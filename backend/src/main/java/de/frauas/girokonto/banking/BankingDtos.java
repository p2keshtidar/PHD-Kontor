package de.frauas.girokonto.banking;

import de.frauas.girokonto.insights.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Ein- und Ausgaben der Schnittstelle für verbundene Bankkonten. */
public final class BankingDtos {

    private BankingDtos() {
    }

    /** @param description kurze Erklärung (z. B. Testdaten / echte Bank über PSD2) */
    public record ProviderDto(String id, String name, boolean sandbox, boolean available, String description) {}

    public record InstitutionDto(String id, String name, String bic, int historyDays, int accessDays, String logo,
                                 boolean sandbox) {}

    public record ConnectRequest(@NotBlank @Size(max = 30) String provider,
                                 @NotBlank @Size(max = 100) String institutionId) {}

    /**
     * @param authorizationUrl Seite der Bank, auf der der Nutzer die Freigabe erteilt (bei der Sandbox: null,
     *                         die App zeigt stattdessen einen gekennzeichneten Test-Dialog)
     */
    public record ConnectResult(ConnectionDto connection, String authorizationUrl, boolean sandbox,
                                String nextStepText) {}

    /** @param approve nur Sandbox: simulierte Entscheidung bei der Bank */
    public record CompleteRequest(Boolean approve) {}

    public record CompleteResult(ConnectionDto connection, SyncResult sync, String resultMessage) {}

    public record ConnectionDto(Long id, String provider, boolean sandbox, String institutionId, String bankName,
                                String status, String statusLabel, List<String> permissions,
                                Instant consentExpiresAt, Integer daysLeft, Instant lastSyncAt, String lastSyncStatus,
                                String lastErrorMessage, Instant createdAt, Instant revokedAt,
                                List<AccountDto> accounts) {}

    public record AccountDto(Long id, Long connectionId, String bankName, String name, String ibanMasked,
                             String currency, Long balanceCents, String balanceType, Instant balanceAt,
                             Instant lastSyncAt, String lastSyncStatus, String connectionStatus, boolean sandbox) {}

    /**
     * Alle verbundenen Konten in einer Übersicht.
     *
     * @param totalBalanceCents Summe der Salden in Euro (Konten in anderen Währungen nicht enthalten)
     */
    public record AccountsOverview(List<AccountDto> accounts, long totalBalanceCents, long monthIncomeCents,
                                   long monthExpenseCents, boolean anySandbox, boolean anyStale) {}

    /** @param status OK, PARTIAL, FAILED, EXPIRED */
    public record SyncResult(Long connectionId, String status, int accounts, int newTransactions,
                             int updatedTransactions, String resultMessage, Instant syncedAt) {}

    public record RevokeResult(Long connectionId, boolean revokedAtProvider, boolean dataDeleted,
                               String resultMessage) {}

    /**
     * Ein Umsatz: Originaldaten der Bank und – getrennt davon – eigene Bearbeitungen.
     *
     * @param autoCategory automatisch erkannte Kategorie
     * @param category     wirksame Kategorie (eigene Wahl, sonst automatisch)
     */
    public record TransactionDto(Long id, Long accountId, String accountName, String bankName, String status,
                                 String statusLabel, LocalDate bookingDate, LocalDate valueDate, long amountCents,
                                 String currency, String counterparty, String remittance, boolean reversal,
                                 Long relatedId, Category autoCategory, Category category, String categoryLabel,
                                 String note, List<String> tags, boolean excluded, boolean edited, boolean sandbox) {}

    /**
     * Eigene Bearbeitung. Felder, die null sind, bleiben unverändert; leerer Text bzw. leere Liste löscht.
     */
    public record UpdateTransactionRequest(@Size(max = 30) String category, @Size(max = 500) String note,
                                           @Size(max = 10) List<@Size(max = 30) String> tags, Boolean excluded) {}
}
