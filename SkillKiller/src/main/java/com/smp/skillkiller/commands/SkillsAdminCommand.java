package com.smp.skillkiller.commands;

import com.smp.skillkiller.DataStore;
import com.smp.skillkiller.Skill;
import com.smp.skillkiller.SkillKillerPlugin;
import com.smp.skillkiller.SkillManager;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** /skillsadmin <set|add|reset> <player> [skill] [amount] - admin-only level management. */
public class SkillsAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("set", "add", "reset");

    private final SkillKillerPlugin plugin;
    private final SkillManager skillManager;

    public SkillsAdminCommand(SkillKillerPlugin plugin, SkillManager skillManager, DataStore dataStore) {
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
                    .map(p -> p.getName())
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
