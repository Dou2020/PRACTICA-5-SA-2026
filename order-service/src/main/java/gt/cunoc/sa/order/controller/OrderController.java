package gt.cunoc.sa.order.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import gt.cunoc.sa.order.dto.CreateOrderRequest;
import gt.cunoc.sa.order.dto.OrderResponse;
import gt.cunoc.sa.order.dto.OrderSummary;
import gt.cunoc.sa.order.saga.OrderSagaOrchestrator;
import gt.cunoc.sa.order.saga.SagaResult;
import gt.cunoc.sa.order.service.OrderService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderSagaOrchestrator saga;
    private final OrderService service;

    public OrderController(OrderSagaOrchestrator saga, OrderService service) {
        this.saga = saga;
        this.service = service;
    }

    /**
     * Ejecuta la saga completa y responde con la orden y su timeline, termine
     * como termine. El status dice cómo terminó: 201 COMPLETED · 409/422 el
     * "no" de negocio del servicio que falló · 503 caída técnica o circuito
     * abierto · 500 COMPENSATION_FAILED.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        SagaResult result = saga.run(request);
        return ResponseEntity.status(result.status()).body(service.get(result.order().getId()));
    }

    @GetMapping
    public List<OrderSummary> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    /**
     * Compensación del paso Order, la misma que usa la saga. 409 sobre una
     * orden COMPLETED (ver OrderService.cancel).
     */
    @DeleteMapping("/{id}")
    public OrderResponse cancel(@PathVariable Long id) {
        service.cancel(id, "CANCELLED_BY_USER", "Cancelada con DELETE /orders/" + id);
        return service.get(id);
    }
}
