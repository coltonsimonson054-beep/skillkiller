package com.smp.skillkiller;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Persists every player's skill levels to plugins/SkillKiller/playerdata.yml.
 * An in-memory cache backs all reads/writes during runtime; the file is only
 * touched on load, on explicit save() calls, and on shutdown.
 */
public class DataStore {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, PlayerSkills> cache = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;

    public DataStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "playerdata.yml");
    }

    public void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipping invalid UUID in playerdata.yml: " + key);
                continue;
            }
            PlayerSkills skills = new PlayerSkills(uuid);
            for (Skill skill : Skill.values()) {
                int level = yaml.getInt(key + "." + skill.configKey(), 0);
                skills.setLevel(skill, level);
            }
            cache.put(uuid, skills);
        }
        plugin.getLogger().info("Loaded skill data for " + cache.size() + " player(s).");
    }

    public synchronized void save() {
        if (!dirty) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (PlayerSkills skills : cache.values()) {
            String key = skills.getUuid().toString();
            for (Map.Entry<Skill, Integer> entry : skills.getLevels().entrySet()) {
                yaml.set(key + "." + entry.getKey().configKey(), entry.getValue());
            }
        }
        try {
            File parent = plugin.getDataFolder();
            if (!parent.exists()) {
                parent.mkdirs();
            }
            yaml.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save playerdata.yml", ex);
        }
    }

    /** Returns (creating if necessary) the cached skill record for a player. */
    public PlayerSkills get(UUID uuid) {
        return cache.computeIfAbsent(uuid, PlayerSkills::new);
    }

    public void markDirty() {
        dirty = true;
    }
}
