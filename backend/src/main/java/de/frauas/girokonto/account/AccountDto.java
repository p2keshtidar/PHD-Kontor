package de.frauas.girokonto.account;

public record AccountDto(Long id, AccountType type, String name, String icon, String iban, long balanceCents) {
}
