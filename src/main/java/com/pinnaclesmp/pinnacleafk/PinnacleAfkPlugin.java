package com.pinnaclesmp.pinnacleafk;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PinnacleAfkPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final String LEGACY_SHARED_AFK_TEAM_NAME = "pinnacleafk";
    private static final String AFK_TEAM_PREFIX = "pafk_";

    private final Map<UUID, AfkState> afkPlayers = new HashMap<>();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        cleanupLegacySharedAfkTeam();

        Bukkit.getPluginManager().registerEvents(this, this);

        PluginCommand afkCommand = getCommand("afk");
        if (afkCommand != null) {
            afkCommand.setExecutor(this);
            afkCommand.setTabCompleter(this);
        }
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isAfk(player)) {
                setAfk(player, false, false);
            }
        }
        afkPlayers.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(message("messages.only-player", null));
            return true;
        }

        setAfk(player, !isAfk(player), true);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        AfkState state = afkPlayers.get(player.getUniqueId());
        if (state == null) {
            return;
        }

        Location to = event.getTo();
        if (to == null) {
            return;
        }

        Location locked = state.lockLocation.clone();

        if (samePosition(locked, to)) {
            return;
        }

        // Keep the player locked in place, but still allow them to look around.
        locked.setYaw(to.getYaw());
        locked.setPitch(to.getPitch());
        event.setTo(locked);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        cancelAfkAction(event.getPlayer(), event);
    }

    // PlayerPortalEvent has its own handler list and must be handled separately.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerPortal(PlayerPortalEvent event) {
        cancelAfkAction(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (isAfk(player)) {
            // A world change that bypassed the cancellable teleport events cannot safely
            // retain a lock location from the previous world.
            setAfk(player, false, true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        cancelAfkAction(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        cancelAfkAction(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        cancelAfkAction(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockDamage(BlockDamageEvent event) {
        cancelAfkAction(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        cancelAfkAction(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockPlace(BlockPlaceEvent event) {
        cancelAfkAction(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onAfkPlayerDamageEntity(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Player player) {
            cancelAfkAction(player, event);
            return;
        }

        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            cancelAfkAction(player, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player) {
            cancelAfkAction(player, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            cancelAfkAction(player, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            cancelAfkAction(player, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            cancelAfkAction(player, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        cancelAfkAction(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        cancelAfkAction(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        cancelAfkAction(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerFish(PlayerFishEvent event) {
        cancelAfkAction(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player player)) {
            return;
        }

        AfkState state = afkPlayers.get(player.getUniqueId());
        if (state != null && state.invincible) {
            event.setCancelled(true);
            event.setDamage(0.0D);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (isAfk(player)) {
            setAfk(player, false, false);
        }
    }

    private void setAfk(Player player, boolean afk, boolean notify) {
        if (afk) {
            enableAfk(player, notify);
        } else {
            disableAfk(player, notify);
        }
    }

    private void enableAfk(Player player, boolean notify) {
        if (isAfk(player)) {
            return;
        }

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String entry = player.getName();
        Team previousTeam = scoreboard.getEntryTeam(entry);
        String temporaryTeamName = temporaryAfkTeamName(player);

        Team existingTemporaryTeam = scoreboard.getTeam(temporaryTeamName);
        if (existingTemporaryTeam != null) {
            existingTemporaryTeam.unregister();
        }

        Team afkTeam = scoreboard.registerNewTeam(temporaryTeamName);
        setupTemporaryAfkTeam(player, previousTeam, afkTeam);

        AfkState state = new AfkState(
                player.getLocation().clone(),
                player.playerListName(),
                previousTeam == null ? null : previousTeam.getName(),
                temporaryTeamName,
                getConfig().getBoolean("display.use-player-list-name", false)
        );

        afkPlayers.put(player.getUniqueId(), state);

        // Stop actions that began before the player entered AFK mode.
        player.clearActiveItem();
        player.closeInventory();

        // Adding the entry automatically removes it from the old team on this scoreboard.
        afkTeam.addEntry(entry);

        if (state.usedPlayerListName) {
            player.playerListName(format("display.tab-format", player));
        }

        int delaySeconds = Math.max(0, getConfig().getInt("invincible-after-seconds", 10));
        if (delaySeconds <= 0) {
            state.invincible = true;
            if (notify) {
                player.sendMessage(message("messages.invincible-now", player));
            }
        } else {
            long delayTicks = delaySeconds * 20L;
            state.invincibleTaskId = Bukkit.getScheduler().runTaskLater(this, () -> {
                AfkState currentState = afkPlayers.get(player.getUniqueId());
                if (currentState != null) {
                    currentState.invincible = true;
                    player.sendMessage(message("messages.invincible-now", player));
                }
            }, delayTicks).getTaskId();
        }

        if (notify) {
            player.sendMessage(message("messages.now-afk", player));
            broadcast(message("messages.broadcast-now-afk", player));
            if (delaySeconds > 0) {
                player.sendMessage(messageWithSeconds("messages.already-protected-delay", player, delaySeconds));
            }
        }
    }

    private void disableAfk(Player player, boolean notify) {
        AfkState state = afkPlayers.remove(player.getUniqueId());
        if (state == null) {
            return;
        }

        if (state.invincibleTaskId != -1) {
            Bukkit.getScheduler().cancelTask(state.invincibleTaskId);
        }

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String entry = player.getName();

        Team temporaryAfkTeam = scoreboard.getTeam(state.temporaryTeamName);
        if (temporaryAfkTeam != null) {
            temporaryAfkTeam.removeEntry(entry);
        }

        if (state.previousTeamName != null) {
            Team previousTeam = scoreboard.getTeam(state.previousTeamName);
            if (previousTeam != null) {
                previousTeam.addEntry(entry);
            }
        }

        if (temporaryAfkTeam != null) {
            temporaryAfkTeam.unregister();
        }

        if (state.usedPlayerListName) {
            player.playerListName(state.originalPlayerListName);
        }

        if (notify) {
            player.sendMessage(message("messages.no-longer-afk", player));
            broadcast(message("messages.broadcast-no-longer-afk", player));
        }
    }

    private void broadcast(Component component) {
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.sendMessage(component);
        }
    }

    private void setupTemporaryAfkTeam(Player player, Team previousTeam, Team afkTeam) {
        Component afkPrefix = format("display.nametag-prefix", player);
        Component afkSuffix = format("display.nametag-suffix", player);

        if (previousTeam == null) {
            afkTeam.prefix(afkPrefix);
            afkTeam.suffix(afkSuffix);
            return;
        }

        copyTeamSettings(previousTeam, afkTeam);
        afkTeam.prefix(previousTeam.prefix().append(afkPrefix));
        afkTeam.suffix(afkSuffix.append(previousTeam.suffix()));
    }

    private void copyTeamSettings(Team source, Team target) {
        target.displayName(source.displayName());
        target.setAllowFriendlyFire(source.allowFriendlyFire());
        target.setCanSeeFriendlyInvisibles(source.canSeeFriendlyInvisibles());
        target.setColor(source.getColor());

        for (Team.Option option : Team.Option.values()) {
            target.setOption(option, source.getOption(option));
        }
    }

    private String temporaryAfkTeamName(Player player) {
        String compactUuid = player.getUniqueId().toString().replace("-", "");
        return AFK_TEAM_PREFIX + compactUuid.substring(0, 11);
    }

    private void cleanupLegacySharedAfkTeam() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team legacyTeam = scoreboard.getTeam(LEGACY_SHARED_AFK_TEAM_NAME);
        if (legacyTeam == null) {
            return;
        }

        Set<String> legacyEntries = new HashSet<>(legacyTeam.getEntries());
        legacyTeam.unregister();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (legacyEntries.contains(player.getName())) {
                player.playerListName(null);
            }
        }

        getLogger().info("Removed legacy shared AFK scoreboard team from older PinnacleAFK versions.");
    }

    private boolean isAfk(Player player) {
        return afkPlayers.containsKey(player.getUniqueId());
    }

    private void cancelAfkAction(Player player, Cancellable event) {
        if (isAfk(player)) {
            event.setCancelled(true);
        }
    }

    private Component message(String path, Player player) {
        return format(path, player);
    }

    private Component messageWithSeconds(String path, Player player, int seconds) {
        String raw = getConfig().getString(path, "");
        if (player != null) {
            raw = raw.replace("%player%", player.getName());
        }
        raw = raw.replace("%seconds%", String.valueOf(seconds));
        return legacy.deserialize(raw);
    }

    private Component format(String path, Player player) {
        String raw = getConfig().getString(path, "");
        if (player != null) {
            raw = raw.replace("%player%", player.getName());
        }
        return legacy.deserialize(raw);
    }

    private boolean samePosition(Location first, Location second) {
        World firstWorld = first.getWorld();
        World secondWorld = second.getWorld();

        if (firstWorld == null || secondWorld == null || !firstWorld.equals(secondWorld)) {
            return false;
        }

        return Math.abs(first.getX() - second.getX()) < 0.0001D
                && Math.abs(first.getY() - second.getY()) < 0.0001D
                && Math.abs(first.getZ() - second.getZ()) < 0.0001D;
    }

    private static final class AfkState {
        private final Location lockLocation;
        private final Component originalPlayerListName;
        private final String previousTeamName;
        private final String temporaryTeamName;
        private final boolean usedPlayerListName;
        private int invincibleTaskId = -1;
        private boolean invincible = false;

        private AfkState(Location lockLocation, Component originalPlayerListName, String previousTeamName, String temporaryTeamName, boolean usedPlayerListName) {
            this.lockLocation = lockLocation;
            this.originalPlayerListName = originalPlayerListName;
            this.previousTeamName = previousTeamName;
            this.temporaryTeamName = temporaryTeamName;
            this.usedPlayerListName = usedPlayerListName;
        }
    }
}
