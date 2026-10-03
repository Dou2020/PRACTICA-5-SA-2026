package gt.cunoc.sa.inventory.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import gt.cunoc.sa.inventory.dto.ProductResponse;
import gt.cunoc.sa.inventory.dto.ReleaseRequest;
import gt.cunoc.sa.inventory.dto.ReleaseResponse;
import gt.cunoc.sa.inventory.dto.ReservationResponse;
import gt.cunoc.sa.inventory.dto.ReserveRequest;
import gt.cunoc.sa.inventory.dto.ReserveResult;
import gt.cunoc.sa.inventory.service.InventoryService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @PostMapping("/reserve")
    public ResponseEntity<ReservationResponse> reserve(@Valid @RequestBody ReserveRequest request) {
        ReserveResult result = service.reserve(request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.reservation());
    }

    @PostMapping("/release")
    public ReleaseResponse release(@Valid @RequestBody ReleaseRequest request) {
        return service.release(request);
    }

    @GetMapping("/products")
    public List<ProductResponse> products() {
        return service.products();
    }
}
