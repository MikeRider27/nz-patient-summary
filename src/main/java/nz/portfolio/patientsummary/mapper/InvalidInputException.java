package nz.portfolio.patientsummary.mapper;

/** Thrown when request content is syntactically valid JSON but clinically/semantically invalid. */
public class InvalidInputException extends RuntimeException {

    private final String field;

    public InvalidInputException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
