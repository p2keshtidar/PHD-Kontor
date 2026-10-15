package de.frauas.girokonto.notification;

public enum NotificationType {
    WELCOME,
    TRANSFER_IN,
    TRANSFER_OUT,
    CARD_PAYMENT,
    CARD_DECLINED,
    INTERNAL,
    SAVING_RATE,
    GOAL,
    INFO,
    /** verbundene Bankkonten (Freigabe läuft ab, Abruf gestört) */
    BANK
}
