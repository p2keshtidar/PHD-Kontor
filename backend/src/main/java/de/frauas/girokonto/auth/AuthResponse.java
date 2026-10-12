package de.frauas.girokonto.auth;

import de.frauas.girokonto.user.UserDto;

public record AuthResponse(String accessToken, String refreshToken, UserDto user) {
}
