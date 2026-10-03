package gt.cunoc.sa.order.dto;

import java.time.Instant;

import gt.cunoc.sa.order.domain.SagaAction;
import gt.cunoc.sa.order.domain.SagaOutcome;
import gt.cunoc.sa.order.domain.SagaStep;
import gt.cunoc.sa.order.domain.SagaStepName;

public record SagaStepResponse(SagaStepName step, SagaAction action, SagaOutcome outcome, String detail,
                               Instant createdAt) {

    public static SagaStepResponse of(SagaStep s) {
        return new SagaStepResponse(s.getStep(), s.getAction(), s.getOutcome(), s.getDetail(), s.getCreatedAt());
    }
}
