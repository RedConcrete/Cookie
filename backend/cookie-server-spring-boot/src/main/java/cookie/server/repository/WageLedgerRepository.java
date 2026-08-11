package cookie.server.repository;

import cookie.server.entity.WageLedgerEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface WageLedgerRepository extends JpaRepository<WageLedgerEntity, String> {
    List<WageLedgerEntity> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    void deleteByUserId(String userId);

    /** Prune-Helfer: löscht alles vor `cutoff`, aufgerufen mit dem createdAt des ältesten noch
     * zu behaltenden Eintrags -- siehe WageService#deductWageForUser. */
    long deleteByUserIdAndCreatedAtBefore(String userId, LocalDateTime cutoff);
}
