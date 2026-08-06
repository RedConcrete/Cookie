package cookie.server.service;

import cookie.server.config.PlayerConfig;
import cookie.server.dto.UserDto;
import cookie.server.dto.UserInformationDto;
import cookie.server.entity.UserEntity;
import cookie.server.enums.EffectType;
import cookie.server.enums.ResourceName;
import cookie.server.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class UserService {

    private static final double RECIPE_INGREDIENT_PER_BATCH = 10.0;
    private static final double RECIPE_COOKIES_PER_BATCH    = 100.0;

    // One "tick" worth of harvest = the amount a single hover-tick granted back when
    // the client called this endpoint every HARVEST_TICK_MS while hovering. Now the
    // client batches calls (every few seconds, or on hover-stop) and the amount scales
    // with real elapsed server time instead -- MAX_HARVEST_BATCH_MS caps how much a
    // single call can ever grant, so a client can't sit on the endpoint and claim hours
    // of AFK hovering; it's clamped to a small multiple of the intended batch interval.
    private static final long HARVEST_TICK_MS       = 900;
    private static final long MAX_HARVEST_BATCH_MS  = 6000;

    private final UserRepository userRepository;
    private final PlayerConfig playerConfig;
    private final SkillTreeService skillTreeService;
    private final PrestigeService prestigeService;
    private final SteamAvatarService steamAvatarService;

    public UserService(UserRepository userRepository, PlayerConfig playerConfig,
                       SkillTreeService skillTreeService, PrestigeService prestigeService,
                       SteamAvatarService steamAvatarService) {
        this.userRepository = userRepository;
        this.playerConfig = playerConfig;
        this.skillTreeService = skillTreeService;
        this.steamAvatarService = steamAvatarService;
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
                    boolean changed = false;
                    if (cleanName != null && !cleanName.equals(existing.getDisplayName())) {
                        existing.setDisplayName(cleanName);
                        changed = true;
                    }
                    // Avatar-URL nur nachladen wenn noch keine gecacht ist oder der Name sich
                    // geaendert hat -- nicht bei jedem Login neu gegen die Steam Web API pruefen.
                    String oldAvatarUrl = existing.getAvatarUrl();
                    if (oldAvatarUrl == null || changed) {
                        String freshUrl = steamAvatarService.fetchAvatarUrl(userId);
                        if (freshUrl != null) {
                            existing.setAvatarUrl(freshUrl);
                            changed = true;
                        }
                    }
                    changed |= downloadAvatarIfNeeded(existing, oldAvatarUrl);
                    if (changed) userRepository.save(existing);
                    return toDto(existing);
                })
                .orElseGet(() -> {
                    UserEntity newUser = new UserEntity();
                    newUser.setSteamId(userId);
                    newUser.setDisplayName(cleanName);
                    newUser.setAvatarUrl(steamAvatarService.fetchAvatarUrl(userId));
                    downloadAvatarIfNeeded(newUser, null);
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

    // (Re)downloads the avatar image bytes whenever the URL is new/changed, or we simply
    // never cached bytes yet (e.g. rows created before this field existed). Fetched once,
    // then served back out of our own DB via getAvatarImage() -- see UserController#getAvatar.
    private boolean downloadAvatarIfNeeded(UserEntity user, String previousAvatarUrl) {
        String currentUrl = user.getAvatarUrl();
        if (currentUrl == null) return false;
        boolean needsDownload = user.getAvatarBytes() == null || !currentUrl.equals(previousAvatarUrl);
        if (!needsDownload) return false;
        SteamAvatarService.AvatarImage image = steamAvatarService.downloadAvatarImage(currentUrl);
        if (image == null) return false;
        user.setAvatarBytes(image.bytes());
        user.setAvatarContentType(image.contentType());
        return true;
    }

    public record AvatarPayload(byte[] bytes, String contentType) {}

    public Optional<AvatarPayload> getAvatarImage(String userId) {
        return userRepository.findById(userId)
                .filter(u -> u.getAvatarBytes() != null)
                .map(u -> new AvatarPayload(u.getAvatarBytes(), u.getAvatarContentType()));
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

        double harvestBonus = skillTreeService.getEffectTotal(userId, EffectType.HARVEST_YIELD, resource.name());
        double prestigeMultiplier = prestigeService.calcMultiplier(user.getPrestigeLevel());

        // Ticks are derived purely from our own clock (now - lastHarvestAt), never from
        // anything the client sends -- the request body only carries which resource.
        LocalDateTime now = LocalDateTime.now();
        long elapsedMs = user.getLastHarvestAt() == null
                ? HARVEST_TICK_MS
                : Duration.between(user.getLastHarvestAt(), now).toMillis();
        elapsedMs = Math.max(0, Math.min(elapsedMs, MAX_HARVEST_BATCH_MS));
        double ticks = elapsedMs / (double) HARVEST_TICK_MS;
        user.setLastHarvestAt(now);

        double amount = (1.0 + harvestBonus) * prestigeMultiplier * ticks;

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

    // Relative path to our own cached-avatar endpoint (UserController#getAvatar), so clients
    // always load the image from our server instead of hotlinking Steam's CDN directly.
    // Null until the bytes are actually cached (e.g. no Steam Web API key configured).
    public static String avatarEndpointFor(UserEntity entity) {
        return entity.getAvatarBytes() != null ? "/api/v1/users/" + entity.getSteamId() + "/avatar" : null;
    }

    private UserInformationDto toDto(UserEntity entity) {
        UserInformationDto dto = new UserInformationDto();
        dto.setSteamId(entity.getSteamId());
        dto.setDisplayName(entity.getDisplayName());
        dto.setAvatarUrl(avatarEndpointFor(entity));
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
