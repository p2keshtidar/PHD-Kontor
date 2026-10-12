package de.frauas.girokonto.simulation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Entweder Tage oder Monate angeben (/F950/). */
public record AdvanceRequest(
        @Min(value = 0, message = "Ungültige Anzahl Tage.") @Max(value = 400, message = "Höchstens 400 Tage.") Integer days,
        @Min(value = 0, message = "Ungültige Anzahl Monate.") @Max(value = 12, message = "Höchstens 12 Monate.") Integer months) {
}
