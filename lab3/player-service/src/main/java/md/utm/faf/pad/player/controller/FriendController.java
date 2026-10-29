package md.utm.faf.pad.player.controller;

import jakarta.validation.Valid;
import md.utm.faf.pad.player.dto.FriendDtos.AddFriendRequest;
import md.utm.faf.pad.player.dto.FriendDtos.FriendResponse;
import md.utm.faf.pad.player.dto.FriendDtos.FriendshipResponse;
import md.utm.faf.pad.player.security.AuthInterceptor;
import md.utm.faf.pad.player.security.Ownership;
import md.utm.faf.pad.player.security.RequiresAuth;
import md.utm.faf.pad.player.service.FriendService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Friends endpoints: send request, list friends, remove friend / cancel request. */
@RestController
@RequestMapping("/api/players/{playerId}/friends")
public class FriendController {

    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    /** POST /api/players/{player_id}/friends -> 201 Created, status PENDING */
    @RequiresAuth
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FriendshipResponse addFriend(@PathVariable String playerId,
                                        @RequestAttribute(AuthInterceptor.CALLER_ID) String callerId,
                                        @Valid @RequestBody AddFriendRequest request) {
        Ownership.requireSelf(callerId, playerId);
        return friendService.sendFriendRequest(playerId, request.targetPlayerId());
    }

    /** GET /api/players/{player_id}/friends */
    @RequiresAuth
    @GetMapping
    public List<FriendResponse> getFriends(@PathVariable String playerId) {
        return friendService.getFriends(playerId);
    }

    /** DELETE /api/players/{player_id}/friends/{friendship_id} -> 204 No Content */
    @RequiresAuth
    @DeleteMapping("/{friendshipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFriend(@PathVariable String playerId,
                             @PathVariable String friendshipId,
                             @RequestAttribute(AuthInterceptor.CALLER_ID) String callerId) {
        Ownership.requireSelf(callerId, playerId);
        friendService.removeFriend(playerId, friendshipId);
    }
}
