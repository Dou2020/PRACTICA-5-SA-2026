package gt.cunoc.sa.chaos;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** GET /chaos devuelve el modo actual; POST /chaos lo cambia. */
@RestController
@RequestMapping("/chaos")
public class ChaosController {

    public record ChaosRequest(@NotNull ChaosMode mode, @PositiveOrZero Long delayMs) {
    }

    private final ChaosState state;

    public ChaosController(ChaosState state) {
        this.state = state;
    }

    @GetMapping
    public ChaosState.Config get() {
        return state.get();
    }

    @PostMapping
    public ChaosState.Config set(@Valid @RequestBody ChaosRequest request) {
        long delay = request.delayMs() != null ? request.delayMs() : 3000;
        return state.set(request.mode(), delay);
    }
}
