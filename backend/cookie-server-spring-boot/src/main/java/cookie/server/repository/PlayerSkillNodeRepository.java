package cookie.server.repository;

import cookie.server.entity.PlayerSkillNodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlayerSkillNodeRepository extends JpaRepository<PlayerSkillNodeEntity, String> {

    List<PlayerSkillNodeEntity> findByUserId(String userId);

    boolean existsByUserIdAndNodeId(String userId, String nodeId);

    boolean existsByNodeId(String nodeId);

    void deleteByUserId(String userId);
}
