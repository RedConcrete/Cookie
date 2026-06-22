package cookie.server.service;

import cookie.server.config.AppConfig;
import cookie.server.dto.BakeJobStatusDto;
import cookie.server.dto.RecipeDto;
import cookie.server.dto.UserInformationDto;
import cookie.server.entity.BakeJobEntity;
import cookie.server.entity.RecipeEntity;
import cookie.server.entity.UserEntity;
import cookie.server.repository.BakeJobRepository;
import cookie.server.repository.PlayerUpgradeRepository;
import cookie.server.repository.RecipeRepository;
import cookie.server.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BakeService {

    private final RecipeRepository recipeRepository;
    private final BakeJobRepository bakeJobRepository;
    private final UserRepository userRepository;
    private final PlayerUpgradeRepository playerUpgradeRepository;
    private final AppConfig appConfig;

    public BakeService(RecipeRepository recipeRepository, BakeJobRepository bakeJobRepository,
                       UserRepository userRepository, PlayerUpgradeRepository playerUpgradeRepository,
                       AppConfig appConfig) {
        this.recipeRepository = recipeRepository;
        this.bakeJobRepository = bakeJobRepository;
        this.userRepository = userRepository;
        this.playerUpgradeRepository = playerUpgradeRepository;
        this.appConfig = appConfig;
    }

    @PostConstruct
    public void seedRecipes() {
        if (recipeRepository.count() > 0) return;

        recipeRepository.saveAll(List.of(
            recipe("standard",   "Standard",    10, 10, 10, 10, 10, 10,  100, 30),
            recipe("milkcookie", "Milchcookie",  5,  3,  3,  3,  3, 25,  130, 60),
            recipe("sparrezept", "Sparrezept",   5,  5,  5,  5,  5,  5,   40, 15)
        ));
    }

    private RecipeEntity recipe(String id, String name,
                                double sugar, double flour, double eggs,
                                double butter, double chocolate, double milk,
                                double output, int seconds) {
        RecipeEntity r = new RecipeEntity();
        r.setId(id);
        r.setName(name);
        r.setSugar(sugar);
        r.setFlour(flour);
        r.setEggs(eggs);
        r.setButter(butter);
        r.setChocolate(chocolate);
        r.setMilk(milk);
        r.setOutput(output);
        r.setBakeDurationSeconds(seconds);
        return r;
    }

    public List<RecipeDto> getRecipes() {
        return recipeRepository.findAll().stream().map(RecipeDto::new).collect(Collectors.toList());
    }

    @Transactional
    public BakeJobStatusDto startBake(String userId, String recipeId, int batches) {
        if (batches < 1) throw new IllegalArgumentException("Batches must be at least 1");

        int extraSlots = playerUpgradeRepository
                .findByUserIdAndUpgradeId(userId, "extra_oven")
                .map(pu -> pu.getLevel())
                .orElse(0);
        int maxSlots = 1 + extraSlots;
        long activeJobs = bakeJobRepository.countByUserIdAndClaimedFalse(userId);
        if (activeJobs >= maxSlots) {
            throw new IllegalStateException("Alle Ofen-Slots belegt. Erst einlösen oder Upgrade kaufen.");
        }

        RecipeEntity recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new NoSuchElementException("Recipe not found: " + recipeId));

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        double needed = (double) batches;
        validate(user.getSugar(),     recipe.getSugar()     * needed, "Zucker");
        validate(user.getFlour(),     recipe.getFlour()     * needed, "Mehl");
        validate(user.getEggs(),      recipe.getEggs()      * needed, "Eier");
        validate(user.getButter(),    recipe.getButter()    * needed, "Butter");
        validate(user.getChocolate(), recipe.getChocolate() * needed, "Schokolade");
        validate(user.getMilk(),      recipe.getMilk()      * needed, "Milch");

        user.setSugar(user.getSugar()         - recipe.getSugar()     * needed);
        user.setFlour(user.getFlour()         - recipe.getFlour()     * needed);
        user.setEggs(user.getEggs()           - recipe.getEggs()      * needed);
        user.setButter(user.getButter()       - recipe.getButter()    * needed);
        user.setChocolate(user.getChocolate() - recipe.getChocolate() * needed);
        user.setMilk(user.getMilk()           - recipe.getMilk()      * needed);
        userRepository.save(user);

        LocalDateTime now = LocalDateTime.now();
        BakeJobEntity job = new BakeJobEntity();
        job.setId(UUID.randomUUID().toString());
        job.setUserId(userId);
        job.setRecipeId(recipeId);
        job.setBatches(batches);
        job.setStartedAt(now);
        long durationSeconds = appConfig.isDevMode() ? 0 : (long) recipe.getBakeDurationSeconds() * batches;
        job.setCompletesAt(now.plusSeconds(durationSeconds));
        job.setClaimed(false);
        bakeJobRepository.save(job);

        return toStatus(job, recipe);
    }

    public BakeJobStatusDto getStatus(String userId) {
        return bakeJobRepository.findByUserIdAndClaimedFalse(userId)
                .map(job -> {
                    RecipeEntity recipe = recipeRepository.findById(job.getRecipeId()).orElseThrow();
                    return toStatus(job, recipe);
                })
                .orElseGet(() -> { BakeJobStatusDto s = new BakeJobStatusDto(); s.setActive(false); return s; });
    }

    @Transactional
    public UserInformationDto claim(String userId) {
        BakeJobEntity job = bakeJobRepository.findByUserIdAndClaimedFalse(userId)
                .orElseThrow(() -> new NoSuchElementException("No active bake job for user: " + userId));

        if (LocalDateTime.now().isBefore(job.getCompletesAt())) {
            throw new IllegalStateException("Bake job not done yet.");
        }

        RecipeEntity recipe = recipeRepository.findById(job.getRecipeId()).orElseThrow();
        UserEntity user = userRepository.findById(userId).orElseThrow();

        int bakeBoost = playerUpgradeRepository
                .findByUserIdAndUpgradeId(userId, "boost_bake")
                .map(pu -> pu.getLevel())
                .orElse(0);
        double outputMultiplier = 1.0 + bakeBoost * 0.10;
        double prestigeMultiplier = cookie.server.service.PrestigeService.calcMultiplier(user.getPrestigeLevel());
        double cookiesEarned = recipe.getOutput() * job.getBatches() * outputMultiplier * prestigeMultiplier;
        user.setCookies(user.getCookies() + cookiesEarned);
        user.setLifetimeCookiesBaked(user.getLifetimeCookiesBaked() + cookiesEarned);
        userRepository.save(user);

        job.setClaimed(true);
        bakeJobRepository.save(job);

        UserInformationDto dto = new UserInformationDto();
        dto.setSteamId(user.getSteamId());
        dto.setCookies(user.getCookies());
        dto.setSugar(user.getSugar());
        dto.setFlour(user.getFlour());
        dto.setEggs(user.getEggs());
        dto.setButter(user.getButter());
        dto.setChocolate(user.getChocolate());
        dto.setMilk(user.getMilk());
        return dto;
    }

    private void validate(double have, double need, String name) {
        if (have < need) throw new IllegalArgumentException("Nicht genug " + name + ". Brauche " + need + ", habe " + have);
    }

    private BakeJobStatusDto toStatus(BakeJobEntity job, RecipeEntity recipe) {
        BakeJobStatusDto s = new BakeJobStatusDto();
        s.setActive(true);
        s.setJobId(job.getId());
        s.setRecipe(new RecipeDto(recipe));
        s.setBatches(job.getBatches());
        s.setTotalCookies(recipe.getOutput() * job.getBatches());
        long remaining = Math.max(0, ChronoUnit.SECONDS.between(LocalDateTime.now(), job.getCompletesAt()));
        s.setRemainingSeconds(remaining);
        s.setDone(remaining == 0);
        s.setClaimed(job.isClaimed());
        return s;
    }
}
