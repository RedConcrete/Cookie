package cookie.server.repository;

import cookie.server.entity.MarketStockEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MarketStockRepository extends JpaRepository<MarketStockEntity, String> {
}
