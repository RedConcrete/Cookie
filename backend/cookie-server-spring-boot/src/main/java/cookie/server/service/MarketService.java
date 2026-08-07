package cookie.server.service;

import cookie.server.config.MarketConfig;
import cookie.server.dto.MarketDto;
import cookie.server.dto.MarketRequestDto;
import cookie.server.dto.UserInformationDto;
import cookie.server.entity.MarketEntity;
import cookie.server.entity.MarketSnapshotEntity;
import cookie.server.entity.MarketStockEntity;
import cookie.server.entity.UserEntity;
import cookie.server.enums.MarketAction;
import cookie.server.enums.ResourceName;
import cookie.server.repository.MarketRepository;
import cookie.server.repository.MarketSnapshotRepository;
import cookie.server.repository.MarketStockRepository;
import cookie.server.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
public class MarketService {
    private static final Logger logger = LoggerFactory.getLogger(MarketService.class);

    private final MarketRepository marketRepository;
    private final MarketSnapshotRepository snapshotRepository;
    private final MarketStockRepository marketStockRepository;
    private final UserRepository userRepository;
    private final MarketConfig marketConfig;
    private final cookie.server.handler.MarketWebSocketHandler webSocketHandler;
    private final BuildingService buildingService;

    private final ReentrantLock marketLock = new ReentrantLock();
    private final AtomicInteger updateCounter = new AtomicInteger(0);
    private static final int CLEANUP_INTERVAL = 100;
    private static final String STOCK_SINGLETON_ID = "SINGLETON";

    // Absolute Untergrenze fuer den Pool-Stock einer Ressource -- rein numerische
    // Sicherheitsbremse gegen Division durch (fast) Null, kein Gameplay-Deckel.
    private static final double STOCK_EPSILON = 0.01;

    public MarketService(MarketRepository marketRepository,
                         MarketSnapshotRepository snapshotRepository,
                         MarketStockRepository marketStockRepository,
                         UserRepository userRepository, MarketConfig marketConfig,
                         cookie.server.handler.MarketWebSocketHandler webSocketHandler,
                         BuildingService buildingService) {
        this.marketRepository = marketRepository;
        this.snapshotRepository = snapshotRepository;
        this.marketStockRepository = marketStockRepository;
        this.userRepository = userRepository;
        this.marketConfig = marketConfig;
        this.webSocketHandler = webSocketHandler;
        this.buildingService = buildingService;
    }

