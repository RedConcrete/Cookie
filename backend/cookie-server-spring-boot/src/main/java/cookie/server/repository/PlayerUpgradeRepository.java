package cookie.server.repository;

import cookie.server.entity.PlayerUpgradeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlayerUpgradeRepository extends JpaRepository<PlayerUpgradeEntity, String> {

    Optional<PlayerUpgradeEntity> findByUserIdAndUpgradeId(String userId, String upgradeId);

    List<PlayerUpgradeEntity> findByUserId(String userId);

    List<PlayerUpgradeEntity> findByUpgradeId(String upgradeId);
}
