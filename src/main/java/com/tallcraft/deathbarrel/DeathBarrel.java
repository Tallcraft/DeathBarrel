package com.tallcraft.deathbarrel;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class DeathBarrel extends JavaPlugin implements Listener {

    private final int barrelCapacity = InventoryType.BARREL.getDefaultSize();
    private FileConfiguration config;

    @Override
    public void onEnable() {
        initConfig();

        // Init bStats metrics.
        new Metrics(this);

        // Enable event handlers.
        getServer().getPluginManager().registerEvents(this, this);
    }

    private void initConfig() {
        config = this.getConfig();

        MemoryConfiguration defaultConfig = new MemoryConfiguration();

        defaultConfig.set("removeOnEmpty", true);
        defaultConfig.set("protectFromOtherPlayers", false);

        ConfigurationSection messages = defaultConfig.createSection("messages");
        messages.set("deathLocation", "You died at [{0}, {1}, {2}]");
        messages.set("barrelCreated", "Created death barrel.");
        messages.set("barrelInventoryTitle", "DeathBarrel");

        config.setDefaults(defaultConfig);
        config.options().copyDefaults(true);
        saveConfig();
    }

    /**
     * Places a barrel block while respecting other plugins / player build
     * permission
     *
     * @param player   - Player to act as.
     * @param location - Where to place the barrel block
     * @return The barrel block placed or null on failure
     */
    private Barrel placeBarrel(Player player, Location location) {
        Block block = location.getBlock();
        Block blockBelow = location.clone().subtract(0, 1, 0).getBlock();
        ItemStack item = new ItemStack(Material.BARREL, 1);

        BlockPlaceEvent blockPlaceEvent = new BlockPlaceEvent(block, block.getState(), blockBelow, item, player, true,
                EquipmentSlot.HAND);
        Bukkit.getServer().getPluginManager().callEvent(blockPlaceEvent);

        if (blockPlaceEvent.isCancelled()) {
            return null;
        }

        block.setType(Material.BARREL);
        // Set identifier to be able to recognise barrel type in the future

        // To fix java.lang.ClassCastException:
        // org.bukkit.craftbukkit.v1_14_R1.block.CraftBlockState cannot be cast to
        // org.bukkit.block.Barrel
        block.getState().setType(Material.BARREL);
        BlockState state = block.getState();
        if (!(state instanceof Barrel)) {
            return null; // Block place failed
        }

        Barrel barrel = (Barrel) block.getState();
        barrel.setCustomName(config.getString("messages.barrelInventoryTitle"));

        // Set barrel metadata which can be used to identify it and its owner.
        PersistentDataContainer data = barrel.getPersistentDataContainer();
        data.set(new NamespacedKey(this, "isDeathBarrel"), PersistentDataType.INTEGER, 1);
        data.set(new NamespacedKey(this, "version"), PersistentDataType.STRING, this.getDescription().getVersion());
        data.set(new NamespacedKey(this, "ownerUUID"), PersistentDataType.STRING, player.getUniqueId().toString());
        barrel.update();

        return barrel;
    }

    /**
     * Test if a barrel is a DeathBarrel
     *
     * @param barrel - Barrel block to test
     * @return true if DeathBarrel, false otherwise
     */
    private boolean isDeathBarrel(Barrel barrel) {
        return barrel.getPersistentDataContainer().has(new NamespacedKey(this, "isDeathBarrel"),
                PersistentDataType.INTEGER);
    }

    private boolean isDeathBarrel(InventoryHolder inventoryHolder) {
        if (inventoryHolder == null || !(inventoryHolder instanceof Barrel)) {
            return false;
        }
        return isDeathBarrel((Barrel) inventoryHolder);
    }

    /**
     * Test if a player owns a barrel. This means it was created as a result of
     * their death.
     * 
     * @param player - Player to check ownership for.
     * @param barrel - Barrel to check.
     * @return true if the barrel belongs to the player, false otherwise. If given
     *         player or barrel is null this check will always return false.
     */
    private boolean isOwner(Player player, Barrel barrel) {
        if (player == null || barrel == null) {
            return false;
        }
        String ownerUUID = barrel.getPersistentDataContainer().get(new NamespacedKey(this, "ownerUUID"),
                PersistentDataType.STRING);
        if (ownerUUID == null) {
            return false;
        }
        return player.getUniqueId().toString().equals(ownerUUID);
    }

    /**
     * Test if a Block is a DeathBarrel
     *
     * @param barrelBlock - Barrel block to test
     * @return true if DeathBarrel, false otherwise
     */
    private boolean isDeathBarrel(Block barrelBlock) {
        return barrelBlock.getBlockData().getMaterial().equals(Material.BARREL)
                && isDeathBarrel((Barrel) barrelBlock.getState());
    }

    /**
     * Create death barrels and store player drops in them. Multiple barrels may be
     * stacked on top of location to account for drop list size. Item drops
     * successfully placed in barrels will be removed from the drop list. Issues
     * with barrel placement (permissions or other event cancel) can lead to items
     * from drop list not being processed. The calling method should account for
     * this.
     *
     * @param player   - Player whose drops should be stored. Will be used for
     *                 barrel place event.
     * @param drops    - Death drops of player.
     * @param location - Location where barrels should be placed.
     * @return true if death barrel/s have been placed, false otherwise
     */
    private boolean createDeathBarrels(Player player, List<ItemStack> drops, Location location) {
        if (drops.size() == 0) {
            return false;
        }
        int barrelCount = (int) Math.ceil(drops.size() / (float) barrelCapacity);
        int placedBarrelCount = 0;

        for (int i = 0; i < barrelCount; i++) {
            Barrel barrel = placeBarrel(player, i == 0 ? location : location.clone().add(0, i, 0));
            if (barrel == null) {
                // If barrel placement fails skip it. This can lead to not all drops being
                // processed.
                // Remaining items not stored in barrels will be dropped on the floor.
                break;
            }
            placedBarrelCount++;

            ItemStack[] dropPartition = new ItemStack[barrelCapacity];
            for (int j = 0; drops.size() > 0 && j < barrelCapacity; j++) {
                dropPartition[j] = drops.remove(0);
            }
            barrel.getInventory().setContents(dropPartition);
        }

        return placedBarrelCount > 0;
    }

    @EventHandler
    public void onPlayerDeathEvent(PlayerDeathEvent event) {
        Player player = event.getEntity();

        if (!player.hasPermission("deathbarrel.use")) {
            return;
        }

        Location location = player.getLocation();
        List<ItemStack> drops = event.getDrops();
        while (location.getBlockY() < 2) {
            location.setY(location.getBlockY() + 1);
        }
        /* Found the air to the terrain surface */
        while (location.getBlock().getType() != Material.AIR && location.getBlock().getType() != Material.VOID_AIR
                && location.getBlock().getType() != Material.CAVE_AIR) {
            if (location.getY() > location.getWorld().getMaxHeight() - 2)
                return;
            location.setY(location.getBlockY() + 1);
        }

        boolean created = createDeathBarrels(player, drops, location);

        player.sendMessage(
                Util.fillArgs(getConfig().getString("messages.deathLocation"), String.valueOf(location.getBlockX()),
                        String.valueOf(location.getBlockY()), String.valueOf(location.getBlockZ())));
        if (created) {
            player.sendMessage(Util.fillArgs(getConfig().getString("messages.barrelCreated")));
        }
    }

    @EventHandler
    public void onBlockBreakEvent(BlockBreakEvent event) {
        Block block = event.getBlock();

        if (!isDeathBarrel(block)) {
            return;
        }

        Barrel barrel = (Barrel) block.getState();
        Player player = event.getPlayer();

        if (config.getBoolean("protectFromOtherPlayers") && player != null
                && !player.hasPermission("deathbarrel.accessprotected") && !isOwner(player, barrel)) {
            event.setCancelled(true);
            player.sendMessage("This barrel is locked. Only the owner can break it.");
            return;
        }

        // We allow the barrel to be broken, but don't drop the actual barrel
        // item. Otherwise players can create unlimited barrels by dying
        // repeatedly.
        event.setDropItems(false); // Only cancels container item drop
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        /* Clone the list */
        List<Block> blocks = new ArrayList<>(e.blockList());
        for (Block block : blocks) {
            if (isDeathBarrel(block)) {
                e.blockList().remove(block);
            }
        }
    }

    @EventHandler
    public void onExplode(BlockExplodeEvent e) {
        /* Clone the list */
        List<Block> blocks = new ArrayList<>(e.blockList());
        for (Block block : blocks) {
            if (isDeathBarrel(block)) {
                e.blockList().remove(block);
            }
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        // This event handler is only used for the barrel owner check.
        if (!config.getBoolean("protectFromOtherPlayers")) {
            return;
        }

        InventoryHolder inventoryHolder = event.getInventory().getHolder();

        // Opening inventory is not a death barrel
        if (!isDeathBarrel(inventoryHolder)) {
            return;
        }

        Barrel barrel = (Barrel) inventoryHolder;
        Player player = (Player) event.getPlayer();

        if (!player.hasPermission("deathbarrel.accessprotected") && !isOwner(player, barrel)) {
            event.setCancelled(true);
            player.sendMessage("This barrel is locked. Only the owner can access it.");
            return;
        }

    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // Feature disabled via config
        if (!config.getBoolean("removeOnEmpty")) {
            return;
        }

        Inventory inventory = event.getInventory();
        InventoryHolder inventoryHolder = event.getInventory().getHolder();

        // Closing inventory is not a death barrel
        if (!isDeathBarrel(inventoryHolder)) {
            return;
        }

        // Death barrel is not empty
        if (!inventory.isEmpty()) {
            return;
        }

        // Remove empty death barrel
        Barrel deathBarrel = (Barrel) inventoryHolder;
        deathBarrel.getBlock().setType(Material.AIR);
    }
}
