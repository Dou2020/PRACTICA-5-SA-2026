package gt.cunoc.sa.order.client;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/**
 * Timeouts de las llamadas a los servicios hoja — el patrón Timeout del
 * enunciado. Sin ellos, una dependencia colgada retendría el hilo del request
 * indefinidamente y el Circuit Breaker nunca llegaría a ver un fallo.
 *
 * Se usa el HttpClient del JDK y no el SimpleClientHttpRequestFactory porque
 * distingue por TIPO el timeout de conexión (HttpConnectTimeoutException: el
 * request no salió) del de respuesta (HttpTimeoutException: salió y no se sabe
 * si se ejecutó). Con el otro habría que distinguirlos por el texto del mensaje.
 * Esa diferencia es la que decide si la saga compensa el paso que falló.
 */
@Configuration
public class ClientConfig {

    @Bean
    public JdkClientHttpRequestFactory remoteRequestFactory(
            @Value("${clients.connect-timeout:1s}") Duration connectTimeout,
            @Value("${clients.read-timeout:2s}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                // Sin esto intenta un upgrade a HTTP/2 (h2c) en cada request a Tomcat.
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}
