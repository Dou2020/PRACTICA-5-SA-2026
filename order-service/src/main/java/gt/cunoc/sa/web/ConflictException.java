package gt.cunoc.sa.web;

/** La operación no aplica al estado actual del recurso. Se responde 409. */
public class ConflictException extends RuntimeException {

    private final String code;

    public ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
