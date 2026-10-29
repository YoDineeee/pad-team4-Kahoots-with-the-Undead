package md.utm.faf.pad.player.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** JSON shapes for the inventory endpoints. */
public final class InventoryDtos {

    private InventoryDtos() {}

    /** One element of GET /api/players/{player_id}/inventory */
    public record InventoryItemResponse(String itemId, String code, String name, int count) {}

    /** POST /api/players/{player_id}/inventory  [internal] */
    public record AddItemRequest(
            @NotBlank String operationId,
            @NotBlank String code,
            @NotBlank String type,
            @NotNull @Positive Integer quantity,
            String source
    ) {}

    /** PATCH /api/players/{player_id}/inventory/{item_id} */
    public record UpdateItemRequest(
            @NotBlank String operationId,
            @NotNull Integer delta,
            String reason
    ) {}

    /** Response of both POST and PATCH on the inventory. */
    public record ItemQuantityResponse(String itemId, String code, int quantity) {}
}
