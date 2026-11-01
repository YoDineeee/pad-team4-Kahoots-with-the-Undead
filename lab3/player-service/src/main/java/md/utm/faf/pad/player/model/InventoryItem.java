package md.utm.faf.pad.player.model;

/**
 * One stack of items owned by a player, e.g. "3 x COFFEE".
 * When an item is consumed down to 0 the record stays (the contract is CRU, not CRUD).
 */
public class InventoryItem {

    private final String id;
    private final String playerId;
    private final String code;   // e.g. COFFEE, ZOMBIE_DETECTOR
    private final String type;   // e.g. consumable, equipment, cosmetic
    private int quantity;

    public InventoryItem(String id, String playerId, String code, String type, int quantity) {
        this.id = id;
        this.playerId = playerId;
        this.code = code;
        this.type = type;
        this.quantity = quantity;
    }

    public String getId() { return id; }
    public String getPlayerId() { return playerId; }
    public String getCode() { return code; }
    public String getType() { return type; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
