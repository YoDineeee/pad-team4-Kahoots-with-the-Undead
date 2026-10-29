package md.utm.faf.pad.player.controller;

import jakarta.validation.Valid;
import md.utm.faf.pad.player.dto.InventoryDtos.AddItemRequest;
import md.utm.faf.pad.player.dto.InventoryDtos.InventoryItemResponse;
import md.utm.faf.pad.player.dto.InventoryDtos.ItemQuantityResponse;
import md.utm.faf.pad.player.dto.InventoryDtos.UpdateItemRequest;
import md.utm.faf.pad.player.security.AuthInterceptor;
import md.utm.faf.pad.player.security.Ownership;
import md.utm.faf.pad.player.security.RequiresAuth;
import md.utm.faf.pad.player.service.InventoryService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Inventory endpoints: Create, Read, Update (no Delete - items stay at quantity 0). */
@RestController
@RequestMapping("/api/players/{playerId}/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /** GET /api/players/{player_id}/inventory - only the owner may look into their bag. */
    @RequiresAuth
    @GetMapping
    public List<InventoryItemResponse> getInventory(@PathVariable String playerId,
                                                    @RequestAttribute(AuthInterceptor.CALLER_ID) String callerId) {
        Ownership.requireSelf(callerId, playerId);
        return inventoryService.getInventory(playerId);
    }

    /** POST /api/players/{player_id}/inventory [internal] -> 201 Created */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ItemQuantityResponse addItem(@PathVariable String playerId,
                                        @Valid @RequestBody AddItemRequest request) {
        return inventoryService.addItem(playerId, request);
    }

    /** PATCH /api/players/{player_id}/inventory/{item_id} - e.g. drink a coffee (delta -1). */
    @PatchMapping("/{itemId}")
    public ItemQuantityResponse updateItem(@PathVariable String playerId,
                                           @PathVariable String itemId,
                                           @Valid @RequestBody UpdateItemRequest request) {
        return inventoryService.updateItem(playerId, itemId, request);
    }
}
