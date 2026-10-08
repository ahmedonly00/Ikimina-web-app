package rw.ikimina.shared.money;

/** An amount arrived in a form {@link Money#parse(String)} does not accept. */
public class MoneyFormatException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public MoneyFormatException(String message) {
        super(message);
    }
}
