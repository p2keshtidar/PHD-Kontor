package de.frauas.girokonto.insights;

/**
 * Gesamtvermögen = Konten der Projektbank (inkl. digitaler Konten, Sparpläne, Kredite negativ) + Depotwerte
 * + Salden verbundener Konten anderer Banken in Euro (/F110/).
 */
public record NetWorthDto(long accountsCents, long depotCents, long loansCents, long totalCents, long externalCents) {
}
