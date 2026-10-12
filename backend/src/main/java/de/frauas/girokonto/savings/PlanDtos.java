package de.frauas.girokonto.savings;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** Ein- und Ausgabedaten der Spar- und Investpläne. */
public final class PlanDtos {

    private PlanDtos() {
    }

    public enum Feasibility { GREEN, YELLOW, RED, UNKNOWN }

    /** Machbarkeit prüfen (/F620/). Für Sparpläne: Ziel und Datum; Rate optional (sonst Vorschlag). */
    public record CheckRequest(
            @NotNull(message = "Plantyp fehlt.") PlanType type,
            Long targetCents,
            LocalDate targetDate,
            Long monthlyRateCents,
            @NotNull(message = "Risikostufe fehlt.") RiskLevel riskLevel) {}

    public record CheckResult(
            Long requiredRateCents,
            long rateCents,
            long availableCents,
            boolean hasData,
            Feasibility feasibility,
            String message,
            LocalDate projectedDate,
            Long alternativeRateCents,
            LocalDate alternativeDate,
            double expectedReturnPa) {}

    public record CreateRequest(
            @NotNull(message = "Plantyp fehlt.") PlanType type,
            @NotBlank(message = "Name fehlt.") @Size(max = 60, message = "Name zu lang.") String name,
            @Size(max = 30) String icon,
            Long targetCents,
            LocalDate targetDate,
            @NotNull(message = "Monatsrate fehlt.") Long monthlyRateCents,
            @NotNull @Min(value = 1, message = "Ausführungstag 1 bis 28.") @Max(value = 28, message = "Ausführungstag 1 bis 28.")
            Integer executionDay,
            @NotNull(message = "Risikostufe fehlt.") RiskLevel riskLevel) {}

    public record UpdateRequest(
            @Size(max = 60) String name,
            @Size(max = 30) String icon,
            Long monthlyRateCents,
            @Min(1) @Max(28) Integer executionDay,
            LocalDate targetDate,
            Long targetCents,
            RiskLevel riskLevel,
            Boolean paused) {}

    public record Summary(
            Long id,
            PlanType type,
            String name,
            String icon,
            PlanStatus status,
            RiskLevel riskLevel,
            Long targetCents,
            LocalDate targetDate,
            long monthlyRateCents,
            int executionDay,
            long currentValueCents,
            long depositedCents,
            Double progressPercent,
            LocalDate projectedDate,
            Boolean onTrack,
            LocalDate nextExecutionDate,
            Long accountId) {}

    public record Position(Fund fund, String label, double units, double price, long valueCents,
                           long investedCents, double weightPercent) {}

    public record HistoryPoint(LocalDate date, long valueCents, long depositedCents) {}

    public record Tip(String category, String label, long lastMonthCents, long averageCents,
                      long saveCents, Integer monthsEarlier, String text) {}

    public record Detail(
            Summary summary,
            long cashCents,
            long depotValueCents,
            long gainCents,
            double gainPercent,
            double expectedReturnPa,
            String riskExplanation,
            Long requiredRateCents,
            List<Position> positions,
            List<HistoryPoint> history,
            List<Tip> tips) {}
}
