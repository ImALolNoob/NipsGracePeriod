package org.novato.hardcore_grace_period;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.novato.hardcore_grace_period.config.ModConfig;
import org.novato.hardcore_grace_period.data.AltarData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The resurrection altar. Players right-click a registered altar block holding an item the
 * current <em>offering</em> asks for; once every item in the offering has been donated, a
 * random fallen player is brought back with a short grace period and the next offering is
 * rolled — larger and more random than the last, so each revival is harder than the one before.
 */
public class AltarManager {
    private final Hardcore_grace_period plugin;
    private final GracePeriodManager graceManager;
    private final ModConfig config;
    private final Random random = new Random();

    /** Materials the altar may ask for, resolved from config once at load. */
    private final List<Material> materialPool = new ArrayList<>();

    private boolean hasBlockAltar = false;
    private String altarWorld;
    private int altarX;
    private int altarY;
    private int altarZ;
    /** UUID of the armor-stand altar, if one is used instead of a block; null otherwise. */
    private UUID altarEntityId;
    private static final String ALTAR_TAG = "hgp_altar_stand";

    /** Number of revivals completed — the difficulty tier. */
    private int revivalsDone = 0;
    /** Current offering, in display order: material -> amount required. */
    private final LinkedHashMap<Material, Integer> offering = new LinkedHashMap<>();
    /** Amount donated so far toward the current offering. */
    private final Map<Material, Integer> donated = new LinkedHashMap<>();

    private final Set<UUID> deadPlayers = new java.util.HashSet<>();
    private final Set<UUID> pendingRevive = new java.util.HashSet<>();

    /** Name of the most recently revived player (for the hologram); null until the first revival. */
    private String lastRevivedName;
    /** The floating text display above the altar; recreated as needed. */
    private TextDisplay hologram;
    private static final String HOLOGRAM_TAG = "hgp_altar_hologram";

