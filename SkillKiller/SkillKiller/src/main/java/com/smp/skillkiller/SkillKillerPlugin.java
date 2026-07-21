package com.smp.skillkiller;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * SkillKiller - killing a player permanently steals a level in a random skill.
 *
 * Everything lives in this one file as static nested classes so the whole
 * plugin is a single copy-paste-able source file. Functionally identical to
 * a normal multi-file layout; only the file organization differs.
 *
 * Requires plugin.yml and config.yml in src/main/resources (see the README /
 * the multi-file project this was generated from) and paper-api 26.2 on the
 * compile classpath.
 */
public class SkillKillerPlugin extends JavaPlugin {

    private DataStore dataStore;
    private SkillManager skillManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        dataStore = new DataStore(this);
        dataStore.load();

        skillManager = new SkillManager(this, dataStore);

        getServer().getPluginManager().registerEvents(new DeathListener(this, skillManager), this);
        getServer().getPluginManager().registerEvents(new PlayerLifecycleListener(this, skillManager), this);

        getCommand("skills").setExecutor(new SkillsCommand(this, skillManager, dataStore));
        SkillsAdminCommand adminCommand = new SkillsAdminCommand(this, skillManager);
        getCommand("skillsadmin").setExecutor(adminCommand);
        getCommand("skillsadmin").setTabCompleter(adminCommand);

        // Covers /reload: apply effects to anyone already online.
        for (Player player : getServer().getOnlinePlayers()) {
            skillManager.applyAll(player);
        }

        startReapplyTask();
        startAutosaveTask();

