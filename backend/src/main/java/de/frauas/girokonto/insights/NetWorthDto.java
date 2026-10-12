package de.frauas.girokonto.insights;

/** Gesamtvermögen = Konten (inkl. Unterkonten, Sparpläne, Kredite negativ) + Depotwerte (/F110/). */
public record NetWorthDto(long accountsCents, long depotCents, long loansCents, long totalCents) {
}
