package com.tallcraft.deathbarrel;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class DeathBarrel extends JavaPlugin implements Listener {

    private final int barrelCapacity = InventoryType.BARREL.getDefaultSize();
    private final String barrelIdentifier = "DeathBarrel";

    @Override
    public void onEnable() {
        // All you have to do is adding this line in your onEnable method:
        saveDefaultConfig();
        reloadConfig();
        Metrics metrics = new Metrics(this);
        getServer().getPluginManager().registerEvents(this, this);
    }


    /**
     * Places a barrel block while respecting other plugins / player build permission
     *
     * @param player   - Player to act as.
     * @param location - Where to place the barrel block
     * @return The barrel block placed or null on failure
     */
    private Barrel placeBarrel(Player player, Location location) {
        Block block = location.getBlock();
        Block blockBelow = location.clone().subtract(0, 1, 0).getBlock();
        ItemStack item = new ItemStack(Material.BARREL, 1);

        BlockPlaceEvent blockPlaceEvent = new BlockPlaceEvent(block, block.getState(), blockBelow, item, player, true, EquipmentSlot.HAND);
        Bukkit.getServer().getPluginManager().callEvent(blockPlaceEvent);

        if (blockPlaceEvent.isCancelled()) {
            return null;
        }

        block.setType(Material.BARREL);

        // Set identifier to be able to recognise barrel type in the future
        Barrel barrel = (Barrel) block.getState();
        barrel.setCustomName(barrelIdentifier);

        barrel.update();

        return barrel;
    }

    /**
     * Test if a barrel is a DeathBarrel
     * @param barrel - Barrel block to test
     * @return true if DeathBarrel, false otherwise
     */
    private boolean isDeathBarrel(Barrel barrel) {
        String customName = barrel.getCustomName();
        return customName != null && customName.equals(barrelIdentifier);
    }

    /**
     * Test if a Block is a DeathBarrel
     * @param barrelBlock - Barrel block to test
     * @return true if DeathBarrel, false otherwise
     */
    private boolean isDeathBarrel(Block barrelBlock) {
        return barrelBlock.getBlockData().getMaterial().equals(Material.BARREL)
                && isDeathBarrel((Barrel) barrelBlock.getState());
    }

    /**
     * Create death barrels and store player drops in them.
     * Multiple barrels may be stacked on top of location to account for drop list size.
     * Item drops successfully placed in barrels will be removed from the drop list.
     * Issues with barrel placement (permissions or other event cancel) can lead to items from
     * drop list not being processed. The calling method should account for this.
     *
     * @param player   - Player whose drops should be stored. Will be used for barrel place event.
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
                // If barrel placement fails skip it. This can lead to not all drops being processed.
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

        /* Found the air to the terrain surface*/
        while (location.getBlock().getType()!=Material.AIR && location.getBlock().getType()!=Material.VOID_AIR && location.getBlock().getType()!=Material.CAVE_AIR){
            if(location.getY() > 254)
                return;
            location.setY(location.getY()+1);
        }

        boolean created = createDeathBarrels(player, drops, location);

        player.sendMessage("You died at [" + location.getBlockX() + ", " + location.getBlockY()
                + ", " + location.getBlockZ() + "]");
        player.sendMessage(Util.fillArgs(getConfig().getString("msgs.deathLocation"),String.valueOf(location.getBlockX()),String.valueOf(location.getBlockY()),String.valueOf(location.getBlockZ())));
        if (created) {
            player.sendMessage(Util.fillArgs(getConfig().getString("msgs.barrelCreated")));
        }
    }

    @EventHandler
    public void onBlockBreakEvent(BlockBreakEvent event) {
        if(isDeathBarrel(event.getBlock())) {
            event.setDropItems(false); // Only cancels container item drop
        }
    }
    @EventHandler
    public void onExplode(EntityExplodeEvent e){
        /* Clone the list */
        List<Block> blocks = new ArrayList<>(e.blockList());
        for (Block block : blocks){
            if(isDeathBarrel(block)){
                e.blockList().remove(block);
            }
        }
    }
    @EventHandler
    public void onExplode(BlockExplodeEvent e){
        /* Clone the list */
        List<Block> blocks = new ArrayList<>(e.blockList());
        for (Block block : blocks){
            if(isDeathBarrel(block)){
                e.blockList().remove(block);
            }
        }
    }
}
