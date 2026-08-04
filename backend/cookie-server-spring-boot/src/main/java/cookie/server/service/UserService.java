package cookie.server.service;

import cookie.server.config.PlayerConfig;
import cookie.server.dto.UserDto;
import cookie.server.dto.UserInformationDto;
import cookie.server.entity.UserEntity;
import cookie.server.enums.ResourceName;
import cookie.server.repository.PlayerUpgradeRepository;
import cookie.server.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class UserService {

    private static final double RECIPE_INGREDIENT_PER_BATCH = 10.0;
    private static final double RECIPE_COOKIES_PER_BATCH    = 100.0;

    private final UserRepository userRepository;
    private final PlayerConfig playerConfig;
    private final PlayerUpgradeRepository playerUpgradeRepository;
    private final PrestigeService prestigeService;

    public UserService(UserRepository userRepository, PlayerConfig playerConfig,
                       PlayerUpgradeRepository playerUpgradeRepository, PrestigeService prestigeService) {
        this.userRepository = userRepository;
        this.playerConfig = playerConfig;
        this.playerUpgradeRepository = playerUpgradeRepository;
        this.prestigeService = prestigeService;
    }

    public UserInformationDto createUser(String userId, UserDto dto) {
        if (userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User already exists");
        }

        UserEntity entity = new UserEntity();
        entity.setSteamId(userId);
        entity.setToken(dto.getToken());
        entity.setCookies(playerConfig.getInitialCookies());
        entity.setSugar(playerConfig.getInitialSugar());
        entity.setFlour(playerConfig.getInitialFlour());
        entity.setEggs(playerConfig.getInitialEggs());
        entity.setButter(playerConfig.getInitialButter());
        entity.setChocolate(playerConfig.getInitialChocolate());
        entity.setMilk(playerConfig.getInitialMilk());

        userRepository.save(entity);
        return toDto(entity);
    }

    public UserInformationDto getUser(String userId) {
        return getUser(userId, null);
    }

    // displayName: Steam display name resynced on every login (see GameController#initializeGame).
    // Not trusted for anything beyond cosmetic display -- capped and stored as-is.
    public UserInformationDto getUser(String userId, String displayName) {
        String cleanName = normalizeDisplayName(displayName);
        return userRepository.findById(userId)
                .map(existing -> {
                    if (cleanName != null && !cleanName.equals(existing.getDisplayName())) {
                        existing.setDisplayName(cleanName);
                        userRepository.save(existing);
                    }
                    return toDto(existing);
                })
                .orElseGet(() -> {
                    UserEntity newUser = new UserEntity();
                    newUser.setSteamId(userId);
                    newUser.setDisplayName(cleanName);
                    newUser.setCookies(playerConfig.getInitialCookies());
                    newUser.setSugar(playerConfig.getInitialSugar());
                    newUser.setFlour(playerConfig.getInitialFlour());
                    newUser.setEggs(playerConfig.getInitialEggs());
                    newUser.setButter(playerConfig.getInitialButter());
                    newUser.setChocolate(playerConfig.getInitialChocolate());
                    newUser.setMilk(playerConfig.getInitialMilk());
                    userRepository.save(newUser);
                    return toDto(newUser);
                });
    }

    private String normalizeDisplayName(String displayName) {
        if (displayName == null) return null;
        String trimmed = displayName.trim();
        if (trimmed.isEmpty()) return null;
        return trimmed.length() > 64 ? trimmed.substring(0, 64) : trimmed;
    }


    @Transactional
    public UserInformationDto produce(String userId, int batches) {
        if (batches < 1) {
            throw new IllegalArgumentException("Amount must be at least 1");
        }

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        double required = batches * RECIPE_INGREDIENT_PER_BATCH;

        if (user.getSugar()     < required) throw new IllegalArgumentException("Not enough sugar. Need " + required + ", have " + user.getSugar());
        if (user.getFlour()     < required) throw new IllegalArgumentException("Not enough flour. Need " + required + ", have " + user.getFlour());
        if (user.getEggs()      < required) throw new IllegalArgumentException("Not enough eggs. Need " + required + ", have " + user.getEggs());
        if (user.getButter()    < required) throw new IllegalArgumentException("Not enough butter. Need " + required + ", have " + user.getButter());
        if (user.getChocolate() < required) throw new IllegalArgumentException("Not enough chocolate. Need " + required + ", have " + user.getChocolate());
        if (user.getMilk()      < required) throw new IllegalArgumentException("Not enough milk. Need " + required + ", have " + user.getMilk());

        user.setSugar(user.getSugar()         - required);
        user.setFlour(user.getFlour()         - required);
        user.setEggs(user.getEggs()           - required);
        user.setButter(user.getButter()       - required);
        user.setChocolate(user.getChocolate() - required);
        user.setMilk(user.getMilk()           - required);
        user.setCookies(user.getCookies()     + batches * RECIPE_COOKIES_PER_BATCH);

        userRepository.save(user);
        return toDto(user);
    }

    @Transactional
    public UserInformationDto harvest(String userId, ResourceName resource,
                                      double storageCap, double marketPrice, double sellFeeRate) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        int boostLevel = playerUpgradeRepository
                .findByUserIdAndUpgradeId(userId, "boost_harvest")
                .map(pu -> pu.getLevel())
                .orElse(0);
        double prestigeMultiplier = prestigeService.calcMultiplier(user.getPrestigeLevel());
        double amount = (1.0 + boostLevel * 0.5) * prestigeMultiplier;

        double totalRes = user.getSugar() + user.getFlour() + user.getEggs()
                        + user.getButter() + user.getChocolate() + user.getMilk();
        double available = Math.max(0, storageCap - totalRes);
        double overflow  = Math.max(0, amount - available);
        double toAdd     = amount - overflow;

        if (overflow > 0) {
            double payout = overflow * marketPrice * (1.0 - sellFeeRate);
            user.setCookies(user.getCookies() + payout);
        }

        switch (resource) {
            case SUGAR     -> user.setSugar(user.getSugar()         + toAdd);
            case FLOUR     -> user.setFlour(user.getFlour()         + toAdd);
            case EGGS      -> user.setEggs(user.getEggs()           + toAdd);
            case BUTTER    -> user.setButter(user.getButter()       + toAdd);
            case CHOCOLATE -> user.setChocolate(user.getChocolate() + toAdd);
            case MILK      -> user.setMilk(user.getMilk()           + toAdd);
        }
        userRepository.save(user);
        return toDto(user);
    }

    public void deleteUser(String userId) {
        if (!userRepository.existsById(userId)) {
            throw new NoSuchElementException("User not found");
        }
        userRepository.deleteById(userId);
    }

    private UserInformationDto toDto(UserEntity entity) {
        UserInformationDto dto = new UserInformationDto();
        dto.setSteamId(entity.getSteamId());
        dto.setDisplayName(entity.getDisplayName());
        dto.setCookies(entity.getCookies());
        dto.setSugar(entity.getSugar());
        dto.setFlour(entity.getFlour());
        dto.setEggs(entity.getEggs());
        dto.setButter(entity.getButter());
        dto.setChocolate(entity.getChocolate());
        dto.setMilk(entity.getMilk());
        dto.setWorkersIdle(entity.isWorkersIdle());
        dto.setOwnedCitizens(entity.getOwnedCitizens());
        return dto;
    }
}
