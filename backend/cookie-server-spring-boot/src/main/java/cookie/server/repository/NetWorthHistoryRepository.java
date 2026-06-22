package cookie.server.repository;

import cookie.server.entity.NetWorthHistoryEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface NetWorthHistoryRepository extends JpaRepository<NetWorthHistoryEntity, String> {

    List<NetWorthHistoryEntity> findByUserIdOrderByTimestampAsc(String userId);

    @Modifying
    @Query("DELETE FROM NetWorthHistoryEntity n WHERE n.userId = :userId AND n.timestamp < :before")
    void deleteOldByUserId(String userId, LocalDateTime before);
}
