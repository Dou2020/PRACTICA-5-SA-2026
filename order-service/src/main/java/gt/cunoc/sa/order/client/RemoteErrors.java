package gt.cunoc.sa.order.client;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.util.function.Supplier;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import gt.cunoc.sa.order.dto.RemoteDtos.ApiError;
import gt.cunoc.sa.order.saga.BusinessException;
import gt.cunoc.sa.order.saga.TechnicalException;

/**
 * Traduce lo que pasó en el cable a las dos excepciones que entiende la saga.
 * Es el único lugar que decide la frontera de la práctica:
 *
 * | Qué pasó                                   | Excepción            | ¿Se ejecutó?  |
 * |--------------------------------------------|----------------------|---------------|
 * | 4xx                                        | BusinessException    | no            |
 * | 5xx (incluye chaos ERROR)                  | TechnicalException   | no            |
 * | conexión rechazada / host / connect timeout| TechnicalException   | no            |
 * | timeout de respuesta / conexión cortada    | TechnicalException   | NO SE SABE    |
 *
 * Que un 5xx cuente como "no ejecutado" se apoya en que los servicios hoja
 * responden error solo con su transacción en rollback (o, con chaos, antes de
 * llegar al controller). Si se tratara como incierto, la compensación tendría
 * que consultar a un servicio que justo está respondiendo 503.
 */
@Component
public class RemoteErrors {

    private final ObjectMapper mapper;

    public RemoteErrors(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** RestClient con los timeouts comunes y el mapeo de status a excepciones. */
    public RestClient restClient(RestClient.Builder builder, ClientHttpRequestFactory factory,
                                 String baseUrl, String service) {
        return builder
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    throw fromStatus(service, response);
                })
                .build();
    }

    /** Ejecuta la llamada y traduce los errores de transporte (timeout, sin conexión). */
    public <T> T call(String service, Supplier<T> call) {
        try {
            return call.get();
        } catch (ResourceAccessException e) {
            throw fromTransport(service, e);
        }
    }

    private RuntimeException fromStatus(String service, ClientHttpResponse response) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        ApiError body = readError(response);
        String message = body != null && body.message() != null
                ? body.message()
                : service + " respondió " + status.value();
        if (status.is4xxClientError()) {
            String code = body != null && body.error() != null ? body.error() : "HTTP_" + status.value();
            return new BusinessException(status, code, message);
        }
        return new TechnicalException(service + " respondió " + status.value() + ": " + message, false, null);
    }

    private ApiError readError(ClientHttpResponse response) {
        try {
            return mapper.readValue(response.getBody(), ApiError.class);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private TechnicalException fromTransport(String service, ResourceAccessException e) {
        if (neverSent(e)) {
            return new TechnicalException("Sin conexión con " + service, false, e);
        }
        return new TechnicalException(service + " no respondió a tiempo: resultado incierto", true, e);
    }

    /**
     * true si hay certeza de que el request no llegó al servicio. Ante la duda
     * (cualquier otra IOException), incierto: compensar algo que no se ejecutó
     * es inofensivo porque las compensaciones son idempotentes; no compensar
     * algo que sí se ejecutó deja un cobro huérfano.
     */
    private static boolean neverSent(Throwable e) {
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (c instanceof HttpConnectTimeoutException
                    || c instanceof ConnectException
                    || c instanceof UnknownHostException
                    || c instanceof UnresolvedAddressException) {
                return true;
            }
        }
        return false;
    }
}
