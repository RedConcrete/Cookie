package cookie.server.repository;

import cookie.server.entity.SeasonResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeasonResultRepository extends JpaRepository<SeasonResultEntity, String> {
    List<SeasonResultEntity> findByUserIdOrderBySeasonIdDesc(String userId);
    List<SeasonResultEntity> findBySeasonId(String seasonId);
}
