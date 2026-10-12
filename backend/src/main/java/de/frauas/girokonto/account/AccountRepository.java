package de.frauas.girokonto.account;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByOwnerIdAndStatusOrderByIdAsc(Long ownerId, AccountStatus status);

    Optional<Account> findFirstByOwnerIdAndType(Long ownerId, AccountType type);

    Optional<Account> findFirstByTypeAndName(AccountType type, String name);

    Optional<Account> findByIban(String iban);

    /**
     * Liest das Konto und sperrt die Zeile bis zum Ende der Transaktion (SELECT ... FOR UPDATE).
     * So können zwei gleichzeitige Überweisungen das Konto nicht gemeinsam überziehen.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);

    @Query(value = "select nextval('account_number_seq')", nativeQuery = true)
    Long nextAccountNumber();
}
