package md.utm.faf.pad.game.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/** JSON shapes for the game session endpoints. */
public final class SessionDtos {

    private SessionDtos() {}

    /** POST /api/lobbies/{lobby_id}/start */
    public record StartSessionRequest(
            @NotNull @Min(20) @Max(3600) Integer cycleDurationSeconds,
            @NotNull @Min(1) @Max(30) Integer semesterLengthCycles
    ) {}

    public record StartSessionResponse(
            String sessionId,
            String lobbyId,
            String cycle,
            int cycleNumber,
            String mapId,
            Instant startedAt
    ) {}

    /** GET /api/sessions/{session_id} */
    public record SessionStateResponse(
            String sessionId,
            String status,
            String cycle,
            int cycleNumber,
            Instant cycleEndsAt,
            int aliveZombies,
            List<PlayerStateResponse> players
    ) {}

    public record PlayerStateResponse(String playerId, int health, String currentRoomId) {}

    /** POST /api/sessions/{session_id}/end */
    public record EndSessionResponse(
            String sessionId,
            String status,
            List<String> survivors,
            int cyclesSurvived
    ) {}
}
