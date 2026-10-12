package de.frauas.girokonto.transfer;

import de.frauas.girokonto.account.Account;
import de.frauas.girokonto.account.AccountRepository;
import de.frauas.girokonto.account.AccountStatus;
import de.frauas.girokonto.account.AccountType;
import de.frauas.girokonto.common.ApiException;
import de.frauas.girokonto.user.AppUser;
import de.frauas.girokonto.user.AppUserRepository;
import de.frauas.girokonto.user.UserService;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Findet den Empfänger einer Überweisung über IBAN, E-Mail oder Telefonnummer (/F210/).
 * Es sind nur Empfänger innerhalb der App möglich (Abgrenzung Kap. 2.3).
 */
@Component
public class RecipientResolver {

    private final AccountRepository accounts;
    private final AppUserRepository users;

    public RecipientResolver(AccountRepository accounts, AppUserRepository users) {
        this.accounts = accounts;
        this.users = users;
    }

    public Recipient resolve(String input) {
        String value = input == null ? "" : input.trim();
        String compact = value.replace(" ", "").toUpperCase(Locale.ROOT);

        Optional<Recipient> found;
        if (compact.matches("[A-Z]{2}\\d{2}[A-Z0-9]{10,30}")) {
            found = accounts.findByIban(compact)
                    .filter(a -> a.getType() == AccountType.MAIN && a.getStatus() == AccountStatus.ACTIVE)
                    .flatMap(a -> users.findById(a.getOwnerId()).map(u -> new Recipient(u, a)));
        } else if (value.contains("@")) {
            found = users.findByEmail(value.toLowerCase(Locale.ROOT)).flatMap(this::withMainAccount);
        } else {
            found = users.findByPhone(UserService.normalizePhone(value)).flatMap(this::withMainAccount);
        }
        return found.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RECIPIENT_NOT_FOUND",
                "Empfänger nicht gefunden. Überweisungen sind nur an Nutzer dieser App möglich."));
    }

    public Optional<Recipient> forUser(AppUser user) {
        return withMainAccount(user);
    }

    private Optional<Recipient> withMainAccount(AppUser user) {
        Optional<Account> main = accounts.findFirstByOwnerIdAndType(user.getId(), AccountType.MAIN)
                .filter(a -> a.getStatus() == AccountStatus.ACTIVE);
        return main.map(a -> new Recipient(user, a));
    }
}
