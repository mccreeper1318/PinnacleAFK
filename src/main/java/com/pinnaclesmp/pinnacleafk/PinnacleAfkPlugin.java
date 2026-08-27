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
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Transformation;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class PinnacleAfkPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final String LEGACY_SHARED_AFK_TEAM_NAME = "pinnacleafk";
    private static final Pattern LEGACY_PER_PLAYER_AFK_TEAM_NAME = Pattern.compile("^pafk_[0-9a-f]{11}$");
    private static final float AFK_MARKER_HEIGHT_OFFSET = 2.6F;

    private final Map<UUID, AfkState> afkPlayers = new HashMap<>();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        cleanupLegacyAfkTeams();

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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        AfkState state = afkPlayers.get(player.getUniqueId());
        if (state == null) {
            return;
        }

        state.lockLocation = event.getRespawnLocation().clone();

        // Respawning can dismount the display. Wait until the player has fully respawned,
        // then replace and reattach the marker at the actual destination.
        Bukkit.getScheduler().runTask(this, () -> {
            AfkState currentState = afkPlayers.get(player.getUniqueId());
            if (currentState != state || !player.isOnline()) {
                return;
            }

            currentState.lockLocation = player.getLocation().clone();
            removeAfkDisplay(currentState);
            currentState.afkDisplayEntityId = createAfkDisplay(player).getUniqueId();
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        cancelAfkAction(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        cancelAfkAction(event.getPlayer(), event);
    }

    // PlayerInteractAtEntityEvent has its own handler list and must be handled separately.
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

    // InventoryCreativeEvent has its own handler list and must be handled separately.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryCreative(InventoryCreativeEvent event) {
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
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            cancelAfkAction(player, event);
        }
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

        TextDisplay afkDisplay = createAfkDisplay(player);
        AfkState state = new AfkState(
                player.getLocation().clone(),
                player.playerListName(),
                afkDisplay.getUniqueId()
        );

        afkPlayers.put(player.getUniqueId(), state);

        // Stop actions that began before the player entered AFK mode.
        player.clearActiveItem();
        player.closeInventory();

        player.playerListName(format("display.tab-format", player));

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

        removeAfkDisplay(state);
        player.playerListName(state.originalPlayerListName);

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

    private TextDisplay createAfkDisplay(Player player) {
        Location displayLocation = player.getLocation().clone();
        Component marker = format("display.nametag-prefix", player)
                .append(format("display.nametag-suffix", player));

        TextDisplay display = player.getWorld().spawn(displayLocation, TextDisplay.class, spawnedDisplay -> {
            spawnedDisplay.text(marker);

            Transformation transformation = spawnedDisplay.getTransformation();
            transformation.getTranslation().set(0.0F, AFK_MARKER_HEIGHT_OFFSET, 0.0F);
            spawnedDisplay.setTransformation(transformation);

            spawnedDisplay.setBillboard(Display.Billboard.CENTER);
            spawnedDisplay.setAlignment(TextDisplay.TextAlignment.CENTER);
            spawnedDisplay.setDefaultBackground(false);
            spawnedDisplay.setSeeThrough(true);
            spawnedDisplay.setShadowed(true);
            spawnedDisplay.setGravity(false);
            spawnedDisplay.setInvulnerable(true);
            spawnedDisplay.setPersistent(false);
            spawnedDisplay.setSilent(true);
        });

        player.addPassenger(display);
        return display;
    }

    private void removeAfkDisplay(AfkState state) {
        Entity display = Bukkit.getEntity(state.afkDisplayEntityId);
        if (display != null) {
            display.remove();
        }
    }

    private void cleanupLegacyAfkTeams() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        Team legacySharedTeam = scoreboard.getTeam(LEGACY_SHARED_AFK_TEAM_NAME);
        if (legacySharedTeam != null) {
            Set<String> legacyEntries = new HashSet<>(legacySharedTeam.getEntries());
            legacySharedTeam.unregister();

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (legacyEntries.contains(player.getName())) {
                    player.playerListName(null);
                }
            }

            getLogger().info("Removed legacy shared AFK scoreboard team from older PinnacleAFK versions.");
        }

        int removedPerPlayerTeams = 0;
        for (Team team : new HashSet<>(scoreboard.getTeams())) {
            if (!LEGACY_PER_PLAYER_AFK_TEAM_NAME.matcher(team.getName()).matches()) {
                continue;
            }

            team.unregister();
            removedPerPlayerTeams++;
        }

        if (removedPerPlayerTeams > 0) {
            getLogger().info(
                    "Removed " + removedPerPlayerTeams
                            + " legacy per-player AFK scoreboard team(s) left by older PinnacleAFK versions."
            );
        }
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
        private Location lockLocation;
        private final Component originalPlayerListName;
        private UUID afkDisplayEntityId;
        private int invincibleTaskId = -1;
        private boolean invincible = false;

        private AfkState(Location lockLocation, Component originalPlayerListName, UUID afkDisplayEntityId) {
            this.lockLocation = lockLocation;
            this.originalPlayerListName = originalPlayerListName;
            this.afkDisplayEntityId = afkDisplayEntityId;
        }
    }
}
