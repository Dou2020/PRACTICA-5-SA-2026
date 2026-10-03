package gt.cunoc.sa.payment.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import gt.cunoc.sa.payment.dto.ChargeRequest;
import gt.cunoc.sa.payment.dto.ChargeResult;
import gt.cunoc.sa.payment.dto.PaymentResponse;
import gt.cunoc.sa.payment.service.PaymentService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> charge(@Valid @RequestBody ChargeRequest request) {
        ChargeResult result = service.charge(request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.payment());
    }

    @PostMapping("/{id}/refund")
    public PaymentResponse refund(@PathVariable Long id) {
        return service.refund(id);
    }

    /** GET /payments?orderId=X — para que el orquestador compense un cobro del que no supo el id. */
    @GetMapping(params = "orderId")
    public PaymentResponse findByOrder(@RequestParam Long orderId) {
        return service.findByOrder(orderId);
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}
