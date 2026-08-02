package cookie.server.service;

import cookie.server.entity.PlayerBuildingEntity;
import cookie.server.entity.UserEntity;
import cookie.server.enums.ResourceName;
import cookie.server.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Eigene Transaktionsgrenze pro Spieler fuer den Passiv-Produktions-Tick (siehe
 * PassiveIncomeScheduler). Getrennt von der Scheduler-Klasse, weil @Transactional bei
 * Self-Invocation (Aufruf von this.methode() innerhalb derselben Klasse) vom Spring-Proxy
 * ignoriert wird -- der Retry-Loop im Scheduler braucht aber eine echte, pro Aufruf neue
 * Transaktion, damit ein Optimistic-Lock-Konflikt bei einem Spieler nicht den ganzen Tick
 * fuer alle anderen Spieler zurueckrollt.
 */
@Service
public class PassiveIncomeService {

    private final UserRepository userRepository;
    private final BuildingService buildingService;
    private final MarketService marketService;

    public PassiveIncomeService(UserRepository userRepository,
                                BuildingService buildingService,
                                MarketService marketService) {
        this.userRepository = userRepository;
        this.buildingService = buildingService;
        this.marketService = marketService;
    }

    @Transactional
    public void creditUser(String userId, double tickSeconds) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
        if (user.isWorkersIdle()) return;

        Map<String, PlayerBuildingEntity> owned = buildingService.ownedMap(userId);
        List<BuildingService.PassiveTick> ticks = buildingService.computePassiveTicks(owned, tickSeconds);
        if (ticks.isEmpty()) return;

        double cap = buildingService.getTotalCap(owned);
        double total = user.getSugar() + user.getFlour() + user.getEggs()
                     + user.getButter() + user.getChocolate() + user.getMilk();
        double available = Math.max(0, cap - total);

        double totalRequested = ticks.stream().mapToDouble(BuildingService.PassiveTick::amount).sum();
        // Reicht der Platz fuer alles, gibt's nichts zu verteilen -- jede Ressource bekommt ihr volles Soll.
        // Reicht er nicht, wird die verfuegbare Kapazitaet PROPORTIONAL zum Anteil jeder Ressource an der
        // Gesamtproduktion dieses Ticks aufgeteilt (statt Listenreihenfolge: erste Ressource frisst den
        // ganzen Platz, Rest faellt komplett in den Ueberlauf-Verkauf).
        double shareFactor = totalRequested <= available ? 1.0 : (totalRequested > 0 ? available / totalRequested : 0);
        double feeRate = buildingService.getEffectiveSellFeeRate(owned, marketService.getSellFeeRate());

        for (BuildingService.PassiveTick t : ticks) {
            double toAdd = t.amount() * shareFactor;
            double overflow = t.amount() - toAdd;

            if (overflow > 0) {
                double price = marketService.getCurrentPrice(t.resource());
                double payout = overflow * price * (1.0 - feeRate);
                user.setCookies(user.getCookies() + payout);
            }

            addResource(user, t.resource(), toAdd);
        }

        userRepository.save(user);
    }

    private void addResource(UserEntity user, ResourceName resource, double amount) {
        switch (resource) {
            case SUGAR     -> user.setSugar(user.getSugar() + amount);
            case FLOUR     -> user.setFlour(user.getFlour() + amount);
            case EGGS      -> user.setEggs(user.getEggs() + amount);
            case BUTTER    -> user.setButter(user.getButter() + amount);
            case CHOCOLATE -> user.setChocolate(user.getChocolate() + amount);
            case MILK      -> user.setMilk(user.getMilk() + amount);
        }
    }
}
