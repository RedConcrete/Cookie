package cookie.server.service;

import cookie.server.dto.UserInformationDto;
import cookie.server.entity.PlayerBuildingEntity;
import cookie.server.entity.UserEntity;
import cookie.server.enums.ResourceName;
import cookie.server.repository.PlayerBuildingRepository;
import cookie.server.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

/**
 * Einsammeln der passiven Gebäude-Produktion -- wie beim Backen (BakeService#claim): das
 * Gebäude sammelt lokal an (BuildingService#settle), hier wird der angesammelte Betrag ins
 * Spieler-Inventar überführt. Ersetzt den alten, ständig laufenden 5s-Scheduler-Tick über
 * alle Spieler -- kein Hintergrund-Tick mehr, alles wird lazy bei Bedarf berechnet.
 */
@Service
public class PassiveIncomeService {

    private final UserRepository userRepository;
    private final PlayerBuildingRepository buildingRepo;
    private final BuildingService buildingService;

    public PassiveIncomeService(UserRepository userRepository,
                                PlayerBuildingRepository buildingRepo,
                                BuildingService buildingService) {
        this.userRepository = userRepository;
        this.buildingRepo = buildingRepo;
        this.buildingService = buildingService;
    }

    @Transactional
    public UserInformationDto collectBuilding(String userId, String buildingId) {
        PlayerBuildingEntity ent = buildingRepo.findByUserIdAndBuildingId(userId, buildingId)
                .orElseThrow(() -> new NoSuchElementException("Building not owned: " + buildingId));

        BuildingService.BuildingDef def = BuildingService.getDefMap().get(buildingId);
        if (def == null || def.passiveResource() == null)
            throw new IllegalStateException("Building has no passive resource: " + buildingId);

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        buildingService.settle(ent, def, user.isWorkersIdle(), LocalDateTime.now());

        double cap = buildingService.getTotalCap(userId);
        double total = user.getSugar() + user.getFlour() + user.getEggs()
                     + user.getButter() + user.getChocolate() + user.getMilk();
        double available = Math.max(0, cap - total);

        // No overflow, no auto-sell fürs Lager (siehe docs/ROADMAP.md) -- was wegen vollem
        // Lager nicht reinpasst, bleibt im Gebäude liegen (pendingAmount wird nur um den
        // tatsächlich gutgeschriebenen Anteil reduziert) statt verworfen zu werden. Das
        // Gebäude bleibt dadurch "voll"/inaktiv bis wieder Platz im Lager ist.
        double credited = Math.min(ent.getPendingAmount(), available);
        if (credited > 0) {
            addResource(user, def.passiveResource(), credited);
            user.addLifetimeHarvested(def.passiveResource(), credited);
            userRepository.save(user);
        }

        ent.setPendingAmount(ent.getPendingAmount() - credited);
        buildingRepo.save(ent);

        UserInformationDto dto = new UserInformationDto();
        dto.setSteamId(user.getSteamId());
        dto.setCookies(user.getCookies());
        dto.setSugar(user.getSugar());
        dto.setFlour(user.getFlour());
        dto.setEggs(user.getEggs());
        dto.setButter(user.getButter());
        dto.setChocolate(user.getChocolate());
        dto.setMilk(user.getMilk());
        dto.setTotalResourceCap(cap);
        return dto;
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
