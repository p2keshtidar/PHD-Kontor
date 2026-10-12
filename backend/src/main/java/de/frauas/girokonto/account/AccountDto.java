package de.frauas.girokonto.account;

/**
 * @param shared      /K060/: Unterkonto ist mit anderen geteilt (eigenes mit Mitgliedern oder von jemand anderem)
 * @param ownerName   Besitzer, falls das Konto jemand anderem gehört (sonst null)
 * @param memberCount Anzahl Mitglieder (ohne Besitzer)
 */
public record AccountDto(Long id, AccountType type, String name, String icon, String iban, long balanceCents,
                         boolean shared, String ownerName, int memberCount) {

    public AccountDto(Long id, AccountType type, String name, String icon, String iban, long balanceCents) {
        this(id, type, name, icon, iban, balanceCents, false, null, 0);
    }
}
