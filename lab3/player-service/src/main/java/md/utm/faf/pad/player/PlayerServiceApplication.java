package md.utm.faf.pad.player;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the Player Service.
 *
 * Layers (read the code in this order):
 *   controller  -> receives HTTP requests, validates the JSON, returns DTOs
 *   service     -> business rules (levels, idempotency, ownership checks ...)
 *   repository  -> stores the data (in memory for now, a real DB later)
 *   model       -> the domain objects we store
 *   dto         -> the exact JSON shapes from the communication contract
 */
@SpringBootApplication
public class PlayerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlayerServiceApplication.class, args);
    }
}
