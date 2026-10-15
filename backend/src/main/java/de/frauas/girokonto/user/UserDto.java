package de.frauas.girokonto.user;

import java.time.LocalDate;

public record UserDto(Long id, String email, String firstName, String lastName,
                      String phone, LocalDate birthDate, Role role, boolean twoFactorEnabled,
                      String language) {

    public static UserDto from(AppUser u) {
        return new UserDto(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(),
                u.getPhone(), u.getBirthDate(), u.getRole(), u.isTotpEnabled(), u.getLanguage());
    }
}
