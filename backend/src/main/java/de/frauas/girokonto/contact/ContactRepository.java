package de.frauas.girokonto.contact;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    List<Contact> findByOwnerIdOrderByIdAsc(Long ownerId);

    Optional<Contact> findByIdAndOwnerId(Long id, Long ownerId);

    boolean existsByOwnerIdAndContactUserId(Long ownerId, Long contactUserId);
}
