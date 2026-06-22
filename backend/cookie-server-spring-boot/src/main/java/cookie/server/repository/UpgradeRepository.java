package cookie.server.repository;

import cookie.server.entity.UpgradeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UpgradeRepository extends JpaRepository<UpgradeEntity, String> {
}
