package com.smp.skillkiller.commands;

import com.smp.skillkiller.DataStore;
import com.smp.skillkiller.PlayerSkills;
import com.smp.skillkiller.Skill;
import com.smp.skillkiller.SkillKillerPlugin;
import com.smp.skillkiller.SkillManager;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/** /skills [player] - shows a player's current level in every enabled skill. */
public class SkillsCommand implements CommandExecutor {

    private final SkillKillerPlugin plugin;
    private final SkillManager skillManager;
    private final DataStore dataStore;

    public SkillsCommand(SkillKillerPlugin plugin, SkillManager skillManager, DataStore dataStore) {
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
