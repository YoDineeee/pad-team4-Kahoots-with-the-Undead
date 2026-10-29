package md.utm.faf.pad.player.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import md.utm.faf.pad.player.dto.FriendDtos.FriendResponse;
import md.utm.faf.pad.player.dto.FriendDtos.FriendshipResponse;
import md.utm.faf.pad.player.exception.ApiException;
import md.utm.faf.pad.player.model.Friendship;
import md.utm.faf.pad.player.model.Player;
import md.utm.faf.pad.player.repository.FriendshipRepository;
import md.utm.faf.pad.player.repository.PlayerRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Friend requests, friend list and online presence. */
@Service
public class FriendService {

    private static final Logger log = LoggerFactory.getLogger(FriendService.class);

    private final FriendshipRepository friendshipRepository;
    private final PlayerRepository playerRepository;
    private final PlayerService playerService;

    public FriendService(FriendshipRepository friendshipRepository,
                         PlayerRepository playerRepository,
                         PlayerService playerService) {
        this.friendshipRepository = friendshipRepository;
        this.playerRepository = playerRepository;
        this.playerService = playerService;
    }

    public FriendshipResponse sendFriendRequest(String playerId, String targetPlayerId) {
        playerService.findPlayerOrThrow(playerId);
        playerService.findPlayerOrThrow(targetPlayerId);

        if (playerId.equals(targetPlayerId)) {
            throw ApiException.badRequest("CANNOT_FRIEND_YOURSELF", "You can't send a friend request to yourself");
        }
        if (friendshipRepository.existsBetween(playerId, targetPlayerId)) {
            throw ApiException.conflict("FRIENDSHIP_ALREADY_EXISTS",
                    "A friendship or request between these players already exists");
        }

        Friendship friendship = friendshipRepository.save(
                new Friendship("friend-" + UUID.randomUUID(), playerId, targetPlayerId));

        log.info("Friend request sent: id={} from={} to={}", friendship.getId(), playerId, targetPlayerId);
        return new FriendshipResponse(friendship.getId(), targetPlayerId, friendship.getStatus().name());
    }

    public List<FriendResponse> getFriends(String playerId) {
        playerService.findPlayerOrThrow(playerId);

        return friendshipRepository.findAllInvolving(playerId).stream()
                .map(friendship -> toFriendResponse(friendship, playerId))
                .flatMap(Optional::stream) // skip friends whose account was deleted meanwhile
                .toList();
    }

    /** Removes an accepted friendship or cancels a pending request. */
    public void removeFriend(String playerId, String friendshipId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .filter(f -> f.involves(playerId))
                .orElseThrow(() -> ApiException.notFound("FRIENDSHIP_NOT_FOUND",
                        "Player " + playerId + " has no friendship " + friendshipId));
        friendshipRepository.deleteById(friendship.getId());
        log.info("Friendship removed: id={} by player={}", friendshipId, playerId);
    }

    private Optional<FriendResponse> toFriendResponse(Friendship friendship, String playerId) {
        String friendId = friendship.otherSide(playerId);
        return playerRepository.findById(friendId)
                .map((Player friend) -> new FriendResponse(
                        friend.getId(),
                        friend.getUsername(),
                        friend.isOnline() ? "online" : "offline",
                        friendship.getId(),
                        friendship.getStatus().name()));
    }
}
