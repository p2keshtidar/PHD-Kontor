package de.frauas.girokonto.card;

/** Volle Kartendaten, nur nach erneuter Passworteingabe (/F310/). */
public record CardDetailsDto(String number, String cvc, String expiry) {
}
