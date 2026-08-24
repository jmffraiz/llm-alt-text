package biz.netcentric.aialttext.exception;

/** An {@code AltTextGenerationException} is thrown when an error occurs during AI-powered alt text generation. */
public class AltTextGenerationException extends Exception {

    /** Constructs a new exception with the specified detail message.
     * 
     * @param message the detail message. */
    public AltTextGenerationException(String message) {
        super(message);
    }

    /** Constructs a new exception with the specified detail message and cause. This constructor is useful for exceptions that are little
     * more than wrappers for other throwable.
     *
     * @param message the detail message.
     * @param cause the cause. */
    public AltTextGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
