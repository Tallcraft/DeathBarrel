package com.tallcraft.deathbarrel;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class DeathBarrel extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        // Plugin startup logic
        getServer().getPluginManager().registerEvents(this, this);
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
        if(drops.size() == 0) {
            return;
        }

        Block bottomBlock = location.getBlock();
        bottomBlock.setType(Material.BARREL);
        Barrel bottomBarrel = (Barrel) bottomBlock.getState();
        Inventory bottomBarrelInventory = bottomBarrel.getInventory();

        int dropSize = drops.size();
        int barrelCapacity = bottomBarrelInventory.getSize();

        if(barrelCapacity >= dropSize) {
            bottomBarrelInventory.setContents(drops.toArray(new ItemStack[0]));
        } else {
            // Drops dont fit in one barrel, lets overflow into a second one

            // Fill bottom barrel
            bottomBarrelInventory.setContents(drops.subList(0, barrelCapacity).toArray(new ItemStack[0]));

            // Fill top barrel
            Block topBlock = location.add(0, 1, 0).getBlock();
            topBlock.setType(Material.BARREL);
            Barrel topBarrel = (Barrel) topBlock.getState();
            Inventory topBarrelInventory = topBarrel.getInventory();
            topBarrelInventory.setContents(drops.subList(barrelCapacity, dropSize).toArray(new ItemStack[0]));
        }

        // Clear drops, they are stored in the barrel/s now
        drops.clear();
    }
}
