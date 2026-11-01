package com.example.zombies;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Persistable;

/**
 * One kind of zombie (for example "tourist_zombie"), stored in the table zombie_types.
 * The annotations from jakarta.validation are checked when a request arrives (@Valid in the controller).
 */
@Entity
@Table(name = "zombie_types")
public class ZombieType implements Persistable<String> {

    // ---- Lab 3: safe with several instances --------------------------------------------------
    // The id is chosen by the client, so Spring Data can't tell "new" from "existing" and its save()
    // would MERGE: if two instances handle "POST tourist_zombie" at the same moment, both pass the
    // existsById() check and the second silently overwrites the first. Telling Spring Data which
    // objects are new makes save() INSERT them instead, so the database's primary key rejects the
    //  isecond one (-> 409 in ApiExceptionHandler) no matter which instance handled it.
    @Transient
    @JsonIgnore
    private boolean isNew = true;

    @Override
    @JsonIgnore
    public String getId() { return typeId; }

    @Override
    @JsonIgnore
    public boolean isNew() { return isNew; }

    // Loaded from or saved to the database -> not new any more (later saves are updates).
    @PostLoad
    @PostPersist
    void markNotNew() { this.isNew = false; }
    // -------------------------------------------------------------------------------------------

    // The client chooses the ID (e.g. "tourist_zombie"), so there is no @GeneratedValue.
    @Id
    @NotBlank
    @Size(max = 100)
    private String typeId;

    @NotBlank
    private String name;

    @NotNull
    @Positive
    private Integer health;

    @NotNull
    @PositiveOrZero
    private Integer damage;

    @NotNull
    @PositiveOrZero
    private Double speed;

    @NotBlank
    private String lootTableId;

    // The list of behaviours is saved in a second table, zombie_behaviours.
    @Valid
    @NotNull
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "zombie_behaviours", joinColumns = @JoinColumn(name = "type_id"))
    @OrderColumn(name = "behaviour_order") // keeps the list order
    private List<Behaviour> behaviours = new ArrayList<>();

    public String getTypeId() { return typeId; }
    public void setTypeId(String typeId) { this.typeId = typeId; }

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
