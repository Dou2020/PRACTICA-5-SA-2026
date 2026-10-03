package gt.cunoc.sa.shipping.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import gt.cunoc.sa.shipping.dto.ScheduleRequest;
import gt.cunoc.sa.shipping.dto.ScheduleResult;
import gt.cunoc.sa.shipping.dto.ShipmentResponse;
import gt.cunoc.sa.shipping.service.ShippingService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/shipping")
public class ShippingController {

    private final ShippingService service;

    public ShippingController(ShippingService service) {
        this.service = service;
    }

    @PostMapping("/schedule")
    public ResponseEntity<ShipmentResponse> schedule(@Valid @RequestBody ScheduleRequest request) {
        ScheduleResult result = service.schedule(request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.shipment());
    }

    /** GET /shipping?orderId=X — para que el orquestador compense un envío del que no supo el id. */
    @GetMapping(params = "orderId")
    public ShipmentResponse findByOrder(@RequestParam Long orderId) {
        return service.findByOrder(orderId);
    }

    /**
     * DELETE que responde 200 con cuerpo (no 204): el orquestador registra en
     * el timeline el estado final del envío compensado.
     */
    @DeleteMapping("/{id}")
    public ShipmentResponse cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}
