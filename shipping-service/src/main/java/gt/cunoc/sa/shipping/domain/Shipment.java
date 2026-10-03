package gt.cunoc.sa.shipping.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "shipments")
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(nullable = false)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShipmentStatus status;

    @Column(name = "tracking_code")
    private String trackingCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Shipment() {
    }

    public static Shipment schedule(Long orderId, String address) {
        Shipment s = new Shipment();
        s.orderId = orderId;
        s.address = address;
        s.status = ShipmentStatus.SCHEDULED;
        s.createdAt = Instant.now();
        s.updatedAt = s.createdAt;
        return s;
    }

    /** El código se deriva del id, que recién existe después del primer save. */
    public void assignTracking() {
        trackingCode = "SHP-" + id;
    }

    /** Devuelve true si cambió algo; cancelar dos veces no hace nada. */
    public boolean cancel() {
        if (status == ShipmentStatus.CANCELLED) {
            return false;
        }
        status = ShipmentStatus.CANCELLED;
        updatedAt = Instant.now();
        return true;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public String getAddress() { return address; }
    public ShipmentStatus getStatus() { return status; }
    public String getTrackingCode() { return trackingCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
