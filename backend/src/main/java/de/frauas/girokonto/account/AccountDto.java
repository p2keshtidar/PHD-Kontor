package de.frauas.girokonto.account;

/**
 * @param shared      /K060/: Unterkonto ist mit anderen geteilt (eigenes mit Mitgliedern oder von jemand anderem)
 * @param ownerName   Besitzer, falls das Konto jemand anderem gehört (sonst null)
 * @param memberCount Anzahl Mitglieder (ohne Besitzer)
 * @param digital     nur bei digitalen Konten (Typ SUB): Zweck, Ziel, Fortschritt, Rolle, Art der Verwahrung
 * @param product     nur bei Bankkonten der Projektbank (Girokonto, weiteres Girokonto, Tagesgeld)
 */
public record AccountDto(Long id, AccountType type, String name, String icon, String iban, long balanceCents,
                         boolean shared, String ownerName, int memberCount, DigitalInfo digital, ProductInfo product) {

    public AccountDto(Long id, AccountType type, String name, String icon, String iban, long balanceCents) {
        this(id, type, name, icon, iban, balanceCents, false, null, 0, null, null);
    }

    public AccountDto(Long id, AccountType type, String name, String icon, String iban, long balanceCents,
                      boolean shared, String ownerName, int memberCount) {
        this(id, type, name, icon, iban, balanceCents, shared, ownerName, memberCount, null, null);
    }

    public AccountDto(Long id, AccountType type, String name, String icon, String iban, long balanceCents,
                      boolean shared, String ownerName, int memberCount, DigitalInfo digital) {
        this(id, type, name, icon, iban, balanceCents, shared, ownerName, memberCount, digital, null);
    }
}
