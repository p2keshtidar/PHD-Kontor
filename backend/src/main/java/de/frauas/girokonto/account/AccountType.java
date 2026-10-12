package de.frauas.girokonto.account;

public enum AccountType {
    /** Girokonto (Hauptkonto) mit IBAN */
    MAIN,
    /** Unterkonto / Topf (Phase 3) */
    SUB,
    /** Unterkonto eines Sparziels (Phase 4) */
    SAVINGS,
    /** Kreditkonto, Saldo negativ = Restschuld (Phase 4) */
    LOAN,
    /** Interne Konten der simulierten Bank, z. B. BANK oder HAENDLER */
    SYSTEM
}
