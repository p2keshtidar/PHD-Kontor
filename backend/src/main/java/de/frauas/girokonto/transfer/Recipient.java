package de.frauas.girokonto.transfer;

import de.frauas.girokonto.account.Account;
import de.frauas.girokonto.user.AppUser;

/** Ein gefundener Empfänger: Nutzer und sein Girokonto. */
public record Recipient(AppUser user, Account account) {

    public String fullName() {
        return user.getFirstName() + " " + user.getLastName();
    }
}
