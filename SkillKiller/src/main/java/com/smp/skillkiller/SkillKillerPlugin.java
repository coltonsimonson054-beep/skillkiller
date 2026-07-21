package com.smp.skillkiller;

import com.smp.skillkiller.commands.SkillsAdminCommand;
import com.smp.skillkiller.commands.SkillsCommand;
import com.smp.skillkiller.listeners.DeathListener;
import com.smp.skillkiller.listeners.PlayerLifecycleListener;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;

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
        SkillsAdminCommand adminCommand = new SkillsAdminCommand(this, skillManager, dataStore);
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
}
