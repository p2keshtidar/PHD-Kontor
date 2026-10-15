package de.frauas.girokonto.user;

import de.frauas.girokonto.audit.AuditLog;
import de.frauas.girokonto.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Profil anzeigen und ändern (/F030/). */
@Service
public class UserService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    private final AuditLog audit;

    public UserService(AppUserRepository users, PasswordEncoder passwordEncoder, AuditLog audit) {
        this.audit = audit;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public AppUser get(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Benutzer nicht gefunden."));
    }

    @Transactional
    public void setLanguage(Long userId, String language) {
        get(userId).setLanguage(language);
    }

    @Transactional
    public AppUser updateProfile(Long userId, UpdateProfileRequest req) {
        AppUser user = get(userId);
        String phone = normalizePhone(req.phone());
        if (users.existsByPhoneAndIdNot(phone, userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "PHONE_EXISTS", "Diese Telefonnummer wird bereits verwendet.");
        }
        user.setFirstName(req.firstName().trim());
        user.setLastName(req.lastName().trim());
        user.setPhone(phone);
        return user;
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req) {
        AppUser user = get(userId);
        if (!passwordEncoder.matches(req.oldPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WRONG_PASSWORD", "Das alte Passwort ist falsch.");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        audit.record(userId, "PASSWORD_CHANGED", null, null);
    }

    /**
     * Einheitliche Form für Telefonnummern, damit "+49 151-1234567", "0049 151 1234567"
     * und "01511234567" als dieselbe Nummer erkannt werden (wichtig für den Adressbuch-Abgleich /F220/).
     */
    public static String normalizePhone(String phone) {
        if (phone == null) {
            return "";
        }
        String p = phone.trim();
        boolean plus = p.startsWith("+");
        p = p.replaceAll("[^0-9]", "");
        if (plus && p.startsWith("49")) {
            return "0" + p.substring(2);
        }
        if (!plus && p.startsWith("0049")) {
            return "0" + p.substring(4);
        }
        return plus ? "+" + p : p;
    }
}