    /** Alle 5 Minuten einen Preis-Snapshot speichern (persistente Langzeit-History). */
    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void saveSnapshot() {
        try {
            MarketEntity current = getOrCreateCurrentMarket();
            MarketSnapshotEntity snap = new MarketSnapshotEntity();
            snap.setId(UUID.randomUUID().toString());
            snap.setDate(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
            snap.setSugarPrice(current.getSugarPrice());
            snap.setFlourPrice(current.getFlourPrice());
            snap.setEggsPrice(current.getEggsPrice());
            snap.setButterPrice(current.getButterPrice());
            snap.setChocolatePrice(current.getChocolatePrice());
            snap.setMilkPrice(current.getMilkPrice());
            snapshotRepository.save(snap);
        } catch (Exception e) {
            logger.error("Snapshot save failed: {}", e.getMessage());
        }
    }

    /**
     * Komprimiert ältere Snapshots stündlich:
     * - 1–7 Tage alt → 1 Eintrag pro Stunde (Stundendurchschnitt)
     * - >7 Tage alt  → 1 Eintrag pro Tag   (Tagesdurchschnitt)
     * - >30 Tage alt → wird gelöscht
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void compressSnapshots() {
        try {
            LocalDateTime now        = LocalDateTime.now();
            LocalDateTime cutoff7d   = now.minusDays(7);
            LocalDateTime cutoff1d   = now.minusDays(1);
            LocalDateTime cutoff30d  = now.minusDays(30);

            // 1–7 Tage → Stundendurchschnitt
            compressBucket(cutoff7d, cutoff1d, java.time.temporal.ChronoUnit.HOURS);
            // >7 Tage → Tagesdurchschnitt
            compressBucket(cutoff30d, cutoff7d, java.time.temporal.ChronoUnit.DAYS);
            // >30 Tage → löschen
            snapshotRepository.deleteByDateBefore(cutoff30d);

            logger.info("Snapshot compression complete");
        } catch (Exception e) {
            logger.error("Snapshot compression failed: {}", e.getMessage());
        }
    }

    private void compressBucket(LocalDateTime from, LocalDateTime to, java.time.temporal.ChronoUnit unit) {
        List<MarketSnapshotEntity> entries = snapshotRepository.findBetween(from, to);
        if (entries.isEmpty()) return;

        // Gruppieren nach Zeit-Bucket
        java.util.Map<LocalDateTime, List<MarketSnapshotEntity>> groups = entries.stream()
                .collect(java.util.stream.Collectors.groupingBy(e -> e.getDate().truncatedTo(unit)));

        // Alte löschen
        snapshotRepository.deleteAll(entries);

        // Durchschnitt pro Bucket speichern
        List<MarketSnapshotEntity> compressed = groups.entrySet().stream().map(entry -> {
            List<MarketSnapshotEntity> g = entry.getValue();
            MarketSnapshotEntity avg = new MarketSnapshotEntity();
            avg.setId(UUID.randomUUID().toString());
            avg.setDate(entry.getKey());
            avg.setSugarPrice(    g.stream().mapToDouble(MarketSnapshotEntity::getSugarPrice).average().orElse(0));
            avg.setFlourPrice(    g.stream().mapToDouble(MarketSnapshotEntity::getFlourPrice).average().orElse(0));
            avg.setEggsPrice(     g.stream().mapToDouble(MarketSnapshotEntity::getEggsPrice).average().orElse(0));
            avg.setButterPrice(   g.stream().mapToDouble(MarketSnapshotEntity::getButterPrice).average().orElse(0));
            avg.setChocolatePrice(g.stream().mapToDouble(MarketSnapshotEntity::getChocolatePrice).average().orElse(0));
            avg.setMilkPrice(     g.stream().mapToDouble(MarketSnapshotEntity::getMilkPrice).average().orElse(0));
            return avg;
        }).collect(java.util.stream.Collectors.toList());

        snapshotRepository.saveAll(compressed);
        logger.debug("Compressed {} → {} entries for bucket {}", entries.size(), compressed.size(), unit);
    }

    /**
     * Aggregierte Gesamt-History für den Chart:
     * - Alle 5-min Snapshots (ältere History)
     * - Raw-Einträge der letzten Stunde (neueste, lückenlos)
     * Beide Quellen werden chronologisch zusammengeführt und dedupliziert.
     */
    public List<MarketDto> getFullHistory() {
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);

        // Snapshots (ältere Daten, vor einer Stunde)
        List<MarketDto> snapshots = snapshotRepository.findOlderThan(oneHourAgo)
                .stream()
                .map(s -> {
                    MarketEntity tmp = new MarketEntity();
                    tmp.setDate(s.getDate());
                    tmp.setSugarPrice(s.getSugarPrice());
                    tmp.setFlourPrice(s.getFlourPrice());
                    tmp.setEggsPrice(s.getEggsPrice());
                    tmp.setButterPrice(s.getButterPrice());
                    tmp.setChocolatePrice(s.getChocolatePrice());
                    tmp.setMilkPrice(s.getMilkPrice());
                    return new MarketDto(tmp);
                })
                .collect(Collectors.toList());

        // Raw-Daten der letzten Stunde (bereits newest-first, umkehren für chronologisch)
        List<MarketDto> recent = marketRepository.findAllByOrderByDateDesc(PageRequest.of(0, 1800))
                .stream()
                .filter(m -> m.getDate().isAfter(oneHourAgo))
                .map(MarketDto::new)
                .collect(Collectors.toList());
        java.util.Collections.reverse(recent);

        List<MarketDto> combined = new ArrayList<>(snapshots);
        combined.addAll(recent);
        return combined;
    }

    public List<MarketDto> getMarketData(int amount) {
        return marketRepository.findAllByOrderByDateDesc(PageRequest.of(0, amount))
                .stream()
                .map(MarketDto::new)
                .collect(Collectors.toList());
    }

