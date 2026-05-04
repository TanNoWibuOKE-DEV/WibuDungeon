package com.wibudungeon.core.reward;

import com.wibudungeon.core.config.ConfigManager;
import com.wibudungeon.core.util.MessageUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages animated reward chests that drop from the sky.
 *
 * v1.0.6 Final Version:
 * - Supports RewardBundle system (bundle-driven rewards)
 * - Chest stays until fully looted (all items taken)
 * - Force removal on next wave or dungeon end
 * - Track remaining items to prevent duplication
 * - Backward compatible with old List<Reward> method
 */
public class RewardChestManager implements Listener {

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final Map<Location, ChestState> activeChests = new HashMap<>();
    private static final String CHEST_META = "wibudungeon_reward_chest";

    public RewardChestManager(Plugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    /**
     * Drop chests using a RewardBundle (v1.0.6 preferred method).
     */
    public void dropRandomChestsFromBundle(Location pos1, Location pos2, int amount, RewardBundle bundle, String instanceId) {
        if (bundle == null || pos1 == null || pos2 == null) return;

        World world = pos1.getWorld();
        if (world == null) return;

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        List<Location> usedLocs = new ArrayList<>();

        for (int i = 0; i < amount; i++) {
            Location chestLoc = findValidRandomLocation(world, minX, maxX, minZ, maxZ, usedLocs);
            if (chestLoc != null) {
                usedLocs.add(chestLoc);
                List<ItemStack> rolledItems = bundle.rollItems();
                if (!rolledItems.isEmpty()) {
                    dropChestWithItems(chestLoc, rolledItems, instanceId);
                }
            }
        }
    }

    /**
     * Drop multiple chests randomly inside the dungeon bounds (legacy method).
     */
    public void dropRandomChests(Location pos1, Location pos2, int amount, List<Reward> rewards, double difficultyMult, String instanceId) {
        if (rewards.isEmpty() || pos1 == null || pos2 == null) return;

        World world = pos1.getWorld();
        if (world == null) return;

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        List<Location> usedLocs = new ArrayList<>();

        for (int i = 0; i < amount; i++) {
            Location chestLoc = findValidRandomLocation(world, minX, maxX, minZ, maxZ, usedLocs);
            if (chestLoc != null) {
                usedLocs.add(chestLoc);
                dropChest(chestLoc, rewards, difficultyMult, instanceId);
            }
        }
    }

    private Location findValidRandomLocation(World world, int minX, int maxX, int minZ, int maxZ, List<Location> existing) {
        List<int[]> candidates = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                candidates.add(new int[]{x, z});
            }
        }
        Collections.shuffle(candidates);

