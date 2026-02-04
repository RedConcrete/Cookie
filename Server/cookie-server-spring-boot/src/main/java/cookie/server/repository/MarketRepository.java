package cookie.server.repository;

import cookie.server.entity.MarketEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MarketRepository extends JpaRepository<MarketEntity, String> {
    List<MarketEntity> findAllByOrderByDateDesc(Pageable pageable);

    /**
     * Loescht alle Eintraege ausser den neuesten X.
     */
    @Modifying
    @Query(value = "DELETE FROM market WHERE id NOT IN (SELECT id FROM market ORDER BY date DESC LIMIT :keepCount)", nativeQuery = true)
    void deleteOldEntries(@Param("keepCount") int keepCount);
}
