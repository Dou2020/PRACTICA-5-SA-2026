package gt.cunoc.sa.order.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Una fila del log de la saga. Inmutable: el timeline solo se agrega, no se edita. */
@Entity
@Table(name = "saga_steps")
public class SagaStep {

    /** Corta a la longitud de la columna: un mensaje remoto largo no debe tumbar el registro. */
    private static final int DETAIL_MAX = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SagaStepName step;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SagaAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SagaOutcome outcome;

    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SagaStep() {
    }

    public static SagaStep of(Long orderId, SagaStepName step, SagaAction action, SagaOutcome outcome,
                              String detail) {
        SagaStep s = new SagaStep();
        s.orderId = orderId;
        s.step = step;
        s.action = action;
        s.outcome = outcome;
        s.detail = detail != null && detail.length() > DETAIL_MAX ? detail.substring(0, DETAIL_MAX) : detail;
        s.createdAt = Instant.now();
        return s;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public SagaStepName getStep() { return step; }
    public SagaAction getAction() { return action; }
    public SagaOutcome getOutcome() { return outcome; }
    public String getDetail() { return detail; }
    public Instant getCreatedAt() { return createdAt; }
}
