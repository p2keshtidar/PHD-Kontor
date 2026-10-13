package de.frauas.girokonto.entry;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntryRepository extends JpaRepository<Entry, Long> {

    List<Entry> findByUserIdAndStatusInOrderByIdAsc(Long userId, Collection<Entry.Status> status);

    List<Entry> findByStatus(Entry.Status status);

    Optional<Entry> findByIdAndUserId(Long id, Long userId);
}
