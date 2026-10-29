package md.utm.faf.pad.game.client;

/**
 * What the Game Service needs from the World Service.
 *
 * It is an INTERFACE on purpose: today a mock implements it ({@link MockWorldServiceClient}),
 * later an HTTP implementation (Spring RestClient) can replace the mock
 * without changing a single line in the services that use it.
 */
public interface WorldServiceClient {

    /** Asks the World Service for the university map to use in a new session; returns its id. */
    String fetchMapForSession(String lobbyId, String university);
}
