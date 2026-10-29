package md.utm.faf.pad.game.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import md.utm.faf.pad.game.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * The Game Service only VALIDATES tokens; the Player Service is the one that issues them.
 * Both services share the same JWT_SECRET, so a token signed by the Player Service
 * can be verified here without any network call.
 */
@Service
public class JwtService {

    private final SecretKey key;

    public JwtService(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** Returns the player id stored in the token, or throws 401 if the token is invalid/expired. */
    public String extractPlayerId(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            throw ApiException.unauthorized("INVALID_TOKEN", "JWT is invalid or expired");
        }
    }
}
