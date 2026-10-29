package md.utm.faf.pad.player.controller;

import jakarta.validation.Valid;
import md.utm.faf.pad.player.dto.AuthDtos.LoginRequest;
import md.utm.faf.pad.player.dto.AuthDtos.LoginResponse;
import md.utm.faf.pad.player.dto.AuthDtos.RegisterRequest;
import md.utm.faf.pad.player.dto.AuthDtos.RegisterResponse;
import md.utm.faf.pad.player.dto.ProfileDtos.ProfileResponse;
import md.utm.faf.pad.player.dto.ProfileDtos.UpdateProfileRequest;
import md.utm.faf.pad.player.dto.ProfileDtos.UpdateXpRequest;
import md.utm.faf.pad.player.dto.ProfileDtos.UpdateXpResponse;
import md.utm.faf.pad.player.dto.ProfileDtos.XpResponse;
import md.utm.faf.pad.player.security.AuthInterceptor;
import md.utm.faf.pad.player.security.Ownership;
import md.utm.faf.pad.player.security.RequiresAuth;
import md.utm.faf.pad.player.service.PlayerService;
import md.utm.faf.pad.player.service.XpService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account, profile and XP endpoints.
 * Controllers are intentionally "thin": they only map HTTP <-> service calls.
 */
@RestController
@RequestMapping("/api/players")
public class PlayerController {

    private final PlayerService playerService;
    private final XpService xpService;

    public PlayerController(PlayerService playerService, XpService xpService) {
        this.playerService = playerService;
        this.xpService = xpService;
    }

    /** POST /api/players/register -> 201 Created */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return playerService.register(request);
    }

    /** POST /api/players/login -> 200 OK + JWT */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return playerService.login(request);
    }

    /** GET /api/players/{player_id} - public profile, any logged-in player may read it. */
    @RequiresAuth
    @GetMapping("/{playerId}")
    public ProfileResponse getProfile(@PathVariable String playerId) {
        return playerService.getProfile(playerId);
    }

    /** PATCH /api/players/{player_id} - only the owner may change the profile. */
    @RequiresAuth
    @PatchMapping("/{playerId}")
    public ProfileResponse updateProfile(@PathVariable String playerId,
                                         @RequestAttribute(AuthInterceptor.CALLER_ID) String callerId,
                                         @Valid @RequestBody UpdateProfileRequest request) {
        Ownership.requireSelf(callerId, playerId);
        return playerService.updateProfile(playerId, request);
    }

    /** DELETE /api/players/{player_id} -> 204 No Content */
    @RequiresAuth
    @DeleteMapping("/{playerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePlayer(@PathVariable String playerId,
                             @RequestAttribute(AuthInterceptor.CALLER_ID) String callerId) {
        Ownership.requireSelf(callerId, playerId);
        playerService.deletePlayer(playerId);
    }

    /** GET /api/players/{player_id}/xp */
    @GetMapping("/{playerId}/xp")
    public XpResponse getXp(@PathVariable String playerId) {
        return xpService.getXp(playerId);
    }

    /** PATCH /api/players/{player_id}/xp - internal, called by Game / Exam services. */
    @PatchMapping("/{playerId}/xp")
    public UpdateXpResponse updateXp(@PathVariable String playerId,
                                     @Valid @RequestBody UpdateXpRequest request) {
        return xpService.updateXp(playerId, request);
    }
}
