package cookie.server.repository;

import cookie.server.entity.MarketSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MarketSnapshotRepository extends JpaRepository<MarketSnapshotEntity, String> {

    List<MarketSnapshotEntity> findAllByOrderByDateAsc();

    @Query("SELECT s FROM MarketSnapshotEntity s WHERE s.date < :cutoff ORDER BY s.date ASC")
    List<MarketSnapshotEntity> findOlderThan(@Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT s FROM MarketSnapshotEntity s WHERE s.date >= :from AND s.date < :to ORDER BY s.date ASC")
    List<MarketSnapshotEntity> findBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    void deleteByDateBefore(LocalDateTime cutoff);
}