        for (int[] coord : candidates) {
            int x = coord[0];
            int z = coord[1];

            int highY = world.getHighestBlockYAt(x, z);
            if (highY <= 0) continue;

            Location candidate = new Location(world, x + 0.5, highY + 1, z + 0.5);

            Block below = candidate.clone().subtract(0, 1, 0).getBlock();
            Material belowType = below.getType();
            if (!belowType.isSolid() || belowType == Material.LAVA || belowType == Material.WATER
                    || belowType.name().contains("LEAVES") || belowType.name().contains("FENCE")) {
                continue;
            }

            if (candidate.getBlock().getType() != Material.AIR) continue;

            boolean tooClose = false;
            for (Location ex : existing) {
                if (ex.distanceSquared(candidate) < 9) { tooClose = true; break; }
            }
            if (!tooClose) return candidate;
        }
        return null;
    }

    /**
     * Drop a chest with pre-rolled ItemStacks (bundle system).
     */
    public void dropChestWithItems(Location groundLoc, List<ItemStack> items, String instanceId) {
        if (items.isEmpty()) return;

        int dropHeight = 10;
        World world = groundLoc.getWorld();
        if (world == null) return;

        Location startLoc = new Location(world, groundLoc.getX(), groundLoc.getY() + dropHeight, groundLoc.getZ());
        startLoc.setYaw(0);
        startLoc.setPitch(0);

        BlockDisplay display = (BlockDisplay) world.spawnEntity(startLoc, EntityType.BLOCK_DISPLAY);
        display.setBlock(Material.CHEST.createBlockData());
        display.setTransformation(new Transformation(
                new Vector3f(0, 0, 0), new AxisAngle4f(0, 0, 1, 0),
                new Vector3f(1, 1, 1), new AxisAngle4f(0, 0, 1, 0)
        ));
        display.setGlowing(true);

        final double targetY = groundLoc.getY();

        new BukkitRunnable() {
            double currentY = startLoc.getY();
            @Override
            public void run() {
                if (display.isDead()) { cancel(); return; }
                currentY -= 0.4;
                if (currentY <= targetY) {
                    cancel();
                    display.remove();
                    placeRealChestWithItems(groundLoc, items, instanceId);
                    world.spawnParticle(Particle.EXPLOSION, groundLoc.clone().add(0.5, 0.5, 0.5), 3);
                    world.playSound(groundLoc, Sound.BLOCK_ANVIL_LAND, 0.5f, 1.5f);
                    return;
                }
                Location newLoc = new Location(world, startLoc.getX(), currentY, startLoc.getZ());
                newLoc.setYaw(0);
                newLoc.setPitch(0);
                display.teleport(newLoc);
                world.spawnParticle(Particle.FLAME, newLoc.clone().add(0.5, 0.5, 0.5), 3, 0.2, 0.1, 0.2, 0.01);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /**
     * Drop a single reward chest from the sky (legacy method).
     */
    public void dropChest(Location groundLoc, List<Reward> rewards, double difficultyMult, String instanceId) {
        if (rewards.isEmpty()) return;

        int dropHeight = 10;
        World world = groundLoc.getWorld();
        if (world == null) return;

        Location startLoc = new Location(world, groundLoc.getX(), groundLoc.getY() + dropHeight, groundLoc.getZ());
        startLoc.setYaw(0);
        startLoc.setPitch(0);

        BlockDisplay display = (BlockDisplay) world.spawnEntity(startLoc, EntityType.BLOCK_DISPLAY);
        display.setBlock(Material.CHEST.createBlockData());
        display.setTransformation(new Transformation(
                new Vector3f(0, 0, 0), new AxisAngle4f(0, 0, 1, 0),
                new Vector3f(1, 1, 1), new AxisAngle4f(0, 0, 1, 0)
        ));
        display.setGlowing(true);

        final double targetY = groundLoc.getY();

        new BukkitRunnable() {
            double currentY = startLoc.getY();
            @Override
            public void run() {
                if (display.isDead()) { cancel(); return; }
                currentY -= 0.4;
                if (currentY <= targetY) {
                    cancel();
                    display.remove();
                    placeRealChest(groundLoc, rewards, difficultyMult, instanceId);
                    world.spawnParticle(Particle.EXPLOSION, groundLoc.clone().add(0.5, 0.5, 0.5), 3);
                    world.playSound(groundLoc, Sound.BLOCK_ANVIL_LAND, 0.5f, 1.5f);
                    return;
                }
                Location newLoc = new Location(world, startLoc.getX(), currentY, startLoc.getZ());
                newLoc.setYaw(0);
                newLoc.setPitch(0);
                display.teleport(newLoc);
                world.spawnParticle(Particle.FLAME, newLoc.clone().add(0.5, 0.5, 0.5), 3, 0.2, 0.1, 0.2, 0.01);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /**
     * Place a real chest with pre-rolled items (bundle system).
     */
    private void placeRealChestWithItems(Location loc, List<ItemStack> items, String instanceId) {
        Location chestLoc = loc.clone();
        chestLoc.setX(chestLoc.getBlockX());
        chestLoc.setY(chestLoc.getBlockY());
        chestLoc.setZ(chestLoc.getBlockZ());

        if (chestLoc.getBlock().getType() != Material.AIR) {
            chestLoc.add(0, 1, 0);
        }

        chestLoc.getBlock().setType(Material.CHEST);

        if (chestLoc.getBlock().getState() instanceof Chest chest) {
            Inventory inv = chest.getInventory();
            List<Integer> usedSlots = new ArrayList<>();
            for (ItemStack item : items) {
                int slot = findEmptySlot(inv, usedSlots);
                if (slot >= 0) {
                    inv.setItem(slot, item.clone());
                    usedSlots.add(slot);
                }
            }
            chest.update();
            chestLoc.getBlock().setMetadata(CHEST_META, new FixedMetadataValue(plugin, instanceId));

            Location key = chestLoc.getBlock().getLocation();
            activeChests.put(key, new ChestState(key, instanceId, countItems(inv)));
        }
    }

    private void placeRealChest(Location loc, List<Reward> rewards, double difficultyMult, String instanceId) {
        Location chestLoc = loc.clone();
        chestLoc.setX(chestLoc.getBlockX());
        chestLoc.setY(chestLoc.getBlockY());
        chestLoc.setZ(chestLoc.getBlockZ());

        if (chestLoc.getBlock().getType() != Material.AIR) {
            chestLoc.add(0, 1, 0);
        }

        chestLoc.getBlock().setType(Material.CHEST);

        if (chestLoc.getBlock().getState() instanceof Chest chest) {
            Inventory inv = chest.getInventory();
            List<Integer> usedSlots = new ArrayList<>();
            for (Reward reward : rewards) {
                double roll = ThreadLocalRandom.current().nextDouble();
                if (roll > (reward.getChance() * difficultyMult)) continue;
                if (reward.getType() == Reward.RewardType.ITEM && reward.getItem() != null) {
                    int slot = findEmptySlot(inv, usedSlots);
                    if (slot >= 0) {
                        inv.setItem(slot, reward.getItem().clone());
                        usedSlots.add(slot);
                    }
                }
            }
            chest.update();
            chestLoc.getBlock().setMetadata(CHEST_META, new FixedMetadataValue(plugin, instanceId));

            Location key = chestLoc.getBlock().getLocation();
            activeChests.put(key, new ChestState(key, instanceId, countItems(inv)));
        }
    }

    private int countItems(Inventory inv) {
        int count = 0;
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() != Material.AIR) count++;
        }
        return count;
    }

    private int findEmptySlot(Inventory inv, List<Integer> used) {
        for (int attempt = 0; attempt < 50; attempt++) {
            int slot = ThreadLocalRandom.current().nextInt(inv.getSize());
            if (!used.contains(slot) && (inv.getItem(slot) == null || inv.getItem(slot).getType() == Material.AIR)) {
                return slot;
            }
        }
        for (int i = 0; i < inv.getSize(); i++) {
            if (!used.contains(i) && (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR)) return i;
        }
        return -1;
    }

    /**
     * v1.0.6: Chest only disappears AFTER fully looted (all items taken).
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Chest chest)) return;

        Location loc = chest.getBlock().getLocation();
        ChestState state = activeChests.get(loc);
        if (state == null) return;

        // Count remaining items
        int remaining = countItems(chest.getInventory());
        state.setRemainingItems(remaining);

        if (remaining == 0) {
            // Fully looted — remove chest
            state.setLooted(true);
            loc.getBlock().removeMetadata(CHEST_META, plugin);
            loc.getBlock().setType(Material.AIR);
            activeChests.remove(loc);

            if (event.getPlayer() instanceof Player player) {
                MessageUtil.send(player, "&a&l★ Reward chest fully looted! ★");
            }
        }
        // If not fully looted, chest stays in world
    }

    /**
     * Force cleanup all chests for an instance (next wave or dungeon end).
     */
    public void cleanupInstance(String instanceId) {
        Iterator<Map.Entry<Location, ChestState>> it = activeChests.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Location, ChestState> entry = it.next();
            if (entry.getValue().getInstanceId().equals(instanceId)) {
                Block block = entry.getKey().getBlock();
                block.removeMetadata(CHEST_META, plugin);
                block.setType(Material.AIR);
                it.remove();
            }
        }
    }

    public void cleanupAll() {
        for (Map.Entry<Location, ChestState> entry : activeChests.entrySet()) {
            Block block = entry.getKey().getBlock();
            block.removeMetadata(CHEST_META, plugin);
            block.setType(Material.AIR);
        }
        activeChests.clear();
    }

    /**
     * v1.0.6: Enhanced ChestState tracks remaining items.
     */
    private static class ChestState {
        private final Location location;
        private final String instanceId;
        private boolean looted;
        private int remainingItems;

        ChestState(Location location, String instanceId, int initialItems) {
            this.location = location;
            this.instanceId = instanceId;
            this.remainingItems = initialItems;
        }

        public Location getLocation() { return location; }
        public String getInstanceId() { return instanceId; }
        public boolean isLooted() { return looted; }
        public void setLooted(boolean looted) { this.looted = looted; }
        public int getRemainingItems() { return remainingItems; }
        public void setRemainingItems(int remaining) { this.remainingItems = remaining; }
    }
}
