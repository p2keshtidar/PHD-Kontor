package de.frauas.girokonto.simulation;

import java.time.LocalDate;

public record ClockDto(LocalDate date, long offsetDays) {
}
