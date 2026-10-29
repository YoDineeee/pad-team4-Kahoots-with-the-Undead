package md.utm.faf.pad.player.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** JSON shapes for the profile and XP endpoints. */
public final class ProfileDtos {

    private ProfileDtos() {}

    /** GET /api/players/{player_id} */
    public record ProfileResponse(
            String playerId,
            String username,
            int level,
            int xp,
            String university,
            String title,
            String avatar
    ) {}

    /** PATCH /api/players/{player_id} - every field is optional, null means "don't change". */
    public record UpdateProfileRequest(
            @Size(max = 50) String title,
            @Size(max = 50) String avatar
    ) {}

    /** GET /api/players/{player_id}/xp */
    public record XpResponse(String playerId, int xp, int level, int xpToNextLevel) {}

    /** PATCH /api/players/{player_id}/xp */
    public record UpdateXpRequest(
            @NotBlank String operationId,
            @NotNull Integer delta,
            @NotBlank String reason,
            String sourceService
    ) {}

    public record UpdateXpResponse(String playerId, int xp, int level, boolean leveledUp) {}
}
