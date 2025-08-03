package cookie.server.repository;

import cookie.server.entitiy.MarketEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MarketRepository extends JpaRepository<MarketEntity, Long> {
}
