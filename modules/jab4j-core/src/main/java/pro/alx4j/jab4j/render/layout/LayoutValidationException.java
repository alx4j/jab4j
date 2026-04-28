package pro.alx4j.jab4j.render.layout;

/**
 * Raised when a layout profile cannot be rendered safely under the milestone-one fixed layout rules.
 */
public final class LayoutValidationException extends IllegalArgumentException {

    /**
     * Creates a new layout validation failure.
     *
     * @param message failure summary
     */
    public LayoutValidationException(String message) {
        super(message);
    }
}
