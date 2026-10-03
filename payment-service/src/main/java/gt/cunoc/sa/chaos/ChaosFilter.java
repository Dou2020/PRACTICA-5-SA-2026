package gt.cunoc.sa.chaos;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Inyecta el fallo técnico antes de llegar al controller, así ningún servicio
 * de negocio sabe que existe el chaos.
 *
 * El WARN por request afectado es la evidencia del video: con el Circuit
 * Breaker abierto, estas líneas dejan de aparecer porque el orquestador ya ni
 * siquiera llama.
 */
@Component
public class ChaosFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ChaosFilter.class);

    private final ChaosState state;

    public ChaosFilter(ChaosState state) {
        this.state = state;
    }

    /**
     * /chaos queda afuera o con ERROR activo no habría cómo apagarlo.
     * /actuator queda afuera para que el healthcheck de Docker no reinicie ni
     * marque unhealthy al contenedor: el fallo lo tiene que detectar el
     * Circuit Breaker, no Docker.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/chaos") || path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ChaosState.Config config = state.get();
        switch (config.mode()) {
            case ERROR -> {
                log.warn("CHAOS ERROR → 503 en {} {}", request.getMethod(), request.getRequestURI());
                response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"error\":\"CHAOS\",\"message\":\"Fallo técnico simulado\"}");
                return;
            }
            case SLOW -> {
                log.warn("CHAOS SLOW → {} ms en {} {}", config.delayMs(), request.getMethod(), request.getRequestURI());
                try {
                    Thread.sleep(config.delayMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            case NONE -> {
            }
        }
        chain.doFilter(request, response);
    }
}
