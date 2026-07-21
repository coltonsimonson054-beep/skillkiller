package com.smp.skillkiller;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Owns all "what does level N in skill X actually do" logic: reading tunables
 * from config.yml, applying/removing the resulting attribute modifiers or
 * potion effects, and changing+persisting a player's level in a skill.
 */
public class SkillManager {

    private final SkillKillerPlugin plugin;
    private final DataStore dataStore;
    private final Map<Skill, NamespacedKey> attributeKeys = new EnumMap<>(Skill.class);

    public SkillManager(SkillKillerPlugin plugin, DataStore dataStore) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        for (Skill skill : Skill.values()) {
            attributeKeys.put(skill, new NamespacedKey(plugin, "skillkiller_" + skill.configKey()));
        }
    }

    // ---------- config lookups ----------

    private ConfigurationSection skillSection(Skill skill) {
        return plugin.getConfig().getConfigurationSection("skills." + skill.configKey());
    }

    public boolean isEnabled(Skill skill) {
        ConfigurationSection section = skillSection(skill);
        return section == null || section.getBoolean("enabled", true);
    }

    public int getMaxLevel(Skill skill) {
        ConfigurationSection section = skillSection(skill);
        return section == null ? 10 : section.getInt("max-level", 10);
    }

    public double getAmountPerLevel(Skill skill) {
        ConfigurationSection section = skillSection(skill);
        return section == null ? 1.0 : section.getDouble("amount-per-level", 1.0);
    }

    public String getDisplayName(Skill skill) {
        String def = skill.name().charAt(0) + skill.name().substring(1).toLowerCase();
        ConfigurationSection section = skillSection(skill);
        return section == null ? def : section.getString("display-name", def);
    }

    public int getMinLevel() {
        return plugin.getConfig().getInt("min-level", 0);
    }

    // ---------- level changes ----------

    /** Adjusts a player's level by delta, clamps it, persists it, and reapplies effects if online. */
    public int changeLevel(UUID uuid, Skill skill, int delta) {
        int current = dataStore.get(uuid).getLevel(skill);
        return setLevel(uuid, skill, current + delta);
    }

    /** Sets a player's level in a skill outright, clamps it, persists it, and reapplies effects if online. */
    public int setLevel(UUID uuid, Skill skill, int level) {
        int clamped = Math.max(getMinLevel(), Math.min(getMaxLevel(skill), level));
        dataStore.get(uuid).setLevel(skill, clamped);
        dataStore.markDirty();

        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null && player.isOnline()) {
            applySkill(player, skill, clamped);
        }
        return clamped;
    }

    // ---------- effect application ----------

    /** Applies every skill's current level to the player. Call on join/respawn. */
    public void applyAll(Player player) {
        PlayerSkills skills = dataStore.get(player.getUniqueId());
        for (Skill skill : Skill.values()) {
            applySkill(player, skill, skills.getLevel(skill));
        }
    }

    /** Re-applies only the potion-based skills. Used by the safety-net timer and the milk-bucket guard. */
    public void applyPotionSkills(Player player) {
        PlayerSkills skills = dataStore.get(player.getUniqueId());
        for (Skill skill : Skill.values()) {
            if (skill.type() == Skill.SkillType.POTION) {
                applySkill(player, skill, skills.getLevel(skill));
            }
        }
    }

    private void applySkill(Player player, Skill skill, int level) {
        if (!isEnabled(skill)) {
            return;
        }
        if (skill.type() == Skill.SkillType.ATTRIBUTE) {
            applyAttribute(player, skill, level);
        } else {
            applyPotion(player, skill, level);
        }
    }

    private void applyAttribute(Player player, Skill skill, int level) {
        Attribute attribute = skill == Skill.HEALTH ? Attribute.MAX_HEALTH : Attribute.ATTACK_DAMAGE;
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }

        NamespacedKey key = attributeKeys.get(skill);
        instance.getModifiers().stream()
                .filter(modifier -> modifier.getKey().equals(key))
                .findFirst()
                .ifPresent(instance::removeModifier);

        if (level > 0) {
            double amount = level * getAmountPerLevel(skill);
            instance.addModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER));
        }

        // Keep current health from visually exceeding a lowered max health.
        if (skill == Skill.HEALTH && player.getHealth() > instance.getValue()) {
            player.setHealth(instance.getValue());
        }
    }

    private void applyPotion(Player player, Skill skill, int level) {
        PotionEffectType type = potionType(skill);
        if (type == null) {
            return;
        }
        if (level <= 0) {
            player.removePotionEffect(type);
            return;
        }
        int amplifier = level - 1;
        player.addPotionEffect(new PotionEffect(type, PotionEffect.INFINITE_DURATION, amplifier, true, false, false));
    }

    private PotionEffectType potionType(Skill skill) {
        return switch (skill) {
            case MINING -> PotionEffectType.HASTE;
            case SPEED -> PotionEffectType.SPEED;
            case REGENERATION -> PotionEffectType.REGENERATION;
            case LUCK -> PotionEffectType.LUCK;
            default -> null;
        };
    }
}
