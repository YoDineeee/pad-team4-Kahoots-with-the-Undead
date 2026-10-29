package md.utm.faf.pad.game.controller;

import jakarta.validation.Valid;
import md.utm.faf.pad.game.dto.SessionDtos.EndSessionResponse;
import md.utm.faf.pad.game.dto.SessionDtos.SessionStateResponse;
import md.utm.faf.pad.game.dto.SessionDtos.StartSessionRequest;
import md.utm.faf.pad.game.dto.SessionDtos.StartSessionResponse;
import md.utm.faf.pad.game.security.AuthInterceptor;
import md.utm.faf.pad.game.security.RequiresAuth;
import md.utm.faf.pad.game.service.SessionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Game session endpoints. The paths are written in full on each method
 * because the contract uses two prefixes: /api/lobbies/... and /api/sessions/...
 */
@RestController
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /** POST /api/lobbies/{lobby_id}/start -> 201 Created. Only the host (from the JWT) may start. */
    @RequiresAuth
    @PostMapping("/api/lobbies/{lobbyId}/start")
    @ResponseStatus(HttpStatus.CREATED)
    public StartSessionResponse startSession(@PathVariable String lobbyId,
                                             @RequestAttribute(AuthInterceptor.CALLER_ID) String callerId,
                                             @Valid @RequestBody StartSessionRequest request) {
        return sessionService.startSession(lobbyId, callerId, request);
    }

    /** GET /api/sessions/{session_id} - snapshot used on reconnect. */
    @GetMapping("/api/sessions/{sessionId}")
    public SessionStateResponse getSessionState(@PathVariable String sessionId) {
        return sessionService.getSessionState(sessionId);
    }

    /** POST /api/sessions/{session_id}/end - only the host may end the semester. */
    @RequiresAuth
    @PostMapping("/api/sessions/{sessionId}/end")
    public EndSessionResponse endSession(@PathVariable String sessionId,
                                         @RequestAttribute(AuthInterceptor.CALLER_ID) String callerId) {
        return sessionService.endSession(sessionId, callerId);
    }
}
