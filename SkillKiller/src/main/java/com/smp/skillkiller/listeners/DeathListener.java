package com.smp.skillkiller.listeners;

import com.smp.skillkiller.Skill;
import com.smp.skillkiller.SkillKillerPlugin;
import com.smp.skillkiller.SkillManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * The heart of the plugin: when a player kills another player, one random
 * enabled skill is rolled. The killer gains a level in it, the victim loses one.
 */
public class DeathListener implements Listener {

    private final SkillKillerPlugin plugin;
    private final SkillManager skillManager;
    private final Random random = new Random();

    public DeathListener(SkillKillerPlugin plugin, SkillManager skillManager) {
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
