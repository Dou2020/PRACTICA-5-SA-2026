package gt.cunoc.sa.payment.service;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gt.cunoc.sa.payment.domain.Payment;
import gt.cunoc.sa.payment.dto.ChargeRequest;
import gt.cunoc.sa.payment.dto.ChargeResult;
import gt.cunoc.sa.payment.dto.PaymentResponse;
import gt.cunoc.sa.payment.repository.PaymentRepository;
import gt.cunoc.sa.web.BusinessException;
import gt.cunoc.sa.web.NotFoundException;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    /**
     * Regla de negocio de la demo: por encima de este monto el cobro se rechaza.
     * Existe para poder provocar un fallo en el PRIMER paso de la saga, donde
     * no hay nada que compensar.
     */
    static final BigDecimal LIMITE = new BigDecimal("1000");

    private final PaymentRepository repository;

    public PaymentService(PaymentRepository repository) {
        this.repository = repository;
    }

    /**
     * Idempotente por orderId. El orquestador reintenta (Fase 4) cuando no
     * recibe respuesta, y "no recibí respuesta" no significa "no se cobró": el
     * cobro pudo completarse y perderse solo la respuesta. Sin esto, cada
     * reintento cobraría otra vez. El UNIQUE de order_id es el respaldo en BD.
     */
    @Transactional
    public ChargeResult charge(ChargeRequest request) {
        var existente = repository.findByOrderId(request.orderId());
        if (existente.isPresent()) {
            log.info("Pago ya existente para orden {} → se devuelve sin cobrar de nuevo", request.orderId());
            return new ChargeResult(PaymentResponse.of(existente.get()), false);
        }
        if (request.amount().compareTo(LIMITE) > 0) {
            log.info("Pago rechazado para orden {}: monto {}", request.orderId(), request.amount());
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "PAYMENT_REJECTED",
                    "Pago rechazado: el monto " + request.amount() + " supera el límite de " + LIMITE);
        }
        Payment payment = repository.save(Payment.charge(request.orderId(), request.amount()));
        log.info("Cobro {} para orden {}: {}", payment.getId(), payment.getOrderId(), payment.getAmount());
        return new ChargeResult(PaymentResponse.of(payment), true);
    }

    /** Compensación. Idempotente: el orquestador puede reintentarla sin devolver dinero dos veces. */
    @Transactional
    public PaymentResponse refund(Long id) {
        Payment payment = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("No existe el pago " + id));
        if (payment.refund()) {
            log.info("Reembolso del pago {} (orden {})", id, payment.getOrderId());
        } else {
            log.info("Pago {} ya estaba reembolsado → sin cambios", id);
        }
        return PaymentResponse.of(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(Long id) {
        return repository.findById(id)
                .map(PaymentResponse::of)
                .orElseThrow(() -> new NotFoundException("No existe el pago " + id));
    }

    /**
     * Búsqueda por orden, para compensar un cobro incierto. Si el POST dio
     * timeout del lado del orquestador, el cobro pudo completarse tarde y el id
     * nunca le llegó: lo único que conoce es su orderId. Sin esto, el refund
     * (que va por id) no tendría cómo encontrar ese cobro.
     */
    @Transactional(readOnly = true)
    public PaymentResponse findByOrder(Long orderId) {
        return repository.findByOrderId(orderId)
                .map(PaymentResponse::of)
                .orElseThrow(() -> new NotFoundException("No hay pago para la orden " + orderId));
    }
}
