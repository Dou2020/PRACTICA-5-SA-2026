package gt.cunoc.sa.payment.domain;

import java.math.BigDecimal;
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
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {
    }

    public static Payment charge(Long orderId, BigDecimal amount) {
        Payment p = new Payment();
        p.orderId = orderId;
        p.amount = amount;
        p.status = PaymentStatus.CHARGED;
        p.createdAt = Instant.now();
        p.updatedAt = p.createdAt;
        return p;
    }

    /** Devuelve true si cambió algo; un segundo refund no hace nada. */
    public boolean refund() {
        if (status == PaymentStatus.REFUNDED) {
            return false;
        }
        status = PaymentStatus.REFUNDED;
        updatedAt = Instant.now();
        return true;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public BigDecimal getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