    public List<MarketDto> getAllMarketData() {
        return marketRepository.findAllByOrderByDateDesc(PageRequest.of(0, 500))
                .stream()
                .map(MarketDto::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserInformationDto performAction(MarketRequestDto request) {
        UserEntity user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new NoSuchElementException("User not found: " + request.getUserId()));

        ResourceName resource = request.getResource().getName();
        double amount = request.getResource().getAmount();
        MarketAction action = request.getAction();

        // Negative/Null-Mengen wuerden Kosten/Auszahlung in der AMM-Formel umkehren
        // (negativer Kaufpreis -> Cookies aus dem Nichts, negativer Verkauf -> Ressourcen
        // aus dem Nichts). Muss serverseitig geprueft werden, Client-Wert nie vertrauen.
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive: " + amount);
        }

        // Kompletter Trade (Stock lesen, Kosten berechnen, Stock/Preis schreiben) laeuft unter
        // demselben marketLock wie createNewMarketEntryAfterTrade/applyRandomPriceFluctuation --
        // vorher wurde der Stock VOR dem Lock gelesen, wodurch zwei schnell aufeinanderfolgende
        // Trades denselben veralteten Stand als Rechengrundlage nahmen und der spaeter
        // committende Trade den Stock-Fortschritt des anderen ueberschrieb (Lost Update, siehe
        // docs/ROADMAP.md). ReentrantLock ist reentrant, der verschachtelte lock() in
        // createNewMarketEntryAfterTrade blockiert hier also nicht.
        marketLock.lock();
        try {
            MarketEntity currentMarket = getOrCreateCurrentMarket();
            MarketStockEntity stock = getOrCreateMarketStock();
            double marketStock = getStock(stock, resource);

            if (action == MarketAction.BUY) {
                // Ein Kauf kann den Pool nie ganz leerkaufen -- die Kosten explodieren vorher.
                if (amount >= marketStock) {
                    throw new IllegalArgumentException("Market out of stock for " + resource + ". Available: " + marketStock + ", Requested: " + amount);
                }
                double cap = buildingService.getTotalCap(request.getUserId());
                double freeSpace = cap - totalUserResources(user);
                if (amount > freeSpace) {
                    throw new IllegalArgumentException("Lager voll. Frei: " + freeSpace + ", Angefragt: " + amount);
                }
                double cost = buyCost(marketStock, resource, amount);
                if (user.getCookies() < cost) {
                    throw new IllegalArgumentException("Not enough cookies. Need: " + cost + ", Have: " + user.getCookies());
                }
                user.setCookies(user.getCookies() - cost);
                addResourceToUser(user, resource, amount);
                user.setLifetimeCookiesSpentOnMarket(user.getLifetimeCookiesSpentOnMarket() + cost);
            } else if (action == MarketAction.SELL) {
                double userAmount = getResourceFromUser(user, resource);
                if (userAmount < amount) {
                    throw new IllegalArgumentException("Not enough " + resource + ". Need: " + amount + ", Have: " + userAmount);
                }
                addResourceToUser(user, resource, -amount);
                double feeRate = buildingService.getEffectiveSellFeeRate(request.getUserId(), marketConfig.getSellFeeRate());
                double payout = sellPayout(marketStock, resource, amount) * (1.0 - feeRate);
                user.setCookies(user.getCookies() + payout);
                user.setLifetimeCookiesEarnedFromMarket(user.getLifetimeCookiesEarnedFromMarket() + payout);
            }

            userRepository.save(user);

            // Preis nach dem Trade anpassen und Stock aktualisieren
            createNewMarketEntryAfterTrade(currentMarket, stock, resource, amount, action);
        } finally {
            marketLock.unlock();
        }

        return toDto(user);
    }

    /**
     * Erstellt einen neuen Markteintrag nach einem Trade.
     * Aktualisiert sowohl Preis als auch Lagerbestand.
     */
    private void createNewMarketEntryAfterTrade(MarketEntity currentMarket, MarketStockEntity stock, ResourceName resource, double amount, MarketAction action) {
        marketLock.lock();
        try {
            double marketStock = getStock(stock, resource);

            // Stock bewegen, Preis rein aus der neuen Poolgroesse ableiten (price = K / stock).
            // Kein separater Impact-Multiplikator/Online-Spieler-Divisor mehr noetig: die Kurve
            // ist von Natur aus konvex, ein einzelner grosser Trade bewegt den Preis ueberproportional
            // staerker als viele kleine mit derselben Gesamtmenge -- "nur die Masse bewegt den Markt"
            // ergibt sich also automatisch aus der Formel selbst.
            double newStock = action == MarketAction.BUY ? marketStock - amount : marketStock + amount;
            newStock = Math.max(STOCK_EPSILON, newStock);
            double newPrice = spotPrice(newStock, resource);

            // Neuen Markteintrag fuer Preis-Historie erstellen
            MarketEntity newMarket = copyMarketWithNewId(currentMarket);
            setPrice(newMarket, resource, newPrice);
            newMarket.setDate(LocalDateTime.now());

            // Baseline anteilig mitziehen -- nur echte Trades duerfen das Rueckfall-Ziel des
            // Zufalls-Ticks verschieben, siehe applyTradeToBaseline.
            applyTradeToBaseline(stock, resource, marketStock, newStock);

            // Stock in separater Tabelle aktualisieren (ueberschreibt sich selbst)
            setStock(stock, resource, newStock);
            marketStockRepository.save(stock);

            // Validierung vor dem Speichern
            if (isValidMarket(newMarket)) {
                marketRepository.save(newMarket);
            } else {
                logger.warn("Invalid market entry detected after trade, skipping save");
            }

        } finally {
            marketLock.unlock();
        }
    }

