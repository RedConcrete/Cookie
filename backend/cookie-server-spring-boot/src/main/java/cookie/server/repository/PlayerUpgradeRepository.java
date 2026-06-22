package cookie.server.repository;

import cookie.server.entity.PlayerUpgradeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PlayerUpgradeRepository extends JpaRepository<PlayerUpgradeEntity, String> {

    Optional<PlayerUpgradeEntity> findByUserIdAndUpgradeId(String userId, String upgradeId);

    List<PlayerUpgradeEntity> findByUserId(String userId);

    @Query("SELECT pu FROM PlayerUpgradeEntity pu WHERE pu.upgradeId LIKE 'auto_%' AND pu.level > 0")
    List<PlayerUpgradeEntity> findActiveAutomations();
}
