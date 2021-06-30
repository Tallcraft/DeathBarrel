package com.tallcraft.deathbarrel;

import io.papermc.lib.PaperLib;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;


public class BarrelCleanupTask extends BukkitRunnable {
  private DeathBarrel plugin;
  private Location location;
  private long maxAgeSeconds;
  private Chunk chunk;

  BarrelCleanupTask(DeathBarrel plugin, Location location, long maxAgeSeconds) {
    this.plugin = plugin;
    this.location = location;
    this.maxAgeSeconds = maxAgeSeconds;
  }

  BarrelCleanupTask(DeathBarrel plugin, Location location, long maxAgeSeconds, Chunk chunk) {
    this(plugin, location, maxAgeSeconds);
    this.chunk = chunk;
  }

  @Override
  public void run() {

    // If we have the chunk we can directly check the barrel and remove it if it has expired.
    if (this.chunk != null) {
      Block block = chunk.getWorld().getBlockAt(this.location.getBlockX(),
          this.location.getBlockY(),
          this.location.getBlockZ());
      this.plugin.cleanupBarrelIfExpired(block, this.maxAgeSeconds);
      return;
    }

    // If we don't have the chunk, get it async.
    PaperLib.getChunkAtAsync(this.location).thenAccept(chunk -> {
      if (chunk == null) {
        return;
      }

      // Now that we have the chunk loaded we can remove the barrel. We need to create a separate
      // synchronous task for that, since we can't modify the chunk async.
      new BarrelCleanupTask(this.plugin, this.location, this.maxAgeSeconds, chunk)
        .runTask(this.plugin);
    });
  }
}
