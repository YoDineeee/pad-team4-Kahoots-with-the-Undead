package com.example.zombies;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

/**
 * Body of PATCH /api/zombies/types/{type_id}.
 * Every field is optional: a field that is missing (null) is left unchanged.
 * type_id is not here, so trying to change it returns 400.
 */
public class ZombieTypeUpdate {

    private String name;

    @Positive
    private Integer health;

    @PositiveOrZero
    private Integer damage;

    @PositiveOrZero
    private Double speed;

    private String lootTableId;

    // If sent, this list REPLACES all existing behaviours. Send [] to remove them all.
    @Valid
    private List<Behaviour> behaviours;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getHealth() { return health; }
    public void setHealth(Integer health) { this.health = health; }

    public Integer getDamage() { return damage; }
    public void setDamage(Integer damage) { this.damage = damage; }

    public Double getSpeed() { return speed; }
    public void setSpeed(Double speed) { this.speed = speed; }

    public String getLootTableId() { return lootTableId; }
    public void setLootTableId(String lootTableId) { this.lootTableId = lootTableId; }

    public List<Behaviour> getBehaviours() { return behaviours; }
    public void setBehaviours(List<Behaviour> behaviours) { this.behaviours = behaviours; }
}
