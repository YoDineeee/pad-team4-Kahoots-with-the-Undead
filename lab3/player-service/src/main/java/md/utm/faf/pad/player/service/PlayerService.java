package md.utm.faf.pad.player.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import md.utm.faf.pad.player.dto.AuthDtos.LoginRequest;
import md.utm.faf.pad.player.dto.AuthDtos.LoginResponse;
import md.utm.faf.pad.player.dto.AuthDtos.RegisterRequest;
import md.utm.faf.pad.player.dto.AuthDtos.RegisterResponse;
import md.utm.faf.pad.player.dto.ProfileDtos.ProfileResponse;
import md.utm.faf.pad.player.dto.ProfileDtos.UpdateProfileRequest;
import md.utm.faf.pad.player.exception.ApiException;
import md.utm.faf.pad.player.model.Player;
import md.utm.faf.pad.player.repository.FriendshipRepository;
import md.utm.faf.pad.player.repository.InventoryRepository;
import md.utm.faf.pad.player.repository.PlayerRepository;
import md.utm.faf.pad.player.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Registration, login and the player's public profile. */
@Service
public class PlayerService {

    private static final Logger log = LoggerFactory.getLogger(PlayerService.class);

    private static final List<String> DEFAULT_ROLES = List.of("player");

    private final PlayerRepository playerRepository;
    private final InventoryRepository inventoryRepository;
    private final FriendshipRepository friendshipRepository;
    private final InventoryService inventoryService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    // Constructor injection: Spring passes the beans in. No @Autowired on fields needed.
    public PlayerService(PlayerRepository playerRepository,
                         InventoryRepository inventoryRepository,
                         FriendshipRepository friendshipRepository,
                         InventoryService inventoryService,
                         PasswordEncoder passwordEncoder,
                         JwtService jwtService) {
        this.playerRepository = playerRepository;
        this.inventoryRepository = inventoryRepository;
        this.friendshipRepository = friendshipRepository;
        this.inventoryService = inventoryService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    // ---------------------------------------------------------------- register / login

    public RegisterResponse register(RegisterRequest request) {
        if (playerRepository.existsByEmail(request.email())) {
            throw ApiException.conflict("EMAIL_ALREADY_USED", "An account with this email already exists");
        }
        if (playerRepository.existsByUsername(request.username())) {
            throw ApiException.conflict("USERNAME_TAKEN", "This username is already taken");
        }

        String id = "player-" + UUID.randomUUID();
        String passwordHash = passwordEncoder.encode(request.password());
        Player player = playerRepository.save(new Player(id, request.username(), request.email(), passwordHash));

        // Every survivor wakes up in FAF Cab with a few basic supplies.
        inventoryService.grantStarterKit(player.getId());
        log.info("Player registered: id={} username={} (starter kit granted)", player.getId(), player.getUsername());

        return new RegisterResponse(player.getId(), player.getUsername(), player.getLevel(), player.getXp());
    }

    public LoginResponse login(LoginRequest request) {
        // Same error for "unknown email" and "wrong password": don't tell attackers which one was wrong.
        Player player = playerRepository.findByEmail(request.email())
                .filter(p -> passwordEncoder.matches(request.password(), p.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("INVALID_CREDENTIALS", "Email or password is wrong"));

        player.setOnline(true);
        log.info("Player logged in: id={} username={}", player.getId(), player.getUsername());
        String jwt = jwtService.generateToken(player.getId(), DEFAULT_ROLES);
        return new LoginResponse(jwt, player.getId(), DEFAULT_ROLES);
    }

    // ---------------------------------------------------------------- profile (R U D)

    public ProfileResponse getProfile(String playerId) {
        return toProfileResponse(findPlayerOrThrow(playerId));
    }

    public ProfileResponse updateProfile(String playerId, UpdateProfileRequest request) {
        Player player = findPlayerOrThrow(playerId);

        // PATCH semantics: only the fields that were sent are changed.
        if (request.title() != null) {
            player.setTitle(request.title());
        }
        if (request.avatar() != null) {
            player.setAvatar(request.avatar());
        }
        log.info("Profile updated: id={} title_changed={} avatar_changed={}", playerId, request.title() != null, request.avatar() != null);
        playerRepository.save(player);
        return toProfileResponse(player);
    }

    /** Contract: "Inventory and active trades are released first", then the account is removed. */
    public void deletePlayer(String playerId) {
        findPlayerOrThrow(playerId);
        inventoryRepository.deleteByPlayerId(playerId);
        friendshipRepository.deleteAllInvolving(playerId);
        // Trades are not implemented in this lab iteration, so there is nothing else to release.
        playerRepository.deleteById(playerId);
        log.info("Player deleted: id={} (inventory and friendships released)", playerId);
    }

    // ---------------------------------------------------------------- helpers

    /** Used by the other services in this package too. */
    public Player findPlayerOrThrow(String playerId) {
        return playerRepository.findById(playerId)
                .orElseThrow(() -> ApiException.notFound("PLAYER_NOT_FOUND", "No player with id " + playerId));
    }

    private ProfileResponse toProfileResponse(Player p) {
        return new ProfileResponse(p.getId(), p.getUsername(), p.getLevel(), p.getXp(),
                p.getUniversity(), p.getTitle(), p.getAvatar());
    }
}
