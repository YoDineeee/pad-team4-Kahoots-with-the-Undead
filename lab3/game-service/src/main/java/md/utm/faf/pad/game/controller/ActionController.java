package md.utm.faf.pad.game.controller;

import jakarta.validation.Valid;
import md.utm.faf.pad.game.dto.ActionDtos.ActionStartedResponse;
import md.utm.faf.pad.game.dto.ActionDtos.ActionStatusResponse;
import md.utm.faf.pad.game.dto.ActionDtos.StartActionRequest;
import md.utm.faf.pad.game.security.AuthInterceptor;
import md.utm.faf.pad.game.security.Ownership;
import md.utm.faf.pad.game.security.RequiresAuth;
import md.utm.faf.pad.game.service.ActionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Timed action endpoints: start (202), poll status, cancel. */
@RestController
public class ActionController {

    private final ActionService actionService;

    public ActionController(ActionService actionService) {
        this.actionService = actionService;
    }

    /**
     * POST /api/game/lobbies/{lobby_id}/actions -> 202 Accepted.
     * 202 (not 201) tells the client: "I accepted the job, it is not finished yet".
     */
    @RequiresAuth
    @PostMapping("/api/game/lobbies/{lobbyId}/actions")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ActionStartedResponse startAction(@PathVariable String lobbyId,
                                             @RequestAttribute(AuthInterceptor.CALLER_ID) String callerId,
                                             @Valid @RequestBody StartActionRequest request) {
        Ownership.requireSelf(callerId, request.playerId());
        return actionService.startAction(lobbyId, request);
    }

    /** GET /api/sessions/{session_id}/actions/{action_id} - polling fallback. */
    @GetMapping("/api/sessions/{sessionId}/actions/{actionId}")
    public ActionStatusResponse getActionStatus(@PathVariable String sessionId,
                                                @PathVariable String actionId) {
        return actionService.getActionStatus(sessionId, actionId);
    }

    /** DELETE /api/game/actions/{action_id} -> 204 No Content */
    @RequiresAuth
    @DeleteMapping("/api/game/actions/{actionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelAction(@PathVariable String actionId,
                             @RequestAttribute(AuthInterceptor.CALLER_ID) String callerId) {
        actionService.cancelAction(actionId, callerId);
    }
}
