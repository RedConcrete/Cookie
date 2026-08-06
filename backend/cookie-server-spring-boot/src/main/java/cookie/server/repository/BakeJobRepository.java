package cookie.server.repository;

import cookie.server.entity.BakeJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BakeJobRepository extends JpaRepository<BakeJobEntity, String> {
    long countByUserIdAndClaimedFalse(String userId);
    java.util.List<BakeJobEntity> findAllByUserIdAndClaimedFalse(String userId);
}
