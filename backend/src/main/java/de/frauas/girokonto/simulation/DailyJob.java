package de.frauas.girokonto.simulation;

import java.time.LocalDate;

/**
 * Eine Aufgabe, die einmal pro (simuliertem) Tag läuft, z. B. Sparraten oder Kreditraten.
 * Die Reihenfolge bestimmt order(): kleinere Zahl läuft zuerst.
 */
public interface DailyJob {

    int order();

    void runFor(LocalDate date);
}
