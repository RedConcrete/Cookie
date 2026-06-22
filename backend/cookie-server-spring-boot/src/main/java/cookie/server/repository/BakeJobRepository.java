package cookie.server.repository;

import cookie.server.entity.BakeJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BakeJobRepository extends JpaRepository<BakeJobEntity, String> {
    Optional<BakeJobEntity> findByUserIdAndClaimedFalse(String userId);
    long countByUserIdAndClaimedFalse(String userId);
    java.util.List<BakeJobEntity> findAllByUserIdAndClaimedFalse(String userId);
}
