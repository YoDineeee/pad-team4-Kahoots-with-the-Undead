package md.utm.faf.pad.game;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the Game Service.
 *
 * Layers (read the code in this order):
 *   controller  -> HTTP endpoints
 *   service     -> game rules (lobbies, sessions, day/night cycle, timed actions)
 *   client      -> calls to OTHER microservices (mocked for now)
 *   repository  -> in-memory storage
 *   model / dto -> domain objects and JSON shapes
 */
@SpringBootApplication
public class GameServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameServiceApplication.class, args);
    }
}
