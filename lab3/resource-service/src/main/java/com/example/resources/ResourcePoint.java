package com.example.resources;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;

/**
 * A place in a room where a resource can be gathered (for example 10 of 30 metal in lab-204).
 * Stored in the table resource_points.
 */
@Entity
@Table(name = "resource_points")
public class ResourcePoint {

    // The database generates a UUID. READ_ONLY = a point_id sent by the client is ignored.
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String pointId;

    @NotBlank
    private String roomId;

    @NotBlank
    private String resource;

    // How much is there now, and the maximum it can hold (0 <= amount <= max_amount)
    @NotNull
    @PositiveOrZero
    private Integer amount;

    @NotNull
    @PositiveOrZero
    private Integer maxAmount;

    public String getPointId() { return pointId; }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public String getResource() { return resource; }
    public void setResource(String resource) { this.resource = resource; }

    public Integer getAmount() { return amount; }
    public void setAmount(Integer amount) { this.amount = amount; }

    public Integer getMaxAmount() { return maxAmount; }
    public void setMaxAmount(Integer maxAmount) { this.maxAmount = maxAmount; }
}
