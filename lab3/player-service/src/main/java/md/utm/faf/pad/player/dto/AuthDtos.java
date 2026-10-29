package md.utm.faf.pad.player.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * JSON shapes for registration and login.
 * Java field "playerId" is sent as "player_id" thanks to the SNAKE_CASE setting
 * in application.properties.
 */
public final class AuthDtos {

    private AuthDtos() {}

    /** POST /api/players/register */
    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 30) String username,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, message = "must have at least 6 characters") String password
    ) {}

    public record RegisterResponse(String playerId, String username, int level, int xp) {}

    /** POST /api/players/login */
    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}

    public record LoginResponse(String jwt, String playerId, List<String> roles) {}
}
