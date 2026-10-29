package md.utm.faf.pad.player.repository;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Makes "internal" operations idempotent through their {@code operation_id}.
 *
 * Why? Other services (Exam, Crafting, Game ...) may retry a request when the network fails.
 * Without this, a retried "+250 XP" would give the player 500 XP.
 *
 * How? The first time we see an operation_id we run the operation and remember its response.
 * If the same operation_id comes again, we just return the remembered response
 * and do NOT run the operation a second time.
 */
@Component
public class IdempotencyStore {

    private final Map<String, Object> responsesByOperationId = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T executeOnce(String operationId, Supplier<T> operation) {
        // computeIfAbsent runs the supplier only if the key is missing, and does it atomically.
        // If the operation throws an exception, nothing is stored, so the client can retry.
        return (T) responsesByOperationId.computeIfAbsent(operationId, id -> operation.get());
    }
}
