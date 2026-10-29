package md.utm.faf.pad.game.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import md.utm.faf.pad.game.exception.ApiException;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Runs BEFORE every controller method.
 *
 * If the method is annotated with {@link RequiresAuth}:
 *   1. read the "Authorization: Bearer <token>" header
 *   2. validate the token
 *   3. store the player id in the request, so the controller can read it with
 *      {@code @RequestAttribute(AuthInterceptor.CALLER_ID) String callerId}
 *
 * We use a simple interceptor instead of full Spring Security to keep the lab easy to follow.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String CALLER_ID = "callerPlayerId";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public AuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        boolean isProtected = handler instanceof HandlerMethod method
                && method.hasMethodAnnotation(RequiresAuth.class);
        if (!isProtected) {
            return true; // public endpoint, let it through
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw ApiException.unauthorized("MISSING_TOKEN", "Header 'Authorization: Bearer <jwt>' is required");
        }

        String token = header.substring(BEARER_PREFIX.length());
        String playerId = jwtService.extractPlayerId(token);
        request.setAttribute(CALLER_ID, playerId);
        return true;
    }
}
