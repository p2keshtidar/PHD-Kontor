package de.frauas.girokonto.contact;

/** displayName = Spitzname, falls gesetzt, sonst voller Name. */
public record ContactDto(Long id, Long userId, String displayName, String fullName, String iban) {
}
