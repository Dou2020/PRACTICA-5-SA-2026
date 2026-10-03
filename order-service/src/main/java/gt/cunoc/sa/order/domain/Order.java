package gt.cunoc.sa.order.domain;

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
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String customer;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "reservation_id")
    private Long reservationId;

    @Column(name = "shipment_id")
    private Long shipmentId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Order() {
    }

    public static Order create(String customer, String sku, int quantity, BigDecimal amount, String address) {
        Order o = new Order();
        o.customer = customer;
        o.sku = sku;
        o.quantity = quantity;
        o.amount = amount;
        o.address = address;
        o.status = OrderStatus.PENDING;
        o.createdAt = Instant.now();
        o.updatedAt = o.createdAt;
        return o;
    }

    public void paid(Long paymentId) {
        this.paymentId = paymentId;
        touch();
    }

    public void reserved(Long reservationId) {
        this.reservationId = reservationId;
        touch();
    }

    public void shipped(Long shipmentId) {
        this.shipmentId = shipmentId;
        touch();
    }

    public void complete() {
        status = OrderStatus.COMPLETED;
        touch();
    }

    /**
     * Compensación del paso Order. Devuelve true si cambió algo; cancelar una
     * orden ya cancelada no hace nada (idempotente, igual que las
     * compensaciones de los servicios hoja). Quien llama verifica antes que no
     * esté COMPLETED.
     */
    public boolean cancel(String code, String reason) {
        if (status == OrderStatus.CANCELLED) {
            return false;
        }
        status = OrderStatus.CANCELLED;
        if (failureCode == null) {
            failureCode = code;
            failureReason = reason;
        }
        touch();
        return true;
    }

    /** El motivo original se registra al fallar el paso, antes de compensar. */
    public void failed(String code, String reason) {
        failureCode = code;
        failureReason = reason;
        touch();
    }

    public void compensationFailed() {
        status = OrderStatus.COMPENSATION_FAILED;
        touch();
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getCustomer() { return customer; }
    public String getSku() { return sku; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getAmount() { return amount; }
    public String getAddress() { return address; }
    public OrderStatus getStatus() { return status; }
    public String getFailureCode() { return failureCode; }
    public String getFailureReason() { return failureReason; }
    public Long getPaymentId() { return paymentId; }
    public Long getReservationId() { return reservationId; }
    public Long getShipmentId() { return shipmentId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
