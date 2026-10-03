package gt.cunoc.sa.inventory.domain;

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
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Reservation() {
    }

    public static Reservation reserve(Long orderId, String sku, Integer quantity) {
        Reservation r = new Reservation();
        r.orderId = orderId;
        r.sku = sku;
        r.quantity = quantity;
        r.status = ReservationStatus.RESERVED;
        r.createdAt = Instant.now();
        r.updatedAt = r.createdAt;
        return r;
    }

    /** Devuelve true si cambió algo; liberar dos veces no devuelve stock dos veces. */
    public boolean release() {
        if (status == ReservationStatus.RELEASED) {
            return false;
        }
        status = ReservationStatus.RELEASED;
        updatedAt = Instant.now();
        return true;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public String getSku() { return sku; }
    public Integer getQuantity() { return quantity; }
    public ReservationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
