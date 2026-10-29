package md.utm.faf.pad.game.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import md.utm.faf.pad.game.client.ResourceServiceClient.Reward;

import java.time.Instant;
import java.util.List;

/** JSON shapes for timed actions. */
public final class ActionDtos {

    private ActionDtos() {}

    /** POST /api/game/lobbies/{lobby_id}/actions */
    public record StartActionRequest(
            @NotBlank String playerId,
            @NotBlank String actionType,
            @NotBlank String target,
            @NotNull @Positive @Max(3600) Integer durationSeconds
    ) {}

    /** 202 Accepted: the action runs in the background, poll or listen on WebSocket for progress. */
    public record ActionStartedResponse(
            String actionId,
            String status,
            Instant startedAt,
            Instant completesAt
    ) {}

    /** GET /api/sessions/{session_id}/actions/{action_id} */
    public record ActionStatusResponse(
            String actionId,
            String playerId,
            String actionType,
            String target,
            String status,
            int percent,
            long elapsedSeconds,
            Instant startedAt,
            Instant completesAt,
            List<Reward> reward   // only present when status = completed
    ) {}
}