    public AltarManager(Hardcore_grace_period plugin, GracePeriodManager graceManager) {
        this.plugin = plugin;
        this.graceManager = graceManager;
        this.config = graceManager.getConfig();
        buildMaterialPool();
        loadData();
        if (offering.isEmpty()) {
            generateOffering();
            saveData();
        }
        // Keep the hologram alive and refreshed, and run the ambient particles.
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::altarTick, 40L, 12L);
    }

    /** Runs a few times a second: ensures the hologram exists, refreshes its text, and shows ambient particles. */
    private void altarTick() {
        ensureHologram();
        updateHologram();
        playAmbientEffect();
    }

    private void buildMaterialPool() {
        for (String name : config.altarMaterialPool) {
            if (name == null) continue;
            Material m = Material.matchMaterial(name.trim().toUpperCase());
            if (m != null && m.isItem()) {
                materialPool.add(m);
            } else {
                plugin.getLogger().warning("Unknown altar material in config, skipping: " + name);
            }
        }
        if (materialPool.isEmpty()) {
            // Never leave the altar with nothing to ask for.
            materialPool.addAll(List.of(Material.DIAMOND, Material.IRON_INGOT, Material.GOLD_INGOT,
                Material.EMERALD, Material.ENDER_PEARL, Material.GOLDEN_APPLE));
        }
    }

    // ----- Altar registration (a block, or a protected armor stand) -----

    public void setAltar(Location loc) {
        removeHologram(); // drop any hologram tied to the old spot
        removeAltarStand(); // switching to a block altar — clear any armor-stand altar
        this.hasBlockAltar = true;
        this.altarWorld = loc.getWorld().getName();
        this.altarX = loc.getBlockX();
        this.altarY = loc.getBlockY();
        this.altarZ = loc.getBlockZ();
        saveData();
        ensureHologram();
    }

    /** Spawns a protected, decorative armor stand at {@code loc} and makes it the altar. */
    public void spawnAltarStand(Location loc) {
        removeHologram();
        removeAltarStand();     // remove a previous stand if any
        this.hasBlockAltar = false;
        World w = loc.getWorld();
        Location at = loc.clone();
        at.setY(Math.floor(at.getY()));
        at.setPitch(0);           // keep it upright
        // keep loc's yaw so the stand faces the direction the player is looking
        ArmorStand stand = w.spawn(at, ArmorStand.class, s -> {
            s.addScoreboardTag(ALTAR_TAG);
            s.setInvulnerable(true);   // immune to all damage
            s.setGravity(false);       // never falls or gets pushed down
            s.setPersistent(true);     // saved with the world
            s.setCanMove(false);
            s.setCustomNameVisible(false);
            s.setArms(true);
            s.setBasePlate(true);
            // Lock every slot so nobody can take or change its gear.
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                s.addEquipmentLock(slot, ArmorStand.LockType.ADDING_OR_CHANGING);
                s.addEquipmentLock(slot, ArmorStand.LockType.REMOVING_OR_CHANGING);
            }
        });
        this.altarEntityId = stand.getUniqueId();
        saveData();
        ensureHologram();
    }

    public void removeAltar() {
        this.hasBlockAltar = false;
        removeAltarStand();
        removeHologram();
        saveData();
    }

    /** Removes the armor-stand altar entity (if present) and forgets it. */
    private void removeAltarStand() {
        if (altarEntityId != null) {
            Entity e = Bukkit.getEntity(altarEntityId);
            if (e != null) e.remove();
            altarEntityId = null;
        }
    }

    /** True if any altar (block or armor stand) is configured. */
    public boolean hasAltar() {
        return hasBlockAltar || altarEntityId != null;
    }

    /** True if this altar is an armor stand (vs. a block). */
    public boolean isArmorStandAltar() {
        return altarEntityId != null;
    }

    /** The altar's reference location (block corner, or the stand's position); null if unresolved. */
    public Location getAltarLocation() {
        if (altarEntityId != null) {
            Entity e = Bukkit.getEntity(altarEntityId);
            return (e != null && e.isValid()) ? e.getLocation() : null;
        }
        if (hasBlockAltar) {
            World w = Bukkit.getWorld(altarWorld);
            if (w == null) return null;
            return new Location(w, altarX, altarY, altarZ);
        }
        return null;
    }

    /** The point effects and the hologram are centered on; null if the altar is unresolved. */
    private Location altarCenter() {
        Location base = getAltarLocation();
        if (base == null || base.getWorld() == null) return null;
        if (altarEntityId != null) {
            return base.clone().add(0, 1.0, 0); // around the stand's upper body
        }
        return base.clone().add(0.5, 1.0, 0.5); // center of the block, one above
    }

    public boolean isAltar(Location blockLoc) {
        return hasBlockAltar
            && blockLoc.getWorld() != null
            && blockLoc.getWorld().getName().equals(altarWorld)
            && blockLoc.getBlockX() == altarX
            && blockLoc.getBlockY() == altarY
            && blockLoc.getBlockZ() == altarZ;
    }

    /** True if the given entity is the armor-stand altar. */
    public boolean isAltarEntity(Entity entity) {
        return altarEntityId != null && entity != null && entity.getUniqueId().equals(altarEntityId);
    }

    // ----- Death tracking -----

    /** Records a permanent death so the player becomes eligible for revival at the altar. */
    public void markDead(UUID uuid) {
        if (deadPlayers.add(uuid)) {
            saveData();
            updateHologram();
        }
    }

    public int deadCount() {
        return deadPlayers.size();
    }

    /** Fallen players' names (resolved where possible), sorted, for admin listing. */
    public List<String> deadNames() {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (UUID uuid : deadPlayers) {
            names.add(resolveName(uuid));
        }
        return new ArrayList<>(names);
    }

    // ----- Donations -----

    /** Handles a right-click on the altar block: offer the item in the player's main hand. */
    public void handleDonation(Player donor) {
        if (offering.isEmpty()) generateOffering();

        if (deadPlayers.isEmpty()) {
            donor.sendMessage(Component.text("The altar is dormant — there are no fallen to bring back.")
                .color(NamedTextColor.GRAY));
            donor.playSound(donor.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
            return;
        }

        ItemStack hand = donor.getInventory().getItemInMainHand();
        Material mat = hand.getType();
        if (hand.getType().isAir()) {
            donor.sendMessage(Component.text("Hold an item the altar craves, then right-click. Use /altar to see what it wants.")
                .color(NamedTextColor.YELLOW));
            return;
        }

        Integer required = offering.get(mat);
        if (required == null) {
            donor.sendMessage(Component.text("The altar has no use for " + pretty(mat) + ". Use /altar to see what it craves.")
                .color(NamedTextColor.YELLOW));
            return;
        }

        int already = donated.getOrDefault(mat, 0);
        int need = required - already;
        if (need <= 0) {
            donor.sendMessage(Component.text("The altar already has all the " + pretty(mat) + " it needs.")
                .color(NamedTextColor.YELLOW));
            return;
        }

        int take = Math.min(hand.getAmount(), need);
        int left = hand.getAmount() - take;
        if (left <= 0) {
            donor.getInventory().setItemInMainHand(null);
        } else {
            hand.setAmount(left);
            donor.getInventory().setItemInMainHand(hand);
        }
        donated.put(mat, already + take);

        donor.playSound(donor.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
        playDonationEffect();
        donor.sendMessage(Component.text("You offer " + take + "x " + pretty(mat) + "  (")
            .color(NamedTextColor.LIGHT_PURPLE)
            .append(Component.text((already + take) + "/" + required).color(NamedTextColor.WHITE))
            .append(Component.text(")").color(NamedTextColor.LIGHT_PURPLE)));

        if (isComplete()) {
            triggerRevival(donor);
        } else {
            saveData();
        }
    }

    private boolean isComplete() {
        if (offering.isEmpty()) return false;
        for (Map.Entry<Material, Integer> entry : offering.entrySet()) {
            if (donated.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private void triggerRevival(Player donor) {
        List<UUID> dead = new ArrayList<>(deadPlayers);
        final UUID chosen = dead.get(random.nextInt(dead.size()));
        deadPlayers.remove(chosen);

        // The offering is spent; the next one is harder.
        revivalsDone++;
        donated.clear();
        generateOffering();

        // Grant the grace now and queue the revival as a crash-safe fallback. The player is only
        // actually brought back when the 10-second charge-up finishes (see the callback below).
        graceManager.setPlayerTime(chosen, config.getAltarRevivalGraceMillis());
        pendingRevive.add(chosen);
        saveData();

        final String name = resolveName(chosen);
        announceChargeStart(donor.getName());
        startChargeUpShow(() -> {
            Player online = Bukkit.getPlayer(chosen);
            if (online != null && online.isOnline()) {
                pendingRevive.remove(chosen); // revived live, so no login fallback needed
                reviveNow(online);            // pops them in on top of the altar
            }
            // If they're offline, they stay queued and spawn at the altar on their next login (once).
            lastRevivedName = name;
            saveData();
            updateHologram();
            announceReturned(name);
        });
    }

    /** Brings an online fallen player back into the living world. */
    public void reviveNow(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(revivalLocation(player));
        graceManager.healPlayer(player);
        player.setFireTicks(0);
        player.setFallDistance(0);
        player.setFreezeTicks(0);
        new ArrayList<>(player.getActivePotionEffects())
            .forEach(effect -> player.removePotionEffect(effect.getType()));
        player.showTitle(Title.title(
            Component.text("You have been reborn!").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
            Component.text("The altar grants you " + graceManager.formatTime(config.getAltarRevivalGraceMillis()) + " of grace")
                .color(NamedTextColor.YELLOW)
        ));
        player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);

        // A burst of light wraps the reborn player.
        Location at = player.getLocation().clone().add(0, 1, 0);
        particle(player.getWorld(), Particle.TOTEM_OF_UNDYING, at, 90, 0.4, 0.9, 0.4, 0.4);
        particle(player.getWorld(), Particle.END_ROD, at, 30, 0.3, 0.9, 0.3, 0.05);
        particle(player.getWorld(), Particle.SOUL_FIRE_FLAME, at, 20, 0.4, 0.9, 0.4, 0.02);
    }

    /**
     * If this player was chosen for revival while offline, restore them now and return true.
     * Called on join. Their grace budget was already set at the moment of revival.
     */
    public boolean consumePendingRevive(UUID uuid) {
        if (pendingRevive.remove(uuid)) {
            saveData();
            return true;
        }
        return false;
    }

    private Location revivalLocation(Player player) {
        Location base = getAltarLocation();
        if (base != null && base.getWorld() != null) {
            // On top of the stand, or on top of the altar block.
            return isArmorStandAltar() ? base.clone().add(0, 1, 0) : base.clone().add(0.5, 1, 0.5);
        }
        World overworld = Bukkit.getWorld("world");
        World world = overworld != null ? overworld : player.getWorld();
        return world.getSpawnLocation();
    }

    /** When the final item goes in: the ritual begins and the altar starts charging. */
    private void announceChargeStart(String donorName) {
        Bukkit.broadcast(Component.text("⟱ The final offering is accepted ⟰").color(NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD));
        Bukkit.broadcast(Component.text(donorName + " completes the ritual — the altar begins to charge...", NamedTextColor.GRAY));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 0.7f);
        }
    }

    /** Ten seconds later, at the finale: the soul is returned. */
    private void announceReturned(String revivedName) {
        Bukkit.broadcast(Component.text(revivedName + " has been pulled back from death!").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
        Bukkit.broadcast(Component.text("The next soul will cost far more.").color(NamedTextColor.DARK_GRAY).decorate(TextDecoration.ITALIC));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.1f);
        }
    }

    // ----- Hologram (native Text Display entity, no extra plugin needed) -----

    /** Spawns the floating text display if the altar is set and one isn't already present. */
    private void ensureHologram() {
        Location center = altarCenter();
        if (center == null) {
            removeHologram();
            return;
        }
        if (hologram != null && hologram.isValid() && !hologram.isDead()) return;

        World w = center.getWorld();
        Location at = center.clone().add(0, 1.3, 0); // float it above the altar/stand
        // Remove any leftover holograms near the altar (e.g. from a previous run).
        for (Entity e : w.getNearbyEntities(at, 2, 3, 2)) {
            if (e instanceof TextDisplay && e.getScoreboardTags().contains(HOLOGRAM_TAG)) {
                e.remove();
            }
        }
        hologram = w.spawn(at, TextDisplay.class, td -> {
            td.addScoreboardTag(HOLOGRAM_TAG);
            td.setBillboard(Display.Billboard.CENTER);   // always faces the viewer
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setSeeThrough(false);
            td.setShadowed(true);
            td.setPersistent(false);                     // not saved to the world; we respawn as needed
            td.setBackgroundColor(Color.fromARGB(140, 10, 0, 20));
            td.text(buildHologramText());
        });
    }

    private void updateHologram() {
        if (hologram != null && hologram.isValid()) {
            hologram.text(buildHologramText());
        }
    }

    private void removeHologram() {
        if (hologram != null) {
            hologram.remove();
            hologram = null;
        }
    }

    /** The multi-line text shown on the altar hologram. */
    private Component buildHologramText() {
        java.util.List<Component> lines = new ArrayList<>();
        lines.add(Component.text("✦ Altar of Rebirth ✦").color(NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD));
        lines.add(Component.text("Fallen: ", NamedTextColor.GRAY)
            .append(Component.text(deadCount(), deadCount() > 0 ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY))
            .append(Component.text("    Revived: ", NamedTextColor.GRAY))
            .append(Component.text(revivalsDone, NamedTextColor.GREEN)));
        lines.add(Component.text("Last reborn: ", NamedTextColor.GRAY)
            .append(Component.text(lastRevivedName == null ? "—" : lastRevivedName, NamedTextColor.WHITE)));

        if (deadCount() == 0) {
            lines.add(Component.empty());
            lines.add(Component.text("Dormant — no fallen to revive.").color(NamedTextColor.DARK_GRAY).decorate(TextDecoration.ITALIC));
        } else {
            lines.add(Component.empty());
            lines.add(Component.text("Offering needed:").color(NamedTextColor.YELLOW));
            for (Map.Entry<Material, Integer> entry : offering.entrySet()) {
                int have = donated.getOrDefault(entry.getKey(), 0);
                int need = entry.getValue();
                boolean done = have >= need;
                lines.add(Component.text("• ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(pretty(entry.getKey()) + ": ", NamedTextColor.WHITE))
                    .append(Component.text(have + "/" + need, done ? NamedTextColor.GREEN : NamedTextColor.RED)));
            }
        }
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    // ----- Particle effects (all native Paper API, no extra plugin needed) -----

    /**
     * Spawns a particle, swallowing the error if this server build demands an extra data object
     * (Color, Float, BlockData, ...) for it. Keeps a cosmetic effect from ever crashing the show
     * or spamming the console; a particle that needs data is simply skipped on this version.
     */
    private void particle(World w, Particle p, Location loc, int count, double ox, double oy, double oz, double extra) {
        try {
            w.spawnParticle(p, loc, count, ox, oy, oz, extra);
        } catch (RuntimeException ignored) {
            // This particle wants a data argument on this server version — skip it.
        }
    }

    /** Soft swirling particles at the altar every tick-batch; a little stronger when souls await rebirth. */
    private void playAmbientEffect() {
        Location c = altarCenter();
        if (c == null) return;
        World w = c.getWorld();
        particle(w, Particle.ENCHANT, c, 6, 0.4, 0.6, 0.4, 0.4);
        particle(w, Particle.PORTAL, c, 5, 0.3, 0.5, 0.3, 0.3);
        if (!deadPlayers.isEmpty()) {
            // "Charged and waiting" — a gentle rising glow.
            particle(w, Particle.SOUL_FIRE_FLAME, c.clone().add(0, 0.2, 0), 2, 0.15, 0.3, 0.15, 0.01);
            particle(w, Particle.END_ROD, c, 2, 0.2, 0.5, 0.2, 0.01);
        }
    }

    /** A small sparkle burst when someone makes an offering. */
    private void playDonationEffect() {
        Location c = altarCenter();
        if (c == null) return;
        World w = c.getWorld();
        particle(w, Particle.HAPPY_VILLAGER, c, 12, 0.4, 0.6, 0.4, 0);
        particle(w, Particle.END_ROD, c, 10, 0.3, 0.5, 0.3, 0.05);
        particle(w, Particle.WITCH, c, 8, 0.4, 0.6, 0.4, 0);
    }

    /**
     * A roughly 10-second charge-up at the altar — an opening lightning flash, then a rotating
     * twin-helix pillar of light with pulsing bursts and a sound beat each second, building to a
     * send-off explosion. {@code onComplete} runs at the finale, which is when the player is
     * actually brought back. Harmless (the lightning does no damage).
     */
    private void startChargeUpShow(Runnable onComplete) {
        final Location base = altarCenter();
        if (base == null) {
            // No altar to animate — just bring them back immediately so nothing gets stuck.
            onComplete.run();
            return;
        }
        final World w = base.getWorld();

        // Opening beat.
        w.strikeLightningEffect(base.clone().subtract(0, 1.0, 0)); // visual only — no fire, no damage
        particle(w, Particle.EXPLOSION, base, 1, 0, 0, 0, 0);

        final int durationTicks = 200; // ~10 seconds
        final int period = 2;          // update 10 times per second
        final int[] elapsed = {0};
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (elapsed[0] >= durationTicks) {
                task.cancel();
                // Finale — then the player pops in.
                particle(w, Particle.EXPLOSION_EMITTER, base, 1, 0, 0, 0, 0);
                particle(w, Particle.TOTEM_OF_UNDYING, base, 150, 0.7, 1.2, 0.7, 0.5);
                particle(w, Particle.END_ROD, base, 70, 0.7, 1.0, 0.7, 0.18);
                w.playSound(base, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                onComplete.run();
                return;
            }

            // How far into the charge we are: 0.0 at the start (10s left) -> 1.0 at the finale.
            double progress = Math.min(1.0, (double) elapsed[0] / durationTicks);

            // Twin helix of light — denser, wider and brighter as the charge builds.
            double angle = elapsed[0] * 0.5;
            double yStep = 0.5 - progress * 0.35;              // 0.5 (sparse) -> 0.15 (dense)
            double radius = 0.5 + progress * 0.7;              // 0.5 -> 1.2 (widens outward)
            int perPoint = 1 + (int) Math.round(progress * 2); // 1 -> 3 particles per point
            for (int arm = 0; arm < 2; arm++) {
                double a = angle + arm * Math.PI;
                for (double y = 0; y <= 4; y += yStep) {
                    double x = Math.cos(a + y) * radius;
                    double z = Math.sin(a + y) * radius;
                    particle(w, Particle.END_ROD, base.clone().add(x, y, z), perPoint, 0, 0, 0, 0);
                }
            }

            // Core swirl, growing from a trickle to a storm.
            particle(w, Particle.TOTEM_OF_UNDYING, base, 3 + (int) (progress * 50), 0.5, 0.9, 0.5, 0.2 + progress * 0.4);
            particle(w, Particle.SOUL_FIRE_FLAME, base, 2 + (int) (progress * 25), 0.5, 0.6, 0.5, 0.01);
            if (progress > 0.4) {
                particle(w, Particle.ENCHANT, base, (int) (progress * 30), 0.6, 1.0, 0.6, 1.0);
            }

            // A rising sound pulse each second: bigger burst, higher pitch, as it climbs.
            if (elapsed[0] % 20 == 0) {
                w.playSound(base, Sound.BLOCK_BEACON_AMBIENT, 0.7f, 0.8f + (float) progress * 1.2f);
                particle(w, Particle.TOTEM_OF_UNDYING, base, 10 + (int) (progress * 60), 0.5, 0.8, 0.5, 0.3);
            }

            elapsed[0] += period;
        }, 0L, period);
    }

    // ----- Offering generation -----

    private void generateOffering() {
        offering.clear();
        donated.clear();
        if (materialPool.isEmpty()) return;

        int types = config.altarBaseItemTypes + revivalsDone * config.altarItemTypesPerRevival;
        types = Math.max(1, Math.min(types, Math.min(config.altarMaxItemTypes, materialPool.size())));

        List<Material> pick = new ArrayList<>(materialPool);
        Collections.shuffle(pick, random);

        double scale = 1.0 + revivalsDone * config.altarCountGrowthPerRevival;
        int lo = Math.max(1, (int) Math.round(config.altarBaseCountMin * scale));
        int hi = Math.max(lo, (int) Math.round(config.altarBaseCountMax * scale));

        for (int i = 0; i < types; i++) {
            Material m = pick.get(i);
            int count = Math.min(lo + random.nextInt(hi - lo + 1), config.altarMaxCountPerType);
            offering.put(m, count);
        }
    }

    /** Admin: roll a fresh offering at the current tier (clears partial donations). */
    public void rerollOffering() {
        generateOffering();
        saveData();
    }

    /**
     * Admin: revive a specific fallen player without an offering (does not change the tier).
     * Returns false if that player isn't among the fallen.
     */
    public boolean forceRevive(UUID uuid) {
        if (!deadPlayers.remove(uuid)) return false;
        graceManager.setPlayerTime(uuid, config.getAltarRevivalGraceMillis());
        lastRevivedName = resolveName(uuid);
        Player online = Bukkit.getPlayer(uuid);
        if (online != null && online.isOnline()) {
            reviveNow(online);
        } else {
            pendingRevive.add(uuid);
        }
        saveData();
        updateHologram();
        return true;
    }

    public boolean isDead(UUID uuid) {
        return deadPlayers.contains(uuid);
    }

    // ----- Status accessors (for commands) -----

    public int getRevivalsDone() {
        return revivalsDone;
    }

    /** The current offering with how much is still missing, in display order. */
    public LinkedHashMap<Material, Integer> getOffering() {
        return new LinkedHashMap<>(offering);
    }

    public int getDonated(Material m) {
        return donated.getOrDefault(m, 0);
    }

    public static String pretty(Material m) {
        String[] words = m.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private String resolveName(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
        return off.getName() != null ? off.getName() : "A fallen soul";
    }

    // ----- Persistence -----

    /** Clears progress, tier and the fallen/pending lists but keeps the registered altar block. */
    public void reset() {
        revivalsDone = 0;
        deadPlayers.clear();
        pendingRevive.clear();
        donated.clear();
        lastRevivedName = null;
        generateOffering();
        saveData();
        updateHologram();
    }

    /** Called on plugin disable: drops the floating hologram (it is not saved to the world). */
    public void shutdown() {
        removeHologram();
    }

    private void loadData() {
        AltarData.StateData d = AltarData.load(plugin);
        this.revivalsDone = Math.max(0, d.revivalsDone);
        this.lastRevivedName = d.lastRevived;
        if (d.altarWorld != null && d.altarX != null && d.altarY != null && d.altarZ != null) {
            this.hasBlockAltar = true;
            this.altarWorld = d.altarWorld;
            this.altarX = d.altarX;
            this.altarY = d.altarY;
            this.altarZ = d.altarZ;
        }
        if (d.altarEntity != null) {
            try {
                this.altarEntityId = UUID.fromString(d.altarEntity);
                this.hasBlockAltar = false; // an armor-stand altar takes precedence
            } catch (IllegalArgumentException ignored) {}
        }
        for (Map.Entry<String, Integer> e : d.offering.entrySet()) {
            Material m = Material.matchMaterial(e.getKey());
            if (m != null && e.getValue() != null) offering.put(m, e.getValue());
        }
        for (Map.Entry<String, Integer> e : d.donated.entrySet()) {
            Material m = Material.matchMaterial(e.getKey());
            if (m != null && e.getValue() != null) donated.put(m, e.getValue());
        }
        for (String s : d.deadPlayers) {
            try { deadPlayers.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
        }
        for (String s : d.pendingRevive) {
            try { pendingRevive.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
        }
    }

    public void saveData() {
        AltarData.StateData d = new AltarData.StateData();
        d.revivalsDone = revivalsDone;
        d.lastRevived = lastRevivedName;
        if (hasBlockAltar) {
            d.altarWorld = altarWorld;
            d.altarX = altarX;
            d.altarY = altarY;
            d.altarZ = altarZ;
        }
        if (altarEntityId != null) {
            d.altarEntity = altarEntityId.toString();
        }
        for (Map.Entry<Material, Integer> e : offering.entrySet()) {
            d.offering.put(e.getKey().name(), e.getValue());
        }
        for (Map.Entry<Material, Integer> e : donated.entrySet()) {
            d.donated.put(e.getKey().name(), e.getValue());
        }
        for (UUID uuid : deadPlayers) d.deadPlayers.add(uuid.toString());
        for (UUID uuid : pendingRevive) d.pendingRevive.add(uuid.toString());
        AltarData.save(plugin, d);
    }
}
