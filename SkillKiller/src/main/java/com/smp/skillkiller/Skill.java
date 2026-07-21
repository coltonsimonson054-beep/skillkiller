package com.smp.skillkiller;

/**
 * The set of skills players can gain/lose levels in. To add a new skill:
 *   1. Add an entry here with its config key and type.
 *   2. If it's a POTION skill, map it to a PotionEffectType in SkillManager#potionType.
 *   3. If it's an ATTRIBUTE skill, wire it up in SkillManager#applyAttribute.
 *   4. Add a matching section under `skills:` in config.yml.
 */
public enum Skill {

    MINING("mining", SkillType.POTION),
    SPEED("speed", SkillType.POTION),
    HEALTH("health", SkillType.ATTRIBUTE),
    STRENGTH("strength", SkillType.ATTRIBUTE),
    REGENERATION("regeneration", SkillType.POTION),
    LUCK("luck", SkillType.POTION);

    private final String configKey;
    private final SkillType type;

    Skill(String configKey, SkillType type) {
        this.configKey = configKey;
        this.type = type;
    }

    /** The lowercase key used in config.yml and in commands (e.g. "mining"). */
    public String configKey() {
        return configKey;
    }

    public SkillType type() {
        return type;
    }

    public enum SkillType {
        POTION,
        ATTRIBUTE
    }
}
