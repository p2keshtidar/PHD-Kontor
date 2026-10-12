package de.frauas.girokonto.contact;

public record MatchDto(Long userId, String fullName, String iban, String phone, boolean alreadyContact) {
}
