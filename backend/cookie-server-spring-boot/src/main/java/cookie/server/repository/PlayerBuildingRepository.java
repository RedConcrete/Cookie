package cookie.server.repository;

import cookie.server.entity.PlayerBuildingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlayerBuildingRepository extends JpaRepository<PlayerBuildingEntity, String> {
    List<PlayerBuildingEntity> findByUserId(String userId);
    Optional<PlayerBuildingEntity> findByUserIdAndBuildingId(String userId, String buildingId);
    void deleteByUserId(String userId);
}
