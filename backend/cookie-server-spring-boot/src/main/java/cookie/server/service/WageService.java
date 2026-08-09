package cookie.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import cookie.server.config.GameBalanceConfig;
import cookie.server.dto.WageLedgerEntryDto;
import cookie.server.entity.UserEntity;
import cookie.server.entity.WageLedgerEntity;
import cookie.server.enums.EffectType;
import cookie.server.repository.UserRepository;
import cookie.server.repository.WageLedgerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Eigene Transaktionsgrenze pro Spieler fuer den Lohn-Abzug (siehe WageScheduler) --
 * gleicher Grund wie bei PassiveIncomeService: @Transactional greift bei Self-Invocation
 * innerhalb derselben Klasse nicht, und ein Optimistic-Lock-Konflikt bei einem Spieler
 * soll nicht den Lohnlauf fuer alle anderen zurueckrollen.
 */
@Service
public class WageService {
    private static final Logger log = LoggerFactory.getLogger(WageService.class);

    private final UserRepository userRepository;
    private final BuildingService buildingService;
    private final SkillTreeService skillTreeService;
    private final WageLedgerRepository wageLedgerRepository;
    private final GameBalanceConfig balance;
    private final ObjectMapper mapper = new ObjectMapper();

    public WageService(UserRepository userRepository, BuildingService buildingService,
                       SkillTreeService skillTreeService, WageLedgerRepository wageLedgerRepository,
                       GameBalanceConfig balance) {
        this.userRepository = userRepository;
        this.buildingService = buildingService;
        this.skillTreeService = skillTreeService;
        this.wageLedgerRepository = wageLedgerRepository;
        this.balance = balance;
    }

    /** Effektiver Dispo-Zinssatz nach Abzug der DISPO-Skillbaum-Knoten, nie unter dem
     * konfigurierten Mindestsatz -- auch fürs Frontend-Polling (WageStatusDto). */
    public double getEffectiveInterestRate(String userId) {
        double reduction = skillTreeService.getEffectTotal(userId, EffectType.WAGE_INTEREST_REDUCTION, null);
        return Math.max(balance.getDebtInterestRateFloor(), balance.getDebtInterestRate() - reduction);
    }

    /** Aktuelle Dispo-Grenze (aktueller Gesamtlohn × balance.debtLimitMultiplier) --
     * auch fürs Frontend-Polling (WageStatusDto), siehe deductWageForUser. */
    public double getDebtLimit(String userId) {
        return balance.getDebtLimitMultiplier() * buildingService.getTotalWage(userId);
    }

    @Transactional
    public void deductWageForUser(String userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        boolean wasIdle = user.isWorkersIdle();
        boolean cookiesChanged = false;

        // Dispo-Zinsen auf bestehende Schulden -- laeuft JEDEN Tick, unabhaengig vom aktuellen
        // Lohn (auch wer gerade keine Arbeiter zugewiesen hat, muss seinen Dispo abbezahlen).
        if (user.getCookies() < 0) {
            double rate = getEffectiveInterestRate(userId);
            user.setCookies(user.getCookies() * (1 + rate));
            cookiesChanged = true;
        }

        Map<String, Double> breakdown = buildingService.getWageBreakdown(userId);
        double wage = breakdown.values().stream().mapToDouble(Double::doubleValue).sum();
        boolean nowIdle;
        if (wage <= 0) {
            nowIdle = false;
        } else {
            // Dispo statt Idle-Sperre: Cookies duerfen bis zur Dispo-Grenze ins Minus rutschen,
            // Arbeiter laufen dabei normal weiter. Erst darueber greift die alte Idle-Sperre.
            double debtLimit = balance.getDebtLimitMultiplier() * wage;
            double tentative = user.getCookies() - wage;
            if (tentative >= -debtLimit) {
                user.setCookies(tentative);
                cookiesChanged = true;
                nowIdle = false;
                // Nur bei tatsaechlicher Abbuchung setzen (nicht im Idle-Fall) --
                // Frontend pollt das fuer die fallende rote Zahl am Cookie-HUD.
                user.setLastWageAmount(wage);
                user.setLastWageAt(LocalDateTime.now());
                recordLedgerEntry(userId, wage, breakdown);
            } else {
                nowIdle = true;
            }
        }

        if (nowIdle == wasIdle && !cookiesChanged) return;

        // Passive Gebäude-Produktion settlen, bevor der Idle-Status wechselt -- sonst würde
        // die Zeitspanne VOR dem Wechsel fälschlich mit dem NEUEN Status bewertet (siehe
        // BuildingService#settleAllForIdleTransition). Kein eigener Scheduler dafür nötig,
        // läuft im ohnehin vorhandenen 60s-Lohnlauf mit.
        if (nowIdle != wasIdle) {
            buildingService.settleAllForIdleTransition(userId, wasIdle);
        }

        user.setWorkersIdle(nowIdle);
        userRepository.save(user);
    }

    /** Schreibt einen Abrechnungshistorie-Eintrag und löscht danach alles über dem Cap
     * (`balance.wageLedgerMaxEntries`) -- alte Einträge werden hart gelöscht statt die
     * Granularität zu vergröbern (siehe Plan-Entscheidung "alte löschen ist okay"). */
    private void recordLedgerEntry(String userId, double wage, Map<String, Double> breakdown) {
        WageLedgerEntity entry = new WageLedgerEntity();
        entry.setId(UUID.randomUUID().toString());
        entry.setUserId(userId);
        entry.setTotalAmount(wage);
        try {
            entry.setBreakdownJson(mapper.writeValueAsString(breakdown));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize wage breakdown for {}", userId, e);
            entry.setBreakdownJson("{}");
        }
        wageLedgerRepository.save(entry);

        int max = balance.getWageLedgerMaxEntries();
        List<WageLedgerEntity> newest = wageLedgerRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, max));
        if (newest.size() == max) {
            LocalDateTime cutoff = newest.get(newest.size() - 1).getCreatedAt();
            wageLedgerRepository.deleteByUserIdAndCreatedAtBefore(userId, cutoff);
        }
    }

    /** Abrechnungshistorie fürs Rathaus, neueste zuerst -- siehe BuildingController#getWageHistory. */
    public List<WageLedgerEntryDto> getWageHistory(String userId, int limit) {
        int cappedLimit = Math.min(limit, balance.getWageLedgerMaxEntries());
        return wageLedgerRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, cappedLimit))
                .stream()
                .map(this::toDto)
                .toList();
    }

    private WageLedgerEntryDto toDto(WageLedgerEntity entity) {
        WageLedgerEntryDto dto = new WageLedgerEntryDto();
        dto.setId(entity.getId());
        dto.setTotalAmount(entity.getTotalAmount());
        dto.setCreatedAtEpochMs(entity.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        try {
            dto.setBreakdown(mapper.readValue(entity.getBreakdownJson(), new TypeReference<Map<String, Double>>() {}));
        } catch (Exception e) {
            log.error("Failed to deserialize wage breakdown for ledger entry {}", entity.getId(), e);
            dto.setBreakdown(Collections.emptyMap());
        }
        return dto;
    }
}
