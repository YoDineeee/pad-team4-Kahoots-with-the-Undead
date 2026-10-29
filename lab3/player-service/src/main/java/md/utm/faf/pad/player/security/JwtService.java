package md.utm.faf.pad.player.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import md.utm.faf.pad.player.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Creates and checks JSON Web Tokens.
 *
 * A JWT has 3 parts: header.payload.signature
 *   - payload ("claims"): who the token belongs to (subject = player id), roles, expiry date
 *   - signature: HMAC-SHA256 of header+payload with our secret key
 * Anyone can READ the payload, but nobody can CHANGE it without knowing the secret,
 * because the signature would not match anymore.
 *
 * The Game Service gets the same JWT_SECRET, so it can validate tokens issued here
 * without calling the Player Service on every request.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration validity;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.expiration-minutes}") long expirationMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validity = Duration.ofMinutes(expirationMinutes);
    }

    public String generateToken(String playerId, List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(playerId)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validity)))
                .signWith(key)
                .compact();
    }

    /** Returns the player id stored in the token, or throws 401 if the token is invalid/expired. */
    public String extractPlayerId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            throw ApiException.unauthorized("INVALID_TOKEN", "JWT is invalid or expired");
        }
    }
}
