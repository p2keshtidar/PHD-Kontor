package de.frauas.girokonto.auth;

import de.frauas.girokonto.user.Role;

/** Der angemeldete Nutzer, wie er in Controllern über @AuthenticationPrincipal ankommt. */
public record AuthUser(Long id, String email, Role role) {
}
