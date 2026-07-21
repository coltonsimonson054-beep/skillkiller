package com.smp.skillkiller.listeners;

import com.smp.skillkiller.SkillManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Keeps skill effects in sync with the player's actual state: (re)applies
 * them on join and respawn, and instantly restores potion-based skills after
 * a milk bucket wipes all potion effects (vanilla behavior).
 */
public class PlayerLifecycleListener implements Listener {

    private final JavaPlugin plugin;
    private final SkillManager skillManager;

    public PlayerLifecycleListener(JavaPlugin plugin, SkillManager skillManager) {
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