    /**
     * Simuliert Hintergrund-Marktaktivitaet: bewegt den Pool-Stock jeder Ressource um einen
     * kleinen zufaelligen Betrag (Phantom-Kauf/-Verkauf, nicht von einem echten Spieler) und
     * leitet den neuen Preis aus der AMM-Kurve ab. Verzerrt Richtung einer pro Ressource
     * gefuehrten Baseline statt eines fixen Ausgangsbestands -- die Baseline wird ausschliesslich
     * von echten Trades verschoben ({@link #applyTradeToBaseline}) und zerfaellt von selbst
     * langsam zurueck auf initialStock ({@link #decayBaselineTowardAnchor}). So haelt der
     * Preis-Effekt anhaltenden Spieler-Tradings ueber die konfigurierte Zeitkonstante an, statt
     * dass er nach wenigen Minuten auf den Ausgangswert zurueckfaellt -- reines Hintergrundrauschen
     * kann die Baseline aber nie selbst bewegen, bleibt also unbegrenzt stabil.
     */
    @Transactional
    public void applyRandomPriceFluctuation() {
        marketLock.lock();
        try {
            MarketEntity currentMarket = getOrCreateCurrentMarket();
            MarketStockEntity stock = getOrCreateMarketStock();

            // Validiere dass currentMarket gueltige Preise hat
            if (!isValidMarket(currentMarket)) {
                logger.warn("Current market has invalid prices, reinitializing...");
                currentMarket = createInitialMarket();
            }

            MarketEntity newMarket = copyMarketWithNewId(currentMarket);

            for (ResourceName resource : ResourceName.values()) {
                double marketStock = getStock(stock, resource);
                double initialStock = getInitialStock(resource);

                // Baseline zuerst einen Schritt Richtung initialStock zerfallen lassen (reiner,
                // rauschfreier Anker-Zerfall -- die Baseline selbst wird nur von echten Trades
                // bewegt, siehe applyTradeToBaseline).
                double baseline = decayBaselineTowardAnchor(stock, resource);

                // Wahrscheinlichkeit fuer "Stock steigt" (Phantom-Verkauf) haengt sanft davon ab,
                // wie weit der aktuelle Stock von der Baseline entfernt ist -- ist er schon
                // knapp (relativ zur Baseline), ist ein Nachschub-Tick wahrscheinlicher als ein
                // weiterer Abfluss.
                double deviation = (baseline - marketStock) / initialStock;
                double increaseChance = 0.5 + Math.max(-0.4, Math.min(0.4, deviation * 0.5));
                double magnitude = Math.random() * marketConfig.getStockFluctuationRatio() * initialStock;

                double newStock;
                if (Math.random() < increaseChance) {
                    newStock = marketStock + magnitude;
                } else {
                    // Nie mehr als die Haelfte des aktuellen Bestands auf einen Schlag wegnehmen.
                    newStock = marketStock - Math.min(magnitude, marketStock * 0.5);
                }
                newStock = Math.max(STOCK_EPSILON, newStock);

                setStock(stock, resource, newStock);
                setPrice(newMarket, resource, spotPrice(newStock, resource));
            }
            marketStockRepository.save(stock);

            newMarket.setDate(LocalDateTime.now());

            // Validierung vor dem Speichern
            if (isValidMarket(newMarket)) {
                marketRepository.save(newMarket);
            } else {
                logger.error("Invalid market entry detected, creating fresh initial market");
                createInitialMarket();
            }

            if (updateCounter.incrementAndGet() >= CLEANUP_INTERVAL) {
                updateCounter.set(0);
                cleanupOldEntries(1800);
            }
            
            // Broadcast update
            webSocketHandler.broadcastMarketUpdate(getMarketData(20));
        } finally {
            marketLock.unlock();
        }
    }

