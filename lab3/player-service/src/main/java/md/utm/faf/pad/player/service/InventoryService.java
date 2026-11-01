package md.utm.faf.pad.player.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import md.utm.faf.pad.player.dto.InventoryDtos.AddItemRequest;
import md.utm.faf.pad.player.dto.InventoryDtos.InventoryItemResponse;
import md.utm.faf.pad.player.dto.InventoryDtos.ItemQuantityResponse;
import md.utm.faf.pad.player.dto.InventoryDtos.UpdateItemRequest;
import md.utm.faf.pad.player.exception.ApiException;
import md.utm.faf.pad.player.model.InventoryItem;
import md.utm.faf.pad.player.repository.IdempotencyStore;
import md.utm.faf.pad.player.repository.InventoryRepository;
import md.utm.faf.pad.player.repository.PlayerRepository;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The player's persistent inventory (consumables, cosmetics, equipment).
 * Items with the same code are stacked: two grants of COFFEE x1 = one record with quantity 2.
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    /** Human-friendly names for known item codes. Unknown codes get a generated name. */
    private static final Map<String, String> ITEM_NAMES = Map.of(
            "COFFEE", "Coffee",
            "ENERGY_DRINK", "Energy Drink",
            "DAVIDAN_SANDWICH", "Davidan Sandwich",
            "IMPROVISED_AXE", "Improvised Axe",
            "ZOMBIE_DETECTOR", "Zombie Detector"
    );

    private final InventoryRepository inventoryRepository;
    private final PlayerRepository playerRepository;
    private final IdempotencyStore idempotencyStore;

    public InventoryService(InventoryRepository inventoryRepository,
                            PlayerRepository playerRepository,
                            IdempotencyStore idempotencyStore) {
        this.inventoryRepository = inventoryRepository;
        this.playerRepository = playerRepository;
        this.idempotencyStore = idempotencyStore;
    }

    // ---------------------------------------------------------------- Read

    public List<InventoryItemResponse> getInventory(String playerId) {
        requirePlayerExists(playerId);
        return inventoryRepository.findByPlayerId(playerId).stream()
                .map(item -> new InventoryItemResponse(
                        item.getId(), item.getCode(), displayName(item.getCode()), item.getQuantity()))
                .toList();
    }

    // ---------------------------------------------------------------- Create

    /** Called by Crafting Service, World Service (Kiki reward) and Game Service (loot). */
    public synchronized ItemQuantityResponse addItem(String playerId, AddItemRequest request) {
        requirePlayerExists(playerId);

        return idempotencyStore.executeOnce("grant:" + request.operationId(), () -> {
            InventoryItem item = addToStack(playerId, request.code(), request.type(), request.quantity());
            log.info("Item granted: player={} code={} +{} -> quantity={} operation={}", playerId, request.code(), request.quantity(), item.getQuantity(), request.operationId());
            return new ItemQuantityResponse(item.getId(), item.getCode(), item.getQuantity());
        });
    }

    /** The supplies every new player starts with (same items as in the contract example). */
    public void grantStarterKit(String playerId) {
        addToStack(playerId, "COFFEE", "consumable", 3);
        addToStack(playerId, "ENERGY_DRINK", "consumable", 1);
        addToStack(playerId, "DAVIDAN_SANDWICH", "consumable", 2);
        addToStack(playerId, "IMPROVISED_AXE", "equipment", 1);
    }

    // ---------------------------------------------------------------- Update

    /**
     * Changes the quantity of an item. Consuming one Coffee = delta -1.
     * The record stays at quantity 0 instead of being deleted.
     */
    public synchronized ItemQuantityResponse updateItem(String playerId, String itemId, UpdateItemRequest request) {
        InventoryItem item = inventoryRepository.findById(itemId)
                .filter(i -> i.getPlayerId().equals(playerId))
                .orElseThrow(() -> ApiException.notFound("ITEM_NOT_FOUND",
                        "Player " + playerId + " has no item " + itemId));

        return idempotencyStore.executeOnce("item:" + request.operationId(), () -> {
            int newQuantity = item.getQuantity() + request.delta();
            if (newQuantity < 0) {
                log.warn("Not enough items: player={} item={} has={} delta={}", playerId, itemId, item.getQuantity(), request.delta());
                throw ApiException.conflict("NOT_ENOUGH_ITEMS",
                        "Only " + item.getQuantity() + " x " + item.getCode() + " available");
            }
            item.setQuantity(newQuantity);
            log.info("Item quantity changed: player={} item={} code={} delta={} -> quantity={}", playerId, itemId, item.getCode(), request.delta(), newQuantity);
            inventoryRepository.save(item);
            return new ItemQuantityResponse(item.getId(), item.getCode(), item.getQuantity());
        });
    }

    // ---------------------------------------------------------------- helpers

    private InventoryItem addToStack(String playerId, String code, String type, int quantity) {
        InventoryItem item = inventoryRepository.findByPlayerIdAndCode(playerId, code)
                .orElseGet(() -> new InventoryItem("item-" + UUID.randomUUID(), playerId, code, type, 0));
        item.setQuantity(item.getQuantity() + quantity);
        return inventoryRepository.save(item);
    }

    private void requirePlayerExists(String playerId) {
        if (playerRepository.findById(playerId).isEmpty()) {
            throw ApiException.notFound("PLAYER_NOT_FOUND", "No player with id " + playerId);
        }
    }

    /** "ZOMBIE_DETECTOR" -> "Zombie Detector" when the code is not in ITEM_NAMES. */
    static String displayName(String code) {
        String knownName = ITEM_NAMES.get(code);
        if (knownName != null) {
            return knownName;
        }
        return Arrays.stream(code.toLowerCase().split("_"))
                .filter(word -> !word.isEmpty())
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }
}
