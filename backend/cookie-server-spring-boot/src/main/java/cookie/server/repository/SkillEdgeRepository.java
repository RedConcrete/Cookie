package cookie.server.repository;

import cookie.server.entity.SkillEdgeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkillEdgeRepository extends JpaRepository<SkillEdgeEntity, String> {

    List<SkillEdgeEntity> findByFromNodeOrToNode(String fromNode, String toNode);
}