    /**
     * Prueft ob ein MarketEntity gueltige Preise hat (keine 0-Werte).
     */
    private boolean isValidMarket(MarketEntity market) {
        return market != null &&
                market.getSugarPrice() > 0 &&
                market.getFlourPrice() > 0 &&
                market.getEggsPrice() > 0 &&
                market.getButterPrice() > 0 &&
                market.getChocolatePrice() > 0 &&
                market.getMilkPrice() > 0;
    }

    /**
     * Gibt den Initial-Preis fuer eine Ressource zurueck.
     */
    private double getInitialPrice(ResourceName resource) {
        return switch (resource) {
            case SUGAR -> marketConfig.getInitialSugarPrice();
            case FLOUR -> marketConfig.getInitialFlourPrice();
            case EGGS -> marketConfig.getInitialEggsPrice();
            case BUTTER -> marketConfig.getInitialButterPrice();
            case CHOCOLATE -> marketConfig.getInitialChocolatePrice();
            case MILK -> marketConfig.getInitialMilkPrice();
        };
    }

    /**
     * Gibt den Initial-Lagerbestand fuer eine Ressource zurueck.
     */
    private double getInitialStock(ResourceName resource) {
        return switch (resource) {
            case SUGAR -> marketConfig.getInitialSugarStock();
            case FLOUR -> marketConfig.getInitialFlourStock();
            case EGGS -> marketConfig.getInitialEggsStock();
            case BUTTER -> marketConfig.getInitialButterStock();
            case CHOCOLATE -> marketConfig.getInitialChocolateStock();
            case MILK -> marketConfig.getInitialMilkStock();
        };
    }

    // ── AMM-Preismodell (constant product, wie Uniswap) ──────────────────────
    // Der Markt haelt fuer jede Ressource einen Pool aus Ressourcen-Reserve (marketStock)
    // gegen eine VIRTUELLE Cookie-Reserve. Beide multipliziert ergeben die Konstante K,
    // kalibriert so, dass der Spotpreis bei Ausgangsbestand genau dem Ausgangspreis
    // entspricht: K = initialStock^2 * initialPrice.
    //
    // Preis ist rein aus dem aktuellen Stock abgeleitet (price = K / stock) -- kein
    // separater Clamp/Deckel noetig. Ein Kauf kann den Pool nie ganz leerkaufen (Kosten
    // gehen asymptotisch gegen unendlich, je naeher man an den kompletten Bestand kommt),
    // genau wie in einem echten liquiden Markt: es gibt kein "ausverkauft", nur "immer teurer".

    /** Konstante K des Pools fuer eine Ressource (bleibt ueber die Zeit fix). */
    private double poolConstant(ResourceName resource) {
        double s0 = getInitialStock(resource);
        double p0 = getInitialPrice(resource);
        return s0 * s0 * p0;
    }

    /** Aktueller Spotpreis fuer einen gegebenen Lagerbestand. */
    private double spotPrice(double stock, ResourceName resource) {
        double s = Math.max(stock, STOCK_EPSILON);
        // Spotpreis = virtuelle Cookie-Reserve / Stock = (K/stock) / stock = K/stock^2.
        return poolConstant(resource) / s / s;
    }

    /** Cookie-Kosten, um `amount` Einheiten aus dem Pool zu kaufen (Integral ueber die Kurve). */
    private double buyCost(double stock, ResourceName resource, double amount) {
        double k = poolConstant(resource);
        double newStock = stock - amount;
        return k / newStock - k / stock;
    }

    /** Cookie-Auszahlung (vor Gebuehr), um `amount` Einheiten in den Pool zu verkaufen. */
    private double sellPayout(double stock, ResourceName resource, double amount) {
        double k = poolConstant(resource);
        double newStock = stock + amount;
        return k / stock - k / newStock;
    }

