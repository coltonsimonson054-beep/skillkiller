# SkillKiller

A Paper plugin for **Minecraft 26.2** (Java Edition). Kill another player and
you permanently steal a level in a random skill from them — forever, until
someone takes it back from you.

## The mechanic

- Killer of a player fight (`Player#getKiller()` must be a `Player`) triggers
  the effect. NPC/mob/environment deaths do nothing.
- One random *enabled* skill is rolled. By default the **same** skill is used
  for both sides: killer gets `+1`, victim gets `-1` (never below 0). Set
  `same-skill-for-both: false` in config.yml if you'd rather each side roll
  independently.
- Levels are stored per-player, forever, in `playerdata.yml` — they survive
  restarts, `/reload`, and relogging.
- Skills apply automatically as the matching potion effect or attribute
  bonus, and reapply on join/respawn.

### First-join starter kit

The first time a player joins, a clean chest-style GUI pops up (after a
1-second delay) letting them spend a few free points (default: 3) directly
into whichever skills they want — click an icon, it applies immediately.
If they close it early, the leftover points are saved and they get a
clickable chat reminder on their next join; `/skillmenu` reopens it anytime
they still have points to spend. Configurable under `starter-kit:` in
config.yml (can be disabled entirely, and the point count/menu title are
adjustable).

### Built-in skills

| Skill | Effect |
|---|---|
| Mining | Haste I, II, III... per level |
| Speed | Speed I, II, III... per level |
| Health | +max health (default 1.0 HP/level = half a heart) |
| Strength | +melee attack damage (default 0.5/level) |
| Regeneration | Regeneration I, II... per level |
| Luck | Luck I, II... per level (loot/fishing) |

Every skill can be individually disabled, renamed, and capped at a max level
in `config.yml`, and the messages/broadcasts are fully templated.

## Building it

You'll need **JDK 25** and **Maven** installed locally (this project can't be
compiled in this sandbox since it has no network access to pull the Paper
API from PaperMC's repository).

```bash
cd SkillKiller
mvn clean package
```

The finished jar will be at `target/SkillKiller-1.0.0.jar`.

## Installing it

1. Your server must be running **Paper 26.2** on **Java 25** (Java 25 is
   required as of the 26.1/26.2 update — check with `java -version`).
2. Drop `SkillKiller-1.0.0.jar` into your server's `plugins/` folder.
3. Start the server once to generate `plugins/SkillKiller/config.yml`.
4. Edit the config to taste, then `/reload` or restart.

## Commands & permissions

| Command | Permission | Default | Description |
|---|---|---|---|
| `/skills [player]` | `skillkiller.use` | everyone | View your (or another player's) skill levels |
| `/skillmenu` | `skillkiller.use` | everyone | Reopen the point-allocation menu, if you have unspent points |
| `/skillsadmin set <player> <skill> <amount>` | `skillkiller.admin` | op | Set a skill to an exact level |
| `/skillsadmin add <player> <skill> <amount>` | `skillkiller.admin` | op | Add (or, with a negative number, subtract) levels |
| `/skillsadmin reset <player>` | `skillkiller.admin` | op | Reset every skill for a player to 0 |

`skillkiller.exempt` — give this to a player and kills involving them (either
direction) never transfer a skill level. Handy for staff/admins.

## Notes on maintenance

- **Milk buckets** clear all vanilla potion effects, which would otherwise
  wipe Mining/Speed/Regeneration/Luck. This plugin instantly restores them
  after a milk bucket is drunk (toggle: `protect-from-milk`).
- A periodic safety-net task (`reapply-interval-seconds`, default 60s)
  silently reapplies potion-based skills to online players in case some
  other plugin or datapack strips effects some other way.
- Health/Strength are implemented as attribute modifiers (not potion
  effects), so they're unaffected by milk and don't show a HUD icon.
- To add a 7th skill, see the comment block at the top of `Skill.java` — it
  walks through the 4 places you need to touch.

## Project layout

Everything is one file, `SkillKillerPlugin.java`, organized as static nested
classes so it's easy to copy-paste into a single class in an IDE:

```
src/main/java/com/smp/skillkiller/
  SkillKillerPlugin.java   - the whole plugin:
                               SkillKillerPlugin (startup/shutdown/wiring)
                               Skill               (the skill enum)
                               PlayerSkills        (one player's levels)
                               DataStore           (load/save playerdata.yml)
                               SkillManager        (effects + level changes)
                               DeathListener       (the kill mechanic)
                               PlayerLifecycleListener (join/respawn/milk)
                               SkillsCommand       (/skills)
                               SkillsAdminCommand  (/skillsadmin)
src/main/resources/
  plugin.yml
  config.yml
```
