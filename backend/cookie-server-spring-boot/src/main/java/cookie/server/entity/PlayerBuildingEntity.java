package cookie.server.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "player_buildings",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "building_id"}))
public class PlayerBuildingEntity {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "building_id")
    private String buildingId;

    @Column(name = "level")
    private int level;

    // Optimistic Lock gegen Lost-Updates bei schnell aufeinanderfolgenden Collect-Requests
    // auf dasselbe Gebaeude (siehe docs/ROADMAP.md). Ohne das ueberschreibt die spaeter
    // committende Transaktion den pendingAmount-Stand der anderen einfach.
    @Version
    @Column(name = "version")
    private long version;

    @Column(name = "workers")
    private int workers = 1;

    // columnDefinition mit DEFAULT noetig, nicht nur der Java-Feld-Initializer: bei
    // ddl-auto=update auf einer schon befuellten Tabelle setzt ALTER TABLE...ADD COLUMN
    // ohne DB-Default die bestehenden Zeilen auf NULL, und Hibernate wirft beim Laden eine
    // IllegalArgumentException fuer das primitive double-Feld ("Can not set double field...
    // to null value"). Mit DEFAULT backfillt Postgres bestehende Zeilen beim ALTER TABLE
    // sofort mit, kein manuelles Migrationsskript noetig (siehe docs/ROADMAP.md Abschnitt 2
    // zur selben Problemklasse bei Spalten-Umbenennungen).

    /** Passive resource accrued in this building, waiting to be collected. */
    @Column(name = "pending_amount", columnDefinition = "double precision default 0", nullable = false)
    private double pendingAmount = 0;

    /** Accrual checkpoint for lazy passive-income settlement (see BuildingService#settle). */
    @Column(name = "last_settled_at", columnDefinition = "timestamp default now()")
    private LocalDateTime lastSettledAt = LocalDateTime.now();

    /** Zeitpunkt des letzten erfolgreichen Collect-Aufrufs (Anti-Spam-Cooldown, siehe
     * PassiveIncomeService#collectBuilding) -- bewusst getrennt von lastSettledAt, das bei
     * jeder Gebäude-Berührung (Read, Stufen-/Arbeiter-Änderung) mitläuft. */
    @Column(name = "last_collected_at")
    private LocalDateTime lastCollectedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getBuildingId() { return buildingId; }
    public void setBuildingId(String buildingId) { this.buildingId = buildingId; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public int getWorkers() { return workers; }
    public void setWorkers(int workers) { this.workers = workers; }
    public double getPendingAmount() { return pendingAmount; }
    public void setPendingAmount(double pendingAmount) { this.pendingAmount = pendingAmount; }
    public LocalDateTime getLastSettledAt() { return lastSettledAt; }
    public void setLastSettledAt(LocalDateTime lastSettledAt) { this.lastSettledAt = lastSettledAt; }
    public LocalDateTime getLastCollectedAt() { return lastCollectedAt; }
    public void setLastCollectedAt(LocalDateTime lastCollectedAt) { this.lastCollectedAt = lastCollectedAt; }
}
