package com.nisovin.shopkeepers.villagers;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.world.ChunkLoadEvent;

import com.nisovin.shopkeepers.Settings;
import com.nisovin.shopkeepers.util.Log;
import com.nisovin.shopkeepers.util.TextUtils;

/**
 * Prevents spawning of regular villagers (including wandering traders).
 */
public class BlockVillagerSpawnListener implements Listener {

	public BlockVillagerSpawnListener() {
	}

	private boolean isSpawnBlockingBypassed(SpawnReason spawnReason) {
		switch (spawnReason) {
		case CUSTOM: // Plugins
		case SPAWNER_EGG:
			// Not obtainable in vanilla Minecraft, regular item usage of shopkeeper creation item is handled separately.
		case SPAWNER: // Not obtainable in vanilla Minecraft
		case CURED: // Handled separately
			return true;
		default:
			return false;
		}
	}

	private boolean isSpawningBlocked(EntityType entityType) {
		if (Settings.blockVillagerSpawns && entityType == EntityType.VILLAGER) return true;
		if (Settings.blockWanderingTraderSpawns && (entityType == EntityType.WANDERING_TRADER || entityType == EntityType.TRADER_LLAMA)) return true;
		return false;
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	void onSpawn(CreatureSpawnEvent event) {
		SpawnReason spawnReason = event.getSpawnReason();
		if (this.isSpawnBlockingBypassed(spawnReason)) return;

		EntityType entityType = event.getEntityType();
		// Prevent spawning of villagers, wandering traders and their trader llamas:
		if (this.isSpawningBlocked(entityType)) {
			Log.debug(() -> "Preventing mob spawn of " + entityType + " at " + TextUtils.getLocationString(event.getLocation()));
			event.setCancelled(true);
		}
	}

	// LOW priority so that other plugins don't have to process those meant-to-be-removed entities.
	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	void onChunkLoad(ChunkLoadEvent event) {
		// Remove villagers that got spawned as part of chunk generation:
		if (!event.isNewChunk()) return;
		for (Entity entity : event.getChunk().getEntities()) {
			EntityType entityType = entity.getType();
			if (this.isSpawningBlocked(entityType)) {
				Log.debug(() -> "Preventing mob spawn (chunk-gen) of " + entityType + " at " + TextUtils.getLocationString(entity.getLocation()));
				entity.remove();
			}
		}
	}
}
