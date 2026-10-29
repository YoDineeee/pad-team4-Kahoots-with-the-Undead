package com.example.resources;

import jakarta.validation.constraints.PositiveOrZero;

/**
 * Body of PATCH /api/resources/points/{point_id}.
 * Only these two fields may change; a missing (null) field is left unchanged.
 * Sending any other field (amount, room_id, ...) returns 400.
 */
public class ResourcePointUpdate {

    private String resource;

    @PositiveOrZero
    private Integer maxAmount;

    public String getResource() { return resource; }
    public void setResource(String resource) { this.resource = resource; }

    public Integer getMaxAmount() { return maxAmount; }
    public void setMaxAmount(Integer maxAmount) { this.maxAmount = maxAmount; }
}
