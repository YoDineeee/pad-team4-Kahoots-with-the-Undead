package md.utm.faf.pad.game.exception;

import org.springframework.http.HttpStatus;

/**
 * The single exception type our services throw for "expected" errors.
 * It carries the HTTP status and a machine-readable code from the contract,
 * e.g. 409 + "EMAIL_ALREADY_USED". {@link GlobalExceptionHandler} turns it into JSON.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    // Small factory methods so the services read like sentences:
    //   throw ApiException.notFound("PLAYER_NOT_FOUND", "...");

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException unauthorized(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
    }

    public static ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message);
    }

    public static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
