package de.frauas.girokonto.booking;

public enum BookingType {
    /** Startguthaben bei der Registrierung (/F010/) */
    INITIAL,
    /** Überweisung zwischen Nutzern (Phase 2) */
    TRANSFER,
    /** Umbuchung zwischen eigenen Konten (Phase 3) */
    INTERNAL,
    /** Kartenzahlung über den Händler-Simulator (Phase 3) */
    CARD,
    /** Monatliche Sparrate (Phase 4) */
    SAVING_RATE,
    /** Kauf/Verkauf im Musterdepot (Phase 4) */
    DEPOT,
    /** Einnahme von außen, z. B. Gehalt (Demo-Profile, Phase 4) */
    INCOME,
    /** Lastschrift, z. B. Miete (Demo-Profile, Phase 4) */
    DIRECT_DEBIT,
    /** Auszahlung eines Kredits (Phase 4) */
    LOAN_PAYOUT,
    /** Monatliche Kreditrate (Phase 4) */
    LOAN_RATE
}
