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
import org.bukkit.event.entity.PlayerDeathEvent;
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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public final class PinnacleAfkPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final String LEGACY_SHARED_AFK_TEAM_NAME = "pinnacleafk";
    private static final Pattern LEGACY_PER_PLAYER_AFK_TEAM_NAME = Pattern.compile("^pafk_[0-9a-f]{11}$");
    private static final float AFK_MARKER_HEIGHT_OFFSET = 2.6F;
    private static final int DEFAULT_INVINCIBLE_AFTER_SECONDS = 10;
    private static final int DEFAULT_TOGGLE_COOLDOWN_SECONDS = 3;

    private final Map<UUID, AfkState> afkPlayers = new HashMap<>();
    private final Map<UUID, Long> lastToggleNanos = new HashMap<>();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();
    private int invincibleAfterSeconds;
    private int toggleCooldownSeconds;
    private int afkReconcileTaskId = -1;

    @Override
    public void onEnable() {
        loadAndValidateConfig();
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
        stopAfkReconcileTask();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isAfk(player)) {
                setAfk(player, false, false);
            }
        }
        afkPlayers.clear();
        lastToggleNanos.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(message("messages.only-player", null));
            return true;
        }

        if (isToggleOnCooldown(player)) {
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
    public void onPlayerDeath(PlayerDeathEvent event) {
        clearAfkState(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        // Failsafe for deaths or respawns initiated by other plugins outside the normal lifecycle.
        clearAfkState(event.getPlayer());
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
        if (state == null) {
            return;
        }

        activateProtectionIfDue(player, state, System.nanoTime());
        if (state.invincible) {
            event.setCancelled(true);
            event.setDamage(0.0D);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        lastToggleNanos.remove(player.getUniqueId());
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
        Component originalPlayerListName = player.playerListName();
        Component basePlayerListName = playerListNameOrUsername(player, originalPlayerListName);
        Component appliedPlayerListName = formatPlayerListName(basePlayerListName);
        AfkState state = new AfkState(
                player.getLocation().clone(),
                originalPlayerListName,
                appliedPlayerListName,
                afkDisplay.getUniqueId()
        );

        afkPlayers.put(player.getUniqueId(), state);
        ensureAfkReconcileTask();

        // Stop actions that began before the player entered AFK mode.
        player.clearActiveItem();
        player.closeInventory();

        player.playerListName(appliedPlayerListName);

        if (invincibleAfterSeconds == 0) {
            state.invincible = true;
            if (notify) {
                player.sendMessage(message("messages.invincible-now", player));
            }
        } else {
            state.protectionDeadlineNanos = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(invincibleAfterSeconds);
        }

        if (notify) {
            player.sendMessage(message("messages.now-afk", player));
            broadcast(message("messages.broadcast-now-afk", player));
            if (invincibleAfterSeconds > 0) {
                player.sendMessage(messageWithSeconds(
                        "messages.already-protected-delay",
                        player,
                        invincibleAfterSeconds
                ));
            }
        }
    }

    private void disableAfk(Player player, boolean notify) {
        AfkState state = afkPlayers.remove(player.getUniqueId());
        if (state == null) {
            return;
        }

        removeAfkDisplay(state);
        if (Objects.equals(player.playerListName(), state.appliedPlayerListName)) {
            player.playerListName(state.originalPlayerListName);
        }
        stopAfkReconcileTaskIfIdle();

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

    private boolean isToggleOnCooldown(Player player) {
        if (toggleCooldownSeconds == 0) {
            return false;
        }

        long now = System.nanoTime();
        long cooldownNanos = TimeUnit.SECONDS.toNanos(toggleCooldownSeconds);
        Long lastToggle = lastToggleNanos.get(player.getUniqueId());

        if (lastToggle != null) {
            long remainingNanos = cooldownNanos - (now - lastToggle);
            if (remainingNanos > 0L) {
                long secondNanos = TimeUnit.SECONDS.toNanos(1L);
                int remainingSeconds = (int) Math.max(
                        1L,
                        Math.min(
                                Integer.MAX_VALUE,
                                (remainingNanos + secondNanos - 1L) / secondNanos
                        )
                );
                player.sendMessage(messageWithSeconds(
                        "messages.toggle-cooldown",
                        player,
                        remainingSeconds
                ));
                return true;
            }
        }

        lastToggleNanos.put(player.getUniqueId(), now);
        return false;
    }

    private void ensureAfkReconcileTask() {
        if (afkReconcileTaskId != -1) {
            return;
        }

        afkReconcileTaskId = Bukkit.getScheduler()
                .runTaskTimer(this, this::reconcileAfkState, 1L, 1L)
                .getTaskId();
    }

    private void stopAfkReconcileTaskIfIdle() {
        if (afkPlayers.isEmpty()) {
            stopAfkReconcileTask();
        }
    }

    private void stopAfkReconcileTask() {
        if (afkReconcileTaskId == -1) {
            return;
        }

        Bukkit.getScheduler().cancelTask(afkReconcileTaskId);
        afkReconcileTaskId = -1;
    }

    private void reconcileAfkState() {
        long now = System.nanoTime();

        for (Map.Entry<UUID, AfkState> entry : afkPlayers.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }

            AfkState state = entry.getValue();
            activateProtectionIfDue(player, state, now);
            reconcilePlayerListName(player, state);
        }
    }

    private void activateProtectionIfDue(Player player, AfkState state, long now) {
        if (state.invincible
                || state.protectionDeadlineNanos == -1L
                || now - state.protectionDeadlineNanos < 0L) {
            return;
        }

        state.invincible = true;
        state.protectionDeadlineNanos = -1L;
        player.sendMessage(message("messages.invincible-now", player));
    }

    private void reconcilePlayerListName(Player player, AfkState state) {
        Component currentPlayerListName = player.playerListName();
        if (Objects.equals(currentPlayerListName, state.appliedPlayerListName)) {
            return;
        }

        // Treat a value PinnacleAFK did not apply as the latest formatting owned
        // by the server or another plugin, then wrap it with the AFK indicator.
        state.originalPlayerListName = currentPlayerListName;
        state.appliedPlayerListName = formatPlayerListName(
                playerListNameOrUsername(player, currentPlayerListName)
        );
        player.playerListName(state.appliedPlayerListName);
    }

    private Component playerListNameOrUsername(Player player, Component playerListName) {
        return playerListName != null ? playerListName : Component.text(player.getName());
    }

    private Component formatPlayerListName(Component playerListName) {
        String raw = configString("display.tab-format");
        String placeholder = "%player%";
        Component result = Component.empty();
        int cursor = 0;
        int placeholderIndex;

        while ((placeholderIndex = raw.indexOf(placeholder, cursor)) >= 0) {
            result = result
                    .append(legacy.deserialize(raw.substring(cursor, placeholderIndex)))
                    .append(playerListName);
            cursor = placeholderIndex + placeholder.length();
        }

        return result.append(legacy.deserialize(raw.substring(cursor)));
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

    private void loadAndValidateConfig() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);

        invincibleAfterSeconds = readNonNegativeSeconds(
                "invincible-after-seconds",
                DEFAULT_INVINCIBLE_AFTER_SECONDS
        );
        toggleCooldownSeconds = readNonNegativeSeconds(
                "toggle-cooldown-seconds",
                DEFAULT_TOGGLE_COOLDOWN_SECONDS
        );

        // Persist newly introduced defaults and any corrected invalid values.
        saveConfig();
    }

    private int readNonNegativeSeconds(String path, int safeDefault) {
        Object configured = getConfig().get(path);
        if (configured instanceof Number number) {
            double decimalValue = number.doubleValue();
            long wholeValue = number.longValue();
            if (Double.isFinite(decimalValue)
                    && decimalValue == wholeValue
                    && wholeValue >= 0L
                    && wholeValue <= Integer.MAX_VALUE) {
                return (int) wholeValue;
            }
        }

        getLogger().warning(
                "Invalid " + path + " value '" + configured
                        + "'; using the safe default of " + safeDefault + " seconds."
        );
        getConfig().set(path, safeDefault);
        return safeDefault;
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

    private void clearAfkState(Player player) {
        if (isAfk(player)) {
            setAfk(player, false, false);
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
        String raw = configString(path);
        if (player != null) {
            raw = raw.replace("%player%", player.getName());
        }
        raw = raw.replace("%seconds%", String.valueOf(seconds));
        return legacy.deserialize(raw);
    }

    private Component format(String path, Player player) {
        String raw = configString(path);
        if (player != null) {
            raw = raw.replace("%player%", player.getName());
        }
        return legacy.deserialize(raw);
    }

    private String configString(String path) {
        String configured = getConfig().getString(path);
        if (configured != null) {
            return configured;
        }

        if (getConfig().getDefaults() != null) {
            String bundledDefault = getConfig().getDefaults().getString(path);
            if (bundledDefault != null) {
                return bundledDefault;
            }
        }

        getLogger().warning("Missing string configuration value: " + path);
        return "";
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
        private Component originalPlayerListName;
        private Component appliedPlayerListName;
        private final UUID afkDisplayEntityId;
        private long protectionDeadlineNanos = -1L;
        private boolean invincible = false;

        private AfkState(
                Location lockLocation,
                Component originalPlayerListName,
                Component appliedPlayerListName,
                UUID afkDisplayEntityId
        ) {
            this.lockLocation = lockLocation;
            this.originalPlayerListName = originalPlayerListName;
            this.appliedPlayerListName = appliedPlayerListName;
            this.afkDisplayEntityId = afkDisplayEntityId;
        }
    }
}
