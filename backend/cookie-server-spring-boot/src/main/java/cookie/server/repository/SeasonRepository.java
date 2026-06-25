package cookie.server.repository;

import cookie.server.entity.SeasonEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SeasonRepository extends JpaRepository<SeasonEntity, String> {
    Optional<SeasonEntity> findByActiveTrue();
}
