package com.smp.skillkiller;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** Holds one player's current level in every skill. Levels default to 0. */
public class PlayerSkills {

    private final UUID uuid;
    private final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);

    public PlayerSkills(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }

    public int getLevel(Skill skill) {
        return levels.getOrDefault(skill, 0);
    }

    public void setLevel(Skill skill, int level) {
        levels.put(skill, level);
    }

    public Map<Skill, Integer> getLevels() {
        return levels;
    }
}