    /**
     * Erstellt eine Kopie des MarketEntity mit neuer ID.
     * Stock wird nicht kopiert - dieser ist in separater Tabelle.
     */
    private MarketEntity copyMarketWithNewId(MarketEntity source) {
        MarketEntity copy = new MarketEntity();
        copy.setId(UUID.randomUUID().toString());

        // Kopiere Preise, aber nutze Initialpreise falls Source 0 hat
        copy.setSugarPrice(source.getSugarPrice() > 0 ? source.getSugarPrice() : marketConfig.getInitialSugarPrice());
        copy.setFlourPrice(source.getFlourPrice() > 0 ? source.getFlourPrice() : marketConfig.getInitialFlourPrice());
        copy.setEggsPrice(source.getEggsPrice() > 0 ? source.getEggsPrice() : marketConfig.getInitialEggsPrice());
        copy.setButterPrice(source.getButterPrice() > 0 ? source.getButterPrice() : marketConfig.getInitialButterPrice());
        copy.setChocolatePrice(source.getChocolatePrice() > 0 ? source.getChocolatePrice() : marketConfig.getInitialChocolatePrice());
        copy.setMilkPrice(source.getMilkPrice() > 0 ? source.getMilkPrice() : marketConfig.getInitialMilkPrice());

        return copy;
    }

    /**
     * Entfernt alte Markteintraege, behaelt nur die neuesten.
     */
    @Transactional
    protected void cleanupOldEntries(int keepCount) {
        try {
            long totalCount = marketRepository.count();
            if (totalCount > keepCount) {
                List<MarketEntity> allEntries = marketRepository.findAllByOrderByDateDesc(
                        PageRequest.of(0, (int) totalCount));
                if (allEntries.size() > keepCount) {
                    List<MarketEntity> toDelete = allEntries.subList(keepCount, allEntries.size());
                    marketRepository.deleteAll(toDelete);
                    logger.debug("Cleaned up {} old market entries", toDelete.size());
                }
            }
        } catch (Exception e) {
            logger.error("Error during cleanup: {}", e.getMessage());
        }
    }

    public double getCurrentPrice(cookie.server.enums.ResourceName resource) {
        return getPrice(getOrCreateCurrentMarket(), resource);
    }

    public double getSellFeeRate() {
        return marketConfig.getSellFeeRate();
    }

    /** Setzt Markt-Stock und -Preise alle auf die konfigurierten Ausgangswerte zurueck. */
    @Transactional
    public void resetMarket() {
        marketLock.lock();
        try {
            MarketStockEntity stock = getOrCreateMarketStock();
            stock.setSugarStock(marketConfig.getInitialSugarStock());
            stock.setFlourStock(marketConfig.getInitialFlourStock());
            stock.setEggsStock(marketConfig.getInitialEggsStock());
            stock.setButterStock(marketConfig.getInitialButterStock());
            stock.setChocolateStock(marketConfig.getInitialChocolateStock());
            stock.setMilkStock(marketConfig.getInitialMilkStock());
            resetBaselinesToStock(stock);
            marketStockRepository.save(stock);

            createInitialMarket();
            logger.info("Market manually reset to initial stock/prices");
        } finally {
            marketLock.unlock();
        }
    }

    public MarketEntity getOrCreateCurrentMarket() {
        List<MarketEntity> markets = marketRepository.findAllByOrderByDateDesc(PageRequest.of(0, 1));
        if (markets.isEmpty()) {
            return createInitialMarket();
        }

        MarketEntity latest = markets.get(0);
        // Falls der letzte Eintrag ungueltig ist, erstelle neuen
        if (!isValidMarket(latest)) {
            logger.warn("Latest market entry is invalid, creating new initial market");
            return createInitialMarket();
        }

        return latest;
    }

    private MarketEntity createInitialMarket() {
        MarketEntity market = new MarketEntity();
        market.setId(UUID.randomUUID().toString());
        market.setDate(LocalDateTime.now());
        market.setSugarPrice(marketConfig.getInitialSugarPrice());
        market.setFlourPrice(marketConfig.getInitialFlourPrice());
        market.setEggsPrice(marketConfig.getInitialEggsPrice());
        market.setButterPrice(marketConfig.getInitialButterPrice());
        market.setChocolatePrice(marketConfig.getInitialChocolatePrice());
        market.setMilkPrice(marketConfig.getInitialMilkPrice());

        logger.info("Created initial market with prices: Sugar={}, Flour={}, Eggs={}, Butter={}, Chocolate={}, Milk={}",
                market.getSugarPrice(), market.getFlourPrice(), market.getEggsPrice(),
                market.getButterPrice(), market.getChocolatePrice(), market.getMilkPrice());

        return marketRepository.save(market);
    }

    /**
     * Holt oder erstellt den MarketStock Singleton-Eintrag.
     */
    private MarketStockEntity getOrCreateMarketStock() {
        return marketStockRepository.findById(STOCK_SINGLETON_ID)
                .orElseGet(this::createInitialMarketStock);
    }

