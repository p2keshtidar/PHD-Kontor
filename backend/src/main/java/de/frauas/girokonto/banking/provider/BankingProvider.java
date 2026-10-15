package de.frauas.girokonto.banking.provider;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Anbieter für Kontoinformationen nach PSD2 (Open Banking, Kontoinformationsdienst).
 *
 * <p>Die Anmeldung bei der Bank geschieht immer beim Anbieter bzw. bei der Bank selbst (Weiterleitung).
 * Girokonto bekommt nur eine Kennung der Freigabe ({@code consentRef}) – niemals Bank-Passwörter.
 * Weitere Anbieter (z. B. Tink, finAPI, Yapily) lassen sich ergänzen, indem man diese Schnittstelle umsetzt.
 */
public interface BankingProvider {

    /** Kurzname, z. B. SANDBOX oder GOCARDLESS (wird gespeichert). */
    String id();

    String displayName();

    /** true = Testdaten, keine echte Bank. */
    boolean sandbox();

    /** false, wenn Zugangsdaten/Verträge fehlen – dann wird der Anbieter nicht angeboten. */
    boolean available();

    /** Mindestabstand zwischen zwei Abrufen eines Kontos (Grenzen der Banken bzw. des Anbieters). */
    Duration minSyncInterval();

    List<Institution> institutions(String country);

    /** Freigabe anlegen. Der Nutzer bestätigt sie anschließend bei der Bank ({@code authorizationUrl}). */
    ConsentStart startConsent(String institutionId, String redirectUrl, String reference, String language);

    ConsentState consentState(String consentRef);

    AccountData account(String consentRef, String accountId);

    /** Umsätze ab {@code from} (gebuchte und vorgemerkte). */
    List<TransactionData> transactions(String consentRef, String accountId, LocalDate from);

    /** Freigabe beim Anbieter löschen. */
    void revoke(String consentRef);

    /** Bank bzw. Institut. */
    record Institution(String id, String name, String bic, int historyDays, int accessDays, String logo) {}

    record ConsentStart(String consentRef, String authorizationUrl) {}

    enum ConsentStatus { PENDING, LINKED, REJECTED, EXPIRED }

    record ConsentState(ConsentStatus status, List<String> accountIds, Instant expiresAt) {}

    /**
     * @param iban vollständig, wird von Girokonto nur gekürzt und als Prüfwert gespeichert
     */
    record AccountData(String externalId, String iban, String name, String currency, Long balanceCents,
                       String balanceType, Instant balanceAt) {}

    /**
     * Ein Umsatz, wie die Bank ihn liefert.
     *
     * @param externalId  stabile Kennung (Bank-ID oder, falls keine geliefert, ein Prüfwert der Daten)
     * @param pending     vorgemerkt (noch nicht gebucht)
     * @param reversal    ist eine Rückbuchung/Rücklastschrift
     * @param reversalOf  Kennung des zurückgebuchten Umsatzes, falls die Quelle sie liefert
     * @param raw         Originaldaten (JSON), werden unverändert aufbewahrt
     */
    record TransactionData(String externalId, boolean pending, LocalDate bookingDate, LocalDate valueDate,
                           long amountCents, String currency, String counterparty, String counterpartyIban,
                           String remittance, String bankCode, boolean reversal, String reversalOf, String raw) {}
}