        String enabledSkills = Arrays.stream(Skill.values())
                .filter(skillManager::isEnabled)
                .map(skillManager::getDisplayName)
                .reduce((a, b) -> a + ", " + b)
                .orElse("none");
        getLogger().info("SkillKiller enabled. Active skills: " + enabledSkills);
    }

    @Override
    public void onDisable() {
        if (dataStore != null) {
            dataStore.markDirty();
            dataStore.save();
        }
    }

    private void startReapplyTask() {
        int seconds = getConfig().getInt("reapply-interval-seconds", 60);
        if (seconds <= 0) {
            return;
        }
        long ticks = seconds * 20L;
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                skillManager.applyPotionSkills(player);
            }
        }, ticks, ticks);
    }

    private void startAutosaveTask() {
        int minutes = getConfig().getInt("autosave-interval-minutes", 5);
        if (minutes <= 0) {
            return;
        }
        long ticks = minutes * 60L * 20L;
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> dataStore.save(), ticks, ticks);
    }

    public SkillManager getSkillManager() {
        return skillManager;
    }

    public DataStore getDataStore() {
        return dataStore;
    }

    // =====================================================================
    //  Skill - the set of skills players can gain/lose levels in.
    //  To add a new skill: add an entry here, map it in SkillManager
    //  (potionType() or applyAttribute()), and add a `skills:` section for
    //  it in config.yml.
    // =====================================================================
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

    // =====================================================================
    //  PlayerSkills - one player's current level in every skill.
    // =====================================================================
    static class PlayerSkills {

        private final UUID uuid;
        private final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);

        PlayerSkills(UUID uuid) {
            this.uuid = uuid;
        }

        UUID getUuid() {
            return uuid;
        }

        int getLevel(Skill skill) {
            return levels.getOrDefault(skill, 0);
        }

        void setLevel(Skill skill, int level) {
            levels.put(skill, level);
        }

        Map<Skill, Integer> getLevels() {
            return levels;
        }
    }

    // =====================================================================
    //  DataStore - loads/saves every player's skill levels to
    //  plugins/SkillKiller/playerdata.yml. An in-memory cache backs all
    //  reads/writes during runtime.
    // =====================================================================
    static class DataStore {

        private final JavaPlugin plugin;
        private final File file;
        private final Map<UUID, PlayerSkills> cache = new ConcurrentHashMap<>();
        private volatile boolean dirty = false;

        DataStore(JavaPlugin plugin) {
            this.plugin = plugin;
            this.file = new File(plugin.getDataFolder(), "playerdata.yml");
        }

        void load() {
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

        synchronized void save() {
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

        PlayerSkills get(UUID uuid) {
            return cache.computeIfAbsent(uuid, PlayerSkills::new);
        }

        void markDirty() {
            dirty = true;
        }
    }

    // =====================================================================
    //  SkillManager - config-driven effect application + level changes.
    // =====================================================================
    static class SkillManager {

        private final SkillKillerPlugin plugin;
        private final DataStore dataStore;
        private final Map<Skill, NamespacedKey> attributeKeys = new EnumMap<>(Skill.class);

        SkillManager(SkillKillerPlugin plugin, DataStore dataStore) {
            this.plugin = plugin;
            this.dataStore = dataStore;
            for (Skill skill : Skill.values()) {
                attributeKeys.put(skill, new NamespacedKey(plugin, "skillkiller_" + skill.configKey()));
            }
        }

        private ConfigurationSection skillSection(Skill skill) {
            return plugin.getConfig().getConfigurationSection("skills." + skill.configKey());
        }

        boolean isEnabled(Skill skill) {
            ConfigurationSection section = skillSection(skill);
            return section == null || section.getBoolean("enabled", true);
        }

        int getMaxLevel(Skill skill) {
            ConfigurationSection section = skillSection(skill);
            return section == null ? 10 : section.getInt("max-level", 10);
        }

        double getAmountPerLevel(Skill skill) {
            ConfigurationSection section = skillSection(skill);
            return section == null ? 1.0 : section.getDouble("amount-per-level", 1.0);
        }

        String getDisplayName(Skill skill) {
            String def = skill.name().charAt(0) + skill.name().substring(1).toLowerCase();
            ConfigurationSection section = skillSection(skill);
            return section == null ? def : section.getString("display-name", def);
        }

        int getMinLevel() {
            return plugin.getConfig().getInt("min-level", 0);
        }

        /** Adjusts a player's level by delta, clamps it, persists it, and reapplies effects if online. */
        int changeLevel(UUID uuid, Skill skill, int delta) {
            int current = dataStore.get(uuid).getLevel(skill);
            return setLevel(uuid, skill, current + delta);
        }

        /** Sets a player's level in a skill outright, clamps it, persists it, and reapplies effects if online. */
        int setLevel(UUID uuid, Skill skill, int level) {
            int clamped = Math.max(getMinLevel(), Math.min(getMaxLevel(skill), level));
            dataStore.get(uuid).setLevel(skill, clamped);
            dataStore.markDirty();

            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                applySkill(player, skill, clamped);
            }
            return clamped;
        }

        /** Applies every skill's current level to the player. Call on join/respawn. */
        void applyAll(Player player) {
            PlayerSkills skills = dataStore.get(player.getUniqueId());
            for (Skill skill : Skill.values()) {
                applySkill(player, skill, skills.getLevel(skill));
            }
        }

        /** Re-applies only the potion-based skills. Used by the safety-net timer and the milk-bucket guard. */
        void applyPotionSkills(Player player) {
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

    // =====================================================================
    //  DeathListener - the core mechanic: kill a player, steal a level.
    // =====================================================================
    static class DeathListener implements Listener {

        private final SkillKillerPlugin plugin;
        private final SkillManager skillManager;
        private final Random random = new Random();

        DeathListener(SkillKillerPlugin plugin, SkillManager skillManager) {
            this.plugin = plugin;
            this.skillManager = skillManager;
        }

        @EventHandler(ignoreCancelled = true)
        public void onDeath(PlayerDeathEvent event) {
            Player victim = event.getEntity();
            Player killer = victim.getKiller();

            if (killer == null || killer.equals(victim)) {
                return;
            }

            List<String> disabledWorlds = plugin.getConfig().getStringList("disabled-worlds");
            if (disabledWorlds.contains(victim.getWorld().getName())) {
                return;
            }

            if (killer.hasPermission("skillkiller.exempt") || victim.hasPermission("skillkiller.exempt")) {
                return;
            }

            Skill[] enabledSkills = Arrays.stream(Skill.values())
                    .filter(skillManager::isEnabled)
                    .toArray(Skill[]::new);
            if (enabledSkills.length == 0) {
                return;
            }

            boolean sameSkill = plugin.getConfig().getBoolean("same-skill-for-both", true);
            Skill killerSkill = enabledSkills[random.nextInt(enabledSkills.length)];
            Skill victimSkill = sameSkill ? killerSkill : enabledSkills[random.nextInt(enabledSkills.length)];

            int newKillerLevel = skillManager.changeLevel(killer.getUniqueId(), killerSkill, 1);
            int newVictimLevel = skillManager.changeLevel(victim.getUniqueId(), victimSkill, -1);

            announce(killer, victim, killerSkill, victimSkill, newKillerLevel, newVictimLevel);
        }

        private void announce(Player killer, Player victim, Skill killerSkill, Skill victimSkill,
                               int killerLevel, int victimLevel) {
            if (plugin.getConfig().getBoolean("notify-players", true)) {
                String killerMsg = plugin.getConfig().getString("killer-message", "&a+1 &b{skill} &7(now level {level})");
                String victimMsg = plugin.getConfig().getString("victim-message", "&c-1 &b{skill} &7(now level {level})");
                killer.sendMessage(color(fill(killerMsg, skillManager.getDisplayName(killerSkill), killerLevel)));
                victim.sendMessage(color(fill(victimMsg, skillManager.getDisplayName(victimSkill), victimLevel)));
            }

            if (plugin.getConfig().getBoolean("broadcast-on-transfer", true)) {
                String format = plugin.getConfig().getString("broadcast-format",
                        "&e{killer} &7defeated &e{victim} &7and stole a level of &b{skill}&7!");
                String message = format
                        .replace("{killer}", killer.getName())
                        .replace("{victim}", victim.getName())
                        .replace("{skill}", skillManager.getDisplayName(killerSkill))
                        .replace("{killer_level}", String.valueOf(killerLevel))
                        .replace("{victim_level}", String.valueOf(victimLevel));
                broadcast(color(message));
            }
        }

        private String fill(String template, String skillName, int level) {
            return template.replace("{skill}", skillName).replace("{level}", String.valueOf(level));
        }

        private String color(String s) {
            return ChatColor.translateAlternateColorCodes('&', s);
        }

        @SuppressWarnings("deprecation") // legacy String broadcast is still supported; swap for Adventure Component if preferred
        private void broadcast(String message) {
            plugin.getServer().broadcastMessage(message);
        }
    }

    // =====================================================================
    //  PlayerLifecycleListener - join/respawn reapplication + milk guard.
    // =====================================================================
    static class PlayerLifecycleListener implements Listener {

        private final SkillKillerPlugin plugin;
        private final SkillManager skillManager;

        PlayerLifecycleListener(SkillKillerPlugin plugin, SkillManager skillManager) {
            this.plugin = plugin;
            this.skillManager = skillManager;
        }

        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            Player player = event.getPlayer();
            // One tick delay so the player's attribute instances are fully initialized.
            plugin.getServer().getScheduler().runTask(plugin, () -> skillManager.applyAll(player));
        }

        @EventHandler
        public void onRespawn(PlayerRespawnEvent event) {
            Player player = event.getPlayer();
            plugin.getServer().getScheduler().runTask(plugin, () -> skillManager.applyAll(player));
        }

        @EventHandler
        public void onConsume(PlayerItemConsumeEvent event) {
            if (event.getItem().getType() != Material.MILK_BUCKET) {
                return;
            }
            if (!plugin.getConfig().getBoolean("protect-from-milk", true)) {
                return;
            }
            Player player = event.getPlayer();
            // Milk clears potion effects on this same tick; reapply right after.
            plugin.getServer().getScheduler().runTask(plugin, () -> skillManager.applyPotionSkills(player));
        }
    }

    // =====================================================================
    //  SkillsCommand - /skills [player]
    // =====================================================================
    static class SkillsCommand implements CommandExecutor {

        private final SkillKillerPlugin plugin;
        private final SkillManager skillManager;
        private final DataStore dataStore;

        SkillsCommand(SkillKillerPlugin plugin, SkillManager skillManager, DataStore dataStore) {
            this.plugin = plugin;
            this.skillManager = skillManager;
            this.dataStore = dataStore;
        }

        @Override
        @SuppressWarnings("deprecation") // name-based offline player lookup; fine for an admin-facing lookup command
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            OfflinePlayer target;
            if (args.length >= 1) {
                target = plugin.getServer().getOfflinePlayer(args[0]);
            } else if (sender instanceof Player player) {
                target = player;
            } else {
                sender.sendMessage(ChatColor.RED + "Console must specify a player: /skills <player>");
                return true;
            }

            UUID uuid = target.getUniqueId();
            PlayerSkills skills = dataStore.get(uuid);
            String name = target.getName() != null ? target.getName() : uuid.toString();

            sender.sendMessage(ChatColor.GOLD + "--- " + name + "'s Skills ---");
            for (Skill skill : Skill.values()) {
                if (!skillManager.isEnabled(skill)) {
                    continue;
                }
                int level = skills.getLevel(skill);
                int max = skillManager.getMaxLevel(skill);
                sender.sendMessage(ChatColor.AQUA + skillManager.getDisplayName(skill) + ChatColor.GRAY + ": "
                        + ChatColor.WHITE + level + ChatColor.GRAY + " / " + max);
            }
            return true;
        }
    }

    // =====================================================================
    //  SkillsAdminCommand - /skillsadmin <set|add|reset> <player> [skill] [amount]
    // =====================================================================
    static class SkillsAdminCommand implements CommandExecutor, TabCompleter {

        private static final List<String> SUBCOMMANDS = List.of("set", "add", "reset");

        private final SkillKillerPlugin plugin;
        private final SkillManager skillManager;

        SkillsAdminCommand(SkillKillerPlugin plugin, SkillManager skillManager) {
            this.plugin = plugin;
            this.skillManager = skillManager;
        }

        @Override
        @SuppressWarnings("deprecation") // name-based offline player lookup; acceptable for an admin console command
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /skillsadmin <set|add|reset> <player> [skill] [amount]");
                return true;
            }

            String action = args[0].toLowerCase();
            OfflinePlayer target = plugin.getServer().getOfflinePlayer(args[1]);
            UUID uuid = target.getUniqueId();

            if (action.equals("reset")) {
                for (Skill skill : Skill.values()) {
                    skillManager.setLevel(uuid, skill, 0);
                }
                sender.sendMessage(ChatColor.YELLOW + "Reset all skills for " + args[1] + ".");
                return true;
            }

            if (!action.equals("set") && !action.equals("add")) {
                sender.sendMessage(ChatColor.RED + "Unknown action. Use set, add, or reset.");
                return true;
            }

            if (args.length < 4) {
                sender.sendMessage(ChatColor.RED + "Usage: /skillsadmin " + action + " <player> <skill> <amount>");
                return true;
            }

            Skill skill = parseSkill(args[2]);
            if (skill == null) {
                sender.sendMessage(ChatColor.RED + "Unknown skill. Valid: "
                        + Arrays.stream(Skill.values()).map(Skill::configKey).collect(Collectors.joining(", ")));
                return true;
            }

            int amount;
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "Amount must be a whole number.");
                return true;
            }

            int newLevel = action.equals("set")
                    ? skillManager.setLevel(uuid, skill, amount)
                    : skillManager.changeLevel(uuid, skill, amount);

            sender.sendMessage(ChatColor.GREEN + args[1] + "'s " + skillManager.getDisplayName(skill)
                    + " is now level " + newLevel + ".");
            return true;
        }

        private Skill parseSkill(String input) {
            for (Skill skill : Skill.values()) {
                if (skill.configKey().equalsIgnoreCase(input)) {
                    return skill;
                }
            }
            return null;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            if (args.length == 1) {
                return filter(SUBCOMMANDS, args[0]);
            }
            if (args.length == 2) {
                List<String> names = plugin.getServer().getOnlinePlayers().stream()
                        .map(Player::getName)
                        .collect(Collectors.toList());
                return filter(names, args[1]);
            }
            if (args.length == 3 && !args[0].equalsIgnoreCase("reset")) {
                List<String> skillNames = Arrays.stream(Skill.values()).map(Skill::configKey).collect(Collectors.toList());
                return filter(skillNames, args[2]);
            }
            return new ArrayList<>();
        }

        private List<String> filter(List<String> options, String prefix) {
            return options.stream()
                    .filter(option -> option.toLowerCase().startsWith(prefix.toLowerCase()))
                    .collect(Collectors.toList());
        }
    }
}