    /**
     * Erstellt den initialen MarketStock Eintrag.
     */
    private MarketStockEntity createInitialMarketStock() {
        MarketStockEntity stock = new MarketStockEntity();
        stock.setSugarStock(marketConfig.getInitialSugarStock());
        stock.setFlourStock(marketConfig.getInitialFlourStock());
        stock.setEggsStock(marketConfig.getInitialEggsStock());
        stock.setButterStock(marketConfig.getInitialButterStock());
        stock.setChocolateStock(marketConfig.getInitialChocolateStock());
        stock.setMilkStock(marketConfig.getInitialMilkStock());
        resetBaselinesToStock(stock);

        logger.info("Created initial market stock: Sugar={}, Flour={}, Eggs={}, Butter={}, Chocolate={}, Milk={}",
                stock.getSugarStock(), stock.getFlourStock(), stock.getEggsStock(),
                stock.getButterStock(), stock.getChocolateStock(), stock.getMilkStock());

        return marketStockRepository.save(stock);
    }

    /** Setzt die Baseline jeder Ressource auf ihren aktuellen Stock (Neuanlage/Reset). */
    private void resetBaselinesToStock(MarketStockEntity stock) {
        for (ResourceName resource : ResourceName.values()) {
            setBaseline(stock, resource, getStock(stock, resource));
        }
    }

    private double getPrice(MarketEntity market, ResourceName resource) {
        double price = switch (resource) {
            case SUGAR -> market.getSugarPrice();
            case FLOUR -> market.getFlourPrice();
            case EGGS -> market.getEggsPrice();
            case BUTTER -> market.getButterPrice();
            case CHOCOLATE -> market.getChocolatePrice();
            case MILK -> market.getMilkPrice();
        };
        // Fallback auf Initialpreis wenn 0
        return price > 0 ? price : getInitialPrice(resource);
    }

    private void setPrice(MarketEntity market, ResourceName resource, double price) {
        // Stelle sicher dass Preis nie 0 oder negativ ist
        double safePrice = Math.max(marketConfig.getMinPrice(), price);
        switch (resource) {
            case SUGAR -> market.setSugarPrice(safePrice);
            case FLOUR -> market.setFlourPrice(safePrice);
            case EGGS -> market.setEggsPrice(safePrice);
            case BUTTER -> market.setButterPrice(safePrice);
            case CHOCOLATE -> market.setChocolatePrice(safePrice);
            case MILK -> market.setMilkPrice(safePrice);
        }
    }

    private double getStock(MarketStockEntity stock, ResourceName resource) {
        return switch (resource) {
            case SUGAR -> stock.getSugarStock();
            case FLOUR -> stock.getFlourStock();
            case EGGS -> stock.getEggsStock();
            case BUTTER -> stock.getButterStock();
            case CHOCOLATE -> stock.getChocolateStock();
            case MILK -> stock.getMilkStock();
        };
    }

    private void setStock(MarketStockEntity stock, ResourceName resource, double amount) {
        double safeStock = Math.max(0, amount);
        switch (resource) {
            case SUGAR -> stock.setSugarStock(safeStock);
            case FLOUR -> stock.setFlourStock(safeStock);
            case EGGS -> stock.setEggsStock(safeStock);
            case BUTTER -> stock.setButterStock(safeStock);
            case CHOCOLATE -> stock.setChocolateStock(safeStock);
            case MILK -> stock.setMilkStock(safeStock);
        }
    }

    private double getBaseline(MarketStockEntity stock, ResourceName resource) {
        return switch (resource) {
            case SUGAR -> stock.getSugarStockBaseline();
            case FLOUR -> stock.getFlourStockBaseline();
            case EGGS -> stock.getEggsStockBaseline();
            case BUTTER -> stock.getButterStockBaseline();
            case CHOCOLATE -> stock.getChocolateStockBaseline();
            case MILK -> stock.getMilkStockBaseline();
        };
    }

    private void setBaseline(MarketStockEntity stock, ResourceName resource, double baseline) {
        double safeBaseline = Math.max(STOCK_EPSILON, baseline);
        switch (resource) {
            case SUGAR -> stock.setSugarStockBaseline(safeBaseline);
            case FLOUR -> stock.setFlourStockBaseline(safeBaseline);
            case EGGS -> stock.setEggsStockBaseline(safeBaseline);
            case BUTTER -> stock.setButterStockBaseline(safeBaseline);
            case CHOCOLATE -> stock.setChocolateStockBaseline(safeBaseline);
            case MILK -> stock.setMilkStockBaseline(safeBaseline);
        }
    }

