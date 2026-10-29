package md.utm.faf.pad.game.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** JSON shapes for the lobby endpoints (snake_case is applied automatically). */
public final class LobbyDtos {

    private LobbyDtos() {}

    /** POST /api/game/lobbies */
    public record CreateLobbyRequest(
            @NotBlank String hostId,
            @NotBlank String university,
            @NotNull @Min(1) @Max(16) Integer maxPlayers,
            @NotBlank String name
    ) {}

    /** POST /api/game/lobbies/{lobby_id}/join  and  /leave */
    public record PlayerRefRequest(@NotBlank String playerId) {}

    /** Returned by create / get / join / leave. */
    public record LobbyResponse(
            String lobbyId,
            String name,
            String hostId,
            String university,
            int maxPlayers,
            int dayCycle,
            String phase,
            List<String> players,
            String sessionId
    ) {}
}
