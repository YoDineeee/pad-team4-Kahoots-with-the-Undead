package md.utm.faf.pad.game.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import md.utm.faf.pad.game.dto.LobbyDtos.CreateLobbyRequest;
import md.utm.faf.pad.game.dto.LobbyDtos.LobbyResponse;
import md.utm.faf.pad.game.exception.ApiException;
import md.utm.faf.pad.game.model.GameSession;
import md.utm.faf.pad.game.model.Lobby;
import md.utm.faf.pad.game.repository.LobbyRepository;
import md.utm.faf.pad.game.repository.SessionRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Create, read, join and leave lobbies. */
@Service
public class LobbyService {

    private static final Logger log = LoggerFactory.getLogger(LobbyService.class);

    private final LobbyRepository lobbyRepository;
    private final SessionRepository sessionRepository;
    private final Clock clock;

    public LobbyService(LobbyRepository lobbyRepository, SessionRepository sessionRepository, Clock clock) {
        this.lobbyRepository = lobbyRepository;
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    public LobbyResponse createLobby(CreateLobbyRequest request) {
        Lobby lobby = new Lobby("lobby-" + UUID.randomUUID(), request.name(), request.hostId(),
                request.university(), request.maxPlayers());
        log.info("Lobby created: id={} name={} host={} max_players={}", lobby.getId(), request.name(), request.hostId(), request.maxPlayers());
        return toResponse(lobbyRepository.save(lobby));
    }

    public LobbyResponse getLobby(String lobbyId) {
        return toResponse(findLobbyOrThrow(lobbyId));
    }

    /** synchronized: two players joining the last free slot at the same time can't both get in. */
    public synchronized LobbyResponse joinLobby(String lobbyId, String playerId) {
        Lobby lobby = findLobbyOrThrow(lobbyId);

        if (lobby.hasStarted()) {
            throw ApiException.conflict("SESSION_ALREADY_STARTED", "This lobby is already playing");
        }
        if (lobby.hasPlayer(playerId)) {
            throw ApiException.conflict("ALREADY_IN_LOBBY", "Player is already in this lobby");
        }
        if (lobby.isFull()) {
            throw ApiException.conflict("LOBBY_FULL", "Lobby has reached max_players");
        }

        lobby.addPlayer(playerId);
        log.info("Player joined lobby: lobby={} player={} players={}/{}", lobbyId, playerId, lobby.getPlayerIds().size(), lobby.getMaxPlayers());
        return toResponse(lobby);
    }

    public synchronized LobbyResponse leaveLobby(String lobbyId, String playerId) {
        Lobby lobby = findLobbyOrThrow(lobbyId);

        if (!lobby.hasPlayer(playerId)) {
            throw ApiException.notFound("PLAYER_NOT_IN_LOBBY", "Player is not in this lobby");
        }

        lobby.removePlayer(playerId);
        log.info("Player left lobby: lobby={} player={} players_left={}", lobbyId, playerId, lobby.getPlayerIds().size());

        // An empty lobby that never started is useless -> remove it.
        if (lobby.getPlayerIds().isEmpty() && !lobby.hasStarted()) {
            lobbyRepository.deleteById(lobbyId);
            log.info("Empty lobby removed: lobby={}", lobbyId);
        }
        return toResponse(lobby);
    }

    public Lobby findLobbyOrThrow(String lobbyId) {
        return lobbyRepository.findById(lobbyId)
                .orElseThrow(() -> ApiException.notFound("LOBBY_NOT_FOUND", "No lobby with id " + lobbyId));
    }

    /** day_cycle and phase come from the running session, or "day 1" if the game hasn't started yet. */
    private LobbyResponse toResponse(Lobby lobby) {
        Instant now = clock.instant();
        Optional<GameSession> session = Optional.ofNullable(lobby.getSessionId())
                .flatMap(sessionRepository::findById);

        int dayCycle = session.map(s -> s.cycleNumberAt(now)).orElse(1);
        String phase = session.map(s -> s.phaseAt(now).name().toLowerCase()).orElse("day");

        return new LobbyResponse(lobby.getId(), lobby.getName(), lobby.getHostId(), lobby.getUniversity(),
                lobby.getMaxPlayers(), dayCycle, phase, lobby.getPlayerIds(), lobby.getSessionId());
    }
}
