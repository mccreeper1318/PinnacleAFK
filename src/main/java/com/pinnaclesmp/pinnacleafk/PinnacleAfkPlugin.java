package com.pinnaclesmp.pinnacleafk;

import io.papermc.paper.event.player.AsyncChatEvent;
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
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Transformation;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class PinnacleAfkPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final String LEGACY_SHARED_AFK_TEAM_NAME = "pinnacleafk";
    private static final Pattern LEGACY_PER_PLAYER_AFK_TEAM_NAME = Pattern.compile("^pafk_[0-9a-f]{11}$");
    private static final float AFK_MARKER_HEIGHT_OFFSET = 2.6F;

    private final Map<UUID, AfkState> afkPlayers = new HashMap<>();
    private final Map<UUID, Long> lastToggleNanos = new HashMap<>();
    private final Map<UUID, Long> lastActivityNanos = new ConcurrentHashMap<>();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();
    private AfkSettings settings;
    private int afkReconcileTaskId = -1;
    private int automaticAfkTaskId = -1;

    @Override
    public void onEnable() {
        loadAndValidateConfig();
        cleanupLegacyAfkTeams();

        Bukkit.getPluginManager().registerEvents(this, this);
        registerCommand("afk");
        registerCommand("pafk");
        restartAutomaticAfkTask();
    }

    @Override
    public void onDisable() {
        stopAfkReconcileTask();
        stopAutomaticAfkTask();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isAfk(player)) {
                setAfk(player, false, false);
            }
        }
        afkPlayers.clear();
        lastToggleNanos.clear();
        lastActivityNanos.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("pafk")) {
            return handleAdminCommand(sender, args);
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(message("messages.only-player", null));
            return true;
        }

        boolean enteringAfk = !isAfk(player);
        if (enteringAfk && !settings.allowsAfkWorld(player.getWorld().getName())) {
            player.sendMessage(message("messages.world-not-allowed", player));
            return true;
        }

        if (isToggleOnCooldown(player)) {
            return true;
        }

        setAfk(player, enteringAfk, true);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("pafk") || !sender.hasPermission("pinnacleafk.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return PafkAction.completions(args[0]);
        }

        if (args.length == 2
                && (args[0].equalsIgnoreCase("status") || args[0].equalsIgnoreCase("remove"))) {
            String prefix = args[1].toLowerCase(java.util.Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(java.util.Locale.ROOT).startsWith(prefix))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }

        return Collections.emptyList();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        AfkState state = afkPlayers.get(player.getUniqueId());
        if (state == null) {
            if (!samePosition(event.getFrom(), to)) {
                recordActivity(player);
            }
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
        } else {
            recordActivity(player);
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
        recordActivity(event.getPlayer());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        recordActivity(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!isAfk(event.getPlayer())) {
            recordActivity(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        // AsyncChatEvent can run away from the server thread, so only update the
        // thread-safe activity clock here. AFK state is evaluated by the main-thread scan.
        lastActivityNanos.put(event.getPlayer().getUniqueId(), System.nanoTime());
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
        if (event.getEntity() instanceof Player player && isAfk(player)) {
            // Pickup is passive and must not reset the inactivity timer for active players.
            event.setCancelled(true);
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

        activateProtectionIfDue(player, state, System.nanoTime(), true);
        if (state.invincible) {
            event.setCancelled(true);
            event.setDamage(0.0D);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        lastToggleNanos.remove(player.getUniqueId());
        lastActivityNanos.remove(player.getUniqueId());
        if (isAfk(player)) {
            setAfk(player, false, false);
        }
    }

    private void registerCommand(String commandName) {
        PluginCommand pluginCommand = getCommand(commandName);
        if (pluginCommand == null) {
            getLogger().severe("Command '" + commandName + "' is missing from plugin.yml.");
            return;
        }

        pluginCommand.setExecutor(this);
        pluginCommand.setTabCompleter(this);
    }

    private boolean handleAdminCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("pinnacleafk.admin")) {
            sender.sendMessage(message("messages.no-permission", null));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(message("messages.admin-usage", null));
            return true;
        }

        PafkAction action = PafkAction.parse(args[0]).orElse(null);
        if (action == null) {
            sender.sendMessage(message("messages.admin-usage", null));
            return true;
        }

        return switch (action) {
            case RELOAD -> {
                if (args.length != 1) {
                    sender.sendMessage(message("messages.admin-usage", null));
                } else {
                    reloadPluginConfig(sender);
                }
                yield true;
            }
            case LIST -> {
                if (args.length != 1) {
                    sender.sendMessage(message("messages.admin-usage", null));
                } else {
                    sendAfkList(sender);
                }
                yield true;
            }
            case STATUS -> {
                if (args.length != 2) {
                    sender.sendMessage(message("messages.admin-usage", null));
                } else {
                    sendAfkStatus(sender, args[1]);
                }
                yield true;
            }
            case REMOVE -> {
                if (args.length != 2) {
                    sender.sendMessage(message("messages.admin-usage", null));
                } else {
                    removeAfkPlayer(sender, args[1]);
                }
                yield true;
            }
        };
    }

    private void sendAfkList(CommandSender sender) {
        List<String> afkNames = afkPlayers.keySet().stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .map(Player::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        if (afkNames.isEmpty()) {
            sender.sendMessage(message("messages.admin-list-empty", null));
            return;
        }

        sender.sendMessage(messageWithValues(
                "messages.admin-list",
                Map.of(
                        "count", String.valueOf(afkNames.size()),
                        "players", String.join(", ", afkNames)
                )
        ));
    }

    private void sendAfkStatus(CommandSender sender, String playerName) {
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage(messageWithValues(
                    "messages.player-not-found",
                    Map.of("player", playerName)
            ));
            return;
        }

        String path = isAfk(target)
                ? "messages.admin-status-afk"
                : "messages.admin-status-not-afk";
        sender.sendMessage(message(path, target));
    }

    private void removeAfkPlayer(CommandSender sender, String playerName) {
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage(messageWithValues(
                    "messages.player-not-found",
                    Map.of("player", playerName)
            ));
            return;
        }

        if (!isAfk(target)) {
            sender.sendMessage(message("messages.admin-status-not-afk", target));
            return;
        }

        setAfk(target, false, true);
        sender.sendMessage(message("messages.admin-removed", target));
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

        if (!settings.allowsAfkWorld(player.getWorld().getName())) {
            if (notify) {
                player.sendMessage(message("messages.world-not-allowed", player));
            }
            return;
        }

        long enteredAtNanos = System.nanoTime();
        AfkState state = new AfkState(
                player.getLocation().clone(),
                player.playerListName(),
                enteredAtNanos
        );

        afkPlayers.put(player.getUniqueId(), state);
        ensureAfkReconcileTask();

        // Stop actions that began before the player entered AFK mode.
        player.clearActiveItem();
        player.closeInventory();

        reconcilePlayerListName(player, state);
        refreshAfkDisplay(player, state);
        configureProtection(player, state, enteredAtNanos, notify);

        if (notify) {
            player.sendMessage(message("messages.now-afk", player));
            broadcast(message("messages.broadcast-now-afk", player));
            if (!state.invincible && state.protectionDeadlineNanos != AfkTiming.NO_DEADLINE) {
                player.sendMessage(messageWithSeconds(
                        "messages.already-protected-delay",
                        player,
                        settings.invincibleAfterSeconds()
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
        removeTabIndicator(player, state);
        recordActivity(player);
        stopAfkReconcileTaskIfIdle();

        if (notify) {
            player.sendMessage(message("messages.no-longer-afk", player));
            broadcast(message("messages.broadcast-no-longer-afk", player));
        }
    }

    private void broadcast(Component component) {
        if (!settings.broadcastsEnabled()) {
            return;
        }

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.sendMessage(component);
        }
    }

    private boolean isToggleOnCooldown(Player player) {
        int cooldownSeconds = settings.toggleCooldownSeconds();
        if (cooldownSeconds == 0) {
            return false;
        }

        long now = System.nanoTime();
        Long lastToggle = lastToggleNanos.get(player.getUniqueId());
        if (lastToggle != null) {
            int remainingSeconds = AfkTiming.remainingWholeSeconds(
                    now,
                    lastToggle,
                    cooldownSeconds
            );
            if (remainingSeconds > 0) {
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
            activateProtectionIfDue(player, state, now, true);
            reconcilePlayerListName(player, state);
        }
    }

    private boolean isProtectionEligible(Player player) {
        return settings.allowsProtectionWorld(player.getWorld().getName())
                && (!settings.protectionRequiresPermission()
                || player.hasPermission("pinnacleafk.protection"));
    }

    private void configureProtection(
            Player player,
            AfkState state,
            long now,
            boolean notifyActivation
    ) {
        state.invincible = false;
        state.protectionDeadlineNanos = AfkTiming.NO_DEADLINE;

        if (!isProtectionEligible(player)) {
            return;
        }

        state.protectionDeadlineNanos = AfkTiming.deadlineFrom(
                state.enteredAtNanos,
                settings.invincibleAfterSeconds()
        );
        activateProtectionIfDue(player, state, now, notifyActivation);
    }

    private void activateProtectionIfDue(
            Player player,
            AfkState state,
            long now,
            boolean notify
    ) {
        if (!isProtectionEligible(player)) {
            state.invincible = false;
            state.protectionDeadlineNanos = AfkTiming.NO_DEADLINE;
            return;
        }

        if (state.invincible) {
            return;
        }

        if (state.protectionDeadlineNanos == AfkTiming.NO_DEADLINE) {
            state.protectionDeadlineNanos = AfkTiming.deadlineFrom(
                    state.enteredAtNanos,
                    settings.invincibleAfterSeconds()
            );
        }

        if (!AfkTiming.isDue(now, state.protectionDeadlineNanos)) {
            return;
        }

        state.invincible = true;
        state.protectionDeadlineNanos = AfkTiming.NO_DEADLINE;
        if (notify) {
            player.sendMessage(message("messages.invincible-now", player));
        }
    }

    private void restartAutomaticAfkTask() {
        stopAutomaticAfkTask();
        if (!settings.automaticAfkEnabled()) {
            return;
        }

        long now = System.nanoTime();
        for (Player player : Bukkit.getOnlinePlayers()) {
            lastActivityNanos.putIfAbsent(player.getUniqueId(), now);
        }

        automaticAfkTaskId = Bukkit.getScheduler()
                .runTaskTimer(this, this::checkAutomaticAfkPlayers, 20L, 20L)
                .getTaskId();
    }

    private void stopAutomaticAfkTask() {
        if (automaticAfkTaskId == -1) {
            return;
        }

        Bukkit.getScheduler().cancelTask(automaticAfkTaskId);
        automaticAfkTaskId = -1;
    }

    private void checkAutomaticAfkPlayers() {
        if (!settings.automaticAfkEnabled()) {
            return;
        }

        long now = System.nanoTime();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isAfk(player)) {
                continue;
            }

            if (!settings.allowsAfkWorld(player.getWorld().getName())) {
                lastActivityNanos.put(player.getUniqueId(), now);
                continue;
            }

            long lastActivity = lastActivityNanos.computeIfAbsent(
                    player.getUniqueId(),
                    ignored -> now
            );
            long deadline = AfkTiming.deadlineFrom(
                    lastActivity,
                    settings.automaticAfkAfterSeconds()
            );
            if (AfkTiming.isDue(now, deadline)) {
                setAfk(player, true, true);
            }
        }
    }

    private void recordActivity(Player player) {
        lastActivityNanos.put(player.getUniqueId(), System.nanoTime());
    }

    private void reconcilePlayerListName(Player player, AfkState state) {
        if (!settings.tabIndicatorEnabled()) {
            removeTabIndicator(player, state);
            state.originalPlayerListName = player.playerListName();
            return;
        }

        Component currentPlayerListName = player.playerListName();
        if (!state.tabIndicatorApplied) {
            state.originalPlayerListName = currentPlayerListName;
        } else if (ValueOwnership.stillOwns(currentPlayerListName, state.appliedPlayerListName)) {
            return;
        } else {
            // A value PinnacleAFK did not apply belongs to the server or another plugin.
            state.originalPlayerListName = currentPlayerListName;
        }

        state.appliedPlayerListName = formatPlayerListName(
                playerListNameOrUsername(player, state.originalPlayerListName)
        );
        state.tabIndicatorApplied = true;
        player.playerListName(state.appliedPlayerListName);
    }

    private void refreshTabIndicator(Player player, AfkState state) {
        removeTabIndicator(player, state);
        state.originalPlayerListName = player.playerListName();
        reconcilePlayerListName(player, state);
    }

    private void removeTabIndicator(Player player, AfkState state) {
        if (state.tabIndicatorApplied
                && ValueOwnership.stillOwns(
                        player.playerListName(),
                        state.appliedPlayerListName
                )) {
            player.playerListName(state.originalPlayerListName);
        }

        state.appliedPlayerListName = null;
        state.tabIndicatorApplied = false;
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
        Component marker = afkMarker(player);

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

    private Component afkMarker(Player player) {
        return format("display.nametag-prefix", player)
                .append(format("display.nametag-suffix", player));
    }

    private void refreshAfkDisplay(Player player, AfkState state) {
        if (!settings.nametagIndicatorEnabled()) {
            removeAfkDisplay(state);
            return;
        }

        Entity existing = state.afkDisplayEntityId == null
                ? null
                : Bukkit.getEntity(state.afkDisplayEntityId);
        if (existing instanceof TextDisplay textDisplay) {
            textDisplay.text(afkMarker(player));
            if (!player.getPassengers().contains(textDisplay)) {
                player.addPassenger(textDisplay);
            }
            return;
        }

        if (existing != null) {
            existing.remove();
        }
        state.afkDisplayEntityId = createAfkDisplay(player).getUniqueId();
    }

    private void removeAfkDisplay(AfkState state) {
        if (state.afkDisplayEntityId == null) {
            return;
        }

        Entity display = Bukkit.getEntity(state.afkDisplayEntityId);
        if (display != null) {
            display.remove();
        }
        state.afkDisplayEntityId = null;
    }

    private void loadAndValidateConfig() {
        saveDefaultConfig();
        settings = readAndPersistConfig();
    }

    private AfkSettings readAndPersistConfig() {
        getConfig().options().copyDefaults(true);
        AfkSettings loadedSettings = AfkSettings.load(getConfig(), getLogger());

        // Persist newly introduced defaults and any corrected invalid values.
        saveConfig();
        return loadedSettings;
    }

    private void reloadPluginConfig(CommandSender sender) {
        File configFile = new File(getDataFolder(), "config.yml");
        YamlConfiguration syntaxCheck = new YamlConfiguration();

        try {
            syntaxCheck.load(configFile);
        } catch (IOException | InvalidConfigurationException exception) {
            getLogger().warning("Could not reload config.yml: " + exception.getMessage());
            sender.sendMessage(message("messages.reload-failed", null));
            return;
        }

        AfkSettings previousSettings = settings;
        reloadConfig();
        settings = readAndPersistConfig();
        applyReloadedSettings(previousSettings);
        sender.sendMessage(message("messages.reload-success", null));
    }

    private void applyReloadedSettings(AfkSettings previousSettings) {
        restartAutomaticAfkTask();
        boolean protectionChanged = !settings.protectionPolicyEquals(previousSettings);

        for (UUID playerId : List.copyOf(afkPlayers.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            AfkState state = afkPlayers.get(playerId);
            if (player == null || state == null) {
                continue;
            }

            if (!settings.allowsAfkWorld(player.getWorld().getName())) {
                setAfk(player, false, false);
                player.sendMessage(message("messages.afk-cleared-world", player));
                continue;
            }

            if (protectionChanged) {
                configureProtection(player, state, System.nanoTime(), false);
            }

            refreshTabIndicator(player, state);
            refreshAfkDisplay(player, state);
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
        } else {
            recordActivity(player);
        }
    }

    private Component message(String path, Player player) {
        return messageWithValues(
                path,
                player == null ? Map.of() : Map.of("player", player.getName())
        );
    }

    private Component messageWithSeconds(String path, Player player, int seconds) {
        Map<String, String> values = new HashMap<>();
        values.put("seconds", String.valueOf(seconds));
        if (player != null) {
            values.put("player", player.getName());
        }
        return messageWithValues(path, values);
    }

    private Component format(String path, Player player) {
        return message(path, player);
    }

    private Component messageWithValues(String path, Map<String, String> values) {
        String raw = configString(path);
        for (Map.Entry<String, String> value : values.entrySet()) {
            raw = raw.replace("%" + value.getKey() + "%", value.getValue());
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
        private final long enteredAtNanos;
        private Component originalPlayerListName;
        private Component appliedPlayerListName;
        private UUID afkDisplayEntityId;
        private long protectionDeadlineNanos = AfkTiming.NO_DEADLINE;
        private boolean tabIndicatorApplied = false;
        private boolean invincible = false;

        private AfkState(
                Location lockLocation,
                Component originalPlayerListName,
                long enteredAtNanos
        ) {
            this.lockLocation = lockLocation;
            this.originalPlayerListName = originalPlayerListName;
            this.enteredAtNanos = enteredAtNanos;
        }
    }
}
