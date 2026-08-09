package cookie.server.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Ein Eintrag pro tatsächlich abgebuchter Lohnzahlung (WageService#deductWageForUser) --
 * Grundlage für die Abrechnungshistorie im Rathaus-Dialog. breakdownJson ist eine
 * Map&lt;buildingId, amount&gt;, damit man nachvollziehen kann, was wie viel gekostet hat. */
@Entity
@Table(name = "wage_ledger")
public class WageLedgerEntity {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "total_amount")
    private double totalAmount;

    // Bewusst KEIN @Lob: bei Postgres mappt Hibernate ein @Lob String-Feld auf den
    // "oid"-Large-Object-Typ statt auf eine normale text-Spalte -- Lesen ausserhalb einer
    // expliziten Transaktion (z.B. das reine getWageHistory()) scheitert dann mit
    // "Large Objects may not be used in auto-commit mode". columnDefinition="text" umgeht
    // das komplett, ganz normale Spalte.
    @Column(name = "breakdown_json", columnDefinition = "text")
    private String breakdownJson;

    @Column(name = "created_at", columnDefinition = "timestamp default now()")
    private LocalDateTime createdAt = LocalDateTime.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }
    public String getBreakdownJson() { return breakdownJson; }
    public void setBreakdownJson(String breakdownJson) { this.breakdownJson = breakdownJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
