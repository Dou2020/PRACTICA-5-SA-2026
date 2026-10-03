package gt.cunoc.sa.web;

/** El recurso pedido por id no existe. Se responde 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
