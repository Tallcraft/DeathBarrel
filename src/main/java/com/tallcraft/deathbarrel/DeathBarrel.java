package com.tallcraft.deathbarrel;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class DeathBarrel extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        // All you have to do is adding this line in your onEnable method:
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
        return (Barrel) block.getState();
    }


    @EventHandler
    public void onPlayerDeathEvent(PlayerDeathEvent event) {
        Player player = event.getEntity();

        if (!player.hasPermission("deathbarrel.use")) {
            return;
        }

        Location location = player.getLocation();
        List<ItemStack> drops = event.getDrops();
        // Player died with empty inventory
        if (drops.size() == 0) {
            return;
        }

        Barrel bottomBarrel = placeBarrel(player, location);
        if (bottomBarrel == null) {
            // Placing the barrel failed (something cancelled the block place event)
            return;
        }

        Inventory bottomBarrelInventory = bottomBarrel.getInventory();

        int dropSize = drops.size();
        int barrelCapacity = bottomBarrelInventory.getSize();

        if (barrelCapacity >= dropSize) {
            bottomBarrelInventory.setContents(drops.toArray(new ItemStack[0]));
        } else {
            // Drops dont fit in one barrel, lets overflow into a second one

            // Fill bottom barrel
            bottomBarrelInventory.setContents(drops.subList(0, barrelCapacity).toArray(new ItemStack[0]));

            // Fill top barrel
            Barrel topBarrel = placeBarrel(player, location.add(0, 1, 0));
            if (topBarrel == null) {
                // Placing the barrel failed (something cancelled the block place event)
                return;
            }
            Inventory topBarrelInventory = topBarrel.getInventory();
            topBarrelInventory.setContents(drops.subList(barrelCapacity, dropSize).toArray(new ItemStack[0]));
        }

        // Clear drops, they are stored in the barrel/s now
        drops.clear();
    }
}
