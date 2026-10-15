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
    /** Weiteres Girokonto der Projektbank mit eigener IBAN (Multi-Banking, Simulation) */
    CURRENT,
    /** Tagesgeldkonto der Projektbank: Zinsen monatlich, Geld nur über eigene Konten (Multi-Banking, Simulation) */
    CALL,
    /** Interne Konten der simulierten Bank, z. B. BANK oder HAENDLER */
    SYSTEM
}
