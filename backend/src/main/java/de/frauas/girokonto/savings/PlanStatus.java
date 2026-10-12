package de.frauas.girokonto.savings;

public enum PlanStatus {
    ACTIVE,
    PAUSED,
    /** ausgezahlt nach Erreichen des Ziels */
    COMPLETED,
    /** vorzeitig aufgelöst */
    CLOSED
}
