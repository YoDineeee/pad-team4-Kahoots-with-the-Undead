package md.utm.faf.pad.game.controller;

import jakarta.validation.Valid;
import md.utm.faf.pad.game.dto.LobbyDtos.CreateLobbyRequest;
import md.utm.faf.pad.game.dto.LobbyDtos.LobbyResponse;
import md.utm.faf.pad.game.dto.LobbyDtos.PlayerRefRequest;
import md.utm.faf.pad.game.security.AuthInterceptor;
import md.utm.faf.pad.game.security.Ownership;
import md.utm.faf.pad.game.security.RequiresAuth;
import md.utm.faf.pad.game.service.LobbyService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Lobby endpoints. Every call needs a JWT issued by the Player Service. */
@RestController
@RequestMapping("/api/game/lobbies")
public class LobbyController {

    private final LobbyService lobbyService;

    public LobbyController(LobbyService lobbyService) {
        this.lobbyService = lobbyService;
    }

    /** POST /api/game/lobbies -> 201 Created. You can only create a lobby with yourself as host. */
    @RequiresAuth
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LobbyResponse createLobby(@RequestAttribute(AuthInterceptor.CALLER_ID) String callerId,
                                     @Valid @RequestBody CreateLobbyRequest request) {
        Ownership.requireSelf(callerId, request.hostId());
        return lobbyService.createLobby(request);
    }

    /** GET /api/game/lobbies/{lobby_id} (extra read endpoint, handy for testing) */
    @RequiresAuth
    @GetMapping("/{lobbyId}")
    public LobbyResponse getLobby(@PathVariable String lobbyId) {
        return lobbyService.getLobby(lobbyId);
    }

    /** POST /api/game/lobbies/{lobby_id}/join */
    @RequiresAuth
    @PostMapping("/{lobbyId}/join")
    public LobbyResponse joinLobby(@PathVariable String lobbyId,
                                   @RequestAttribute(AuthInterceptor.CALLER_ID) String callerId,
                                   @Valid @RequestBody PlayerRefRequest request) {
        Ownership.requireSelf(callerId, request.playerId());
        return lobbyService.joinLobby(lobbyId, request.playerId());
    }

    /** POST /api/game/lobbies/{lobby_id}/leave */
    @RequiresAuth
    @PostMapping("/{lobbyId}/leave")
    public LobbyResponse leaveLobby(@PathVariable String lobbyId,
                                    @RequestAttribute(AuthInterceptor.CALLER_ID) String callerId,
                                    @Valid @RequestBody PlayerRefRequest request) {
        Ownership.requireSelf(callerId, request.playerId());
        return lobbyService.leaveLobby(lobbyId, request.playerId());
    }
}
