package gt.cunoc.sa.order.saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import gt.cunoc.sa.order.domain.SagaAction;
import gt.cunoc.sa.order.domain.SagaOutcome;
import gt.cunoc.sa.order.domain.SagaStep;
import gt.cunoc.sa.order.domain.SagaStepName;
import gt.cunoc.sa.order.repository.SagaStepRepository;

/**
 * Registra cada paso apenas ocurre, cada uno en su propia transacción (la de
 * save()). Si el proceso muere a mitad de la saga, lo que ya pasó queda escrito.
 */
@Component
public class SagaLog {

    private static final Logger log = LoggerFactory.getLogger(SagaLog.class);

    private final SagaStepRepository steps;

    public SagaLog(SagaStepRepository steps) {
        this.steps = steps;
    }

    public void record(Long orderId, SagaStepName step, SagaAction action, SagaOutcome outcome, String detail) {
        steps.save(SagaStep.of(orderId, step, action, outcome, detail));
        if (outcome == SagaOutcome.OK) {
            log.info("Saga orden {}: {} {} {} — {}", orderId, step, action, outcome, detail);
        } else {
            log.warn("Saga orden {}: {} {} {} — {}", orderId, step, action, outcome, detail);
        }
    }
}
