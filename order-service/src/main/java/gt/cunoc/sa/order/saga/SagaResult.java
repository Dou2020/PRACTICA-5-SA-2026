package gt.cunoc.sa.order.saga;

import org.springframework.http.HttpStatusCode;

import gt.cunoc.sa.order.domain.Order;

/** La orden como quedó y el status HTTP que le corresponde a cómo terminó la saga. */
public record SagaResult(Order order, HttpStatusCode status) {
}