    /**
     * Laesst die Baseline einer Ressource pro Zufalls-Tick ein Stueck Richtung initialStock
     * zerfallen (reiner Anker-Zerfall gegen einen fixen Zielwert, unbedingt stabil -- siehe
     * {@link MarketConfig#getStockBaselineTimeConstantSeconds()}). Die Baseline selbst wird nur
     * durch echte Trades verschoben ({@link #applyTradeToBaseline}), nie durch dieses Rauschen --
     * sonst koennte reines Hintergrundrauschen die Baseline unbegrenzt wegtreiben.
     *
     * @return die aktualisierte Baseline
     */
    private double decayBaselineTowardAnchor(MarketStockEntity stock, ResourceName resource) {
        double initialStock = getInitialStock(resource);
        double baseline = getBaseline(stock, resource);
        if (baseline <= 0) {
            baseline = initialStock;
        } else {
            double dtSeconds = marketConfig.getUpdateIntervalMs() / 1000.0;
            double tau = Math.max(1.0, marketConfig.getStockBaselineTimeConstantSeconds());
            double alpha = 1.0 - Math.exp(-dtSeconds / tau);
            baseline = baseline + alpha * (initialStock - baseline);
        }
        setBaseline(stock, resource, baseline);
        return baseline;
    }

    /**
     * Verschiebt die Baseline anteilig um die Stock-Aenderung eines echten Trades
     * ({@link MarketConfig#getStockBaselineTradeTransferRatio()}). Dadurch "merkt" sich der
     * Markt anhaltendes Kaufen/Verkaufen als neue vorlaeufige Gleichgewichtslage, statt dass der
     * Preis-Effekt sofort durch den naechsten Zufalls-Tick wieder wegkorrigiert wird -- klingt
     * ueber {@link #decayBaselineTowardAnchor} langsam von selbst wieder ab.
     */
    private void applyTradeToBaseline(MarketStockEntity stock, ResourceName resource, double oldStock, double newStock) {
        double baseline = getBaseline(stock, resource);
        if (baseline <= 0) {
            baseline = oldStock;
        }
        baseline = baseline + marketConfig.getStockBaselineTradeTransferRatio() * (newStock - oldStock);
        setBaseline(stock, resource, baseline);
    }

    private double totalUserResources(UserEntity user) {
        return user.getSugar() + user.getFlour() + user.getEggs()
             + user.getButter() + user.getChocolate() + user.getMilk();
    }

    private double getResourceFromUser(UserEntity user, ResourceName resource) {
        return switch (resource) {
            case SUGAR -> user.getSugar();
            case FLOUR -> user.getFlour();
            case EGGS -> user.getEggs();
            case BUTTER -> user.getButter();
            case CHOCOLATE -> user.getChocolate();
            case MILK -> user.getMilk();
        };
    }

    private void addResourceToUser(UserEntity user, ResourceName resource, double amount) {
        switch (resource) {
            case SUGAR -> user.setSugar(user.getSugar() + amount);
            case FLOUR -> user.setFlour(user.getFlour() + amount);
            case EGGS -> user.setEggs(user.getEggs() + amount);
            case BUTTER -> user.setButter(user.getButter() + amount);
            case CHOCOLATE -> user.setChocolate(user.getChocolate() + amount);
            case MILK -> user.setMilk(user.getMilk() + amount);
        }
    }

    private UserInformationDto toDto(UserEntity entity) {
        UserInformationDto dto = new UserInformationDto();
        dto.setSteamId(entity.getSteamId());
        dto.setDisplayName(entity.getDisplayName());
        dto.setAvatarUrl(UserService.avatarEndpointFor(entity));
        dto.setCookies(entity.getCookies());
        dto.setSugar(entity.getSugar());
        dto.setFlour(entity.getFlour());
        dto.setEggs(entity.getEggs());
        dto.setButter(entity.getButter());
        dto.setChocolate(entity.getChocolate());
        dto.setMilk(entity.getMilk());
        dto.setWorkersIdle(entity.isWorkersIdle());
        dto.setOwnedCitizens(entity.getOwnedCitizens());
        dto.setTotalResourceCap(buildingService.getTotalCap(entity.getSteamId()));
        return dto;
    }
}
