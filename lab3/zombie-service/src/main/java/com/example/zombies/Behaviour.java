package com.example.zombies;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * One rule such as "when player_nearby -> chase".
 * It is only stored as configuration; this service does not execute it.
 * @Embeddable = it has no ID of its own, it always belongs to a ZombieType.
 */
@Embeddable
@JsonInclude(JsonInclude.Include.NON_NULL) // optional fields that are null are left out of the JSON
public class Behaviour {

    // "trigger" and "action" are reserved words in SQL, so the columns get other names.
    @NotBlank
    @Column(name = "trigger_name")
    private String trigger;

    @NotBlank
    @Column(name = "action_name")
    private String action;

    // Optional settings
    @PositiveOrZero
    private Integer rangeTiles;
    @PositiveOrZero
    private Integer maxItems;

    private String location;

    public String getTrigger() { return trigger; }
    public void setTrigger(String trigger) { this.trigger = trigger; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Integer getRangeTiles() { return rangeTiles; }
    public void setRangeTiles(Integer rangeTiles) { this.rangeTiles = rangeTiles; }

    public Integer getMaxItems() { return maxItems; }
    public void setMaxItems(Integer maxItems) { this.maxItems = maxItems; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
}
