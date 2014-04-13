package com.nisovin.shopkeepers;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.nisovin.shopkeepers.compat.NMSManager;
import com.nisovin.shopkeepers.events.ShopkeeperDeletedEvent;
import com.nisovin.shopkeepers.events.ShopkeeperEditedEvent;
import com.nisovin.shopkeepers.shoptypes.PlayerShopkeeper;

class ShopListener implements Listener {

	ShopkeepersPlugin plugin;

	Map<String, Long> lastPurchase;

	public ShopListener(ShopkeepersPlugin plugin) {
		this.plugin = plugin;
		this.lastPurchase = new HashMap<String, Long>();
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	void onBlockPlace(BlockPlaceEvent event) {
		if (event.getBlock().getType() == Material.CHEST) {
			Block b = event.getBlock();
			List<String> list = plugin.recentlyPlacedChests.get(event.getPlayer().getName());
			if (list == null) {
				list = new LinkedList<String>();
				plugin.recentlyPlacedChests.put(event.getPlayer().getName(), list);
			}
			list.add(b.getWorld().getName() + "," + b.getX() + "," + b.getY() + "," + b.getZ());
			if (list.size() > 5) {
				list.remove(0);
			}
		}
	}

	@EventHandler
	void onInventoryClose(InventoryCloseEvent event) {
		String name = event.getPlayer().getName();
		if (plugin.editing.containsKey(name)) {
			ShopkeepersPlugin.debug("Player " + name + " closed editor window");
			String id = plugin.editing.remove(name);
			Shopkeeper shopkeeper = plugin.activeShopkeepers.get(id);
			if (shopkeeper != null) {
				if (plugin.isShopkeeperEditorWindow(event.getInventory())) {
					shopkeeper.onEditorClose(event);
					plugin.closeTradingForShopkeeper(id);
					plugin.save();
				}
			}
		} else if (plugin.purchasing.containsKey(name)) {
			ShopkeepersPlugin.debug("Player " + name + " closed trade window");
			plugin.purchasing.remove(name);
		} else if (plugin.hiring.containsKey(name)) {
			ShopkeepersPlugin.debug("Player " + name + " closed hire window");
			plugin.hiring.remove(name);
		}
	}

	@EventHandler
	void onInventoryClick(InventoryClickEvent event) {
		Inventory inventory = event.getInventory();
		String playerName = event.getWhoClicked().getName();
		// shopkeeper editor click
		if (plugin.isShopkeeperEditorWindow(inventory)) {
			if (plugin.editing.containsKey(playerName)) {
				Player player = (Player) event.getWhoClicked();
				// get the shopkeeper being edited
				String id = plugin.editing.get(playerName);
				Shopkeeper shopkeeper = plugin.activeShopkeepers.get(id);
				if (shopkeeper != null) {
					// editor click
					EditorClickResult result = shopkeeper.onEditorClick(event);
					if (result == EditorClickResult.DELETE_SHOPKEEPER) {
						// close inventories
						plugin.closeTradingForShopkeeper(id);

						// return egg
						if (Settings.deletingPlayerShopReturnsEgg && shopkeeper instanceof PlayerShopkeeper) {
							ItemStack creationItem = Settings.createCreationItem();
							HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(creationItem);
							if (!remaining.isEmpty()) {
								player.getWorld().dropItem(shopkeeper.getActualLocation(), creationItem);
							}
						}

						// remove shopkeeper
						plugin.activeShopkeepers.remove(id);
						plugin.allShopkeepersByChunk.get(shopkeeper.getChunk()).remove(shopkeeper);

						// run event
						Bukkit.getPluginManager().callEvent(new ShopkeeperDeletedEvent(player, shopkeeper));

						// save
						plugin.save();
					} else if (result == EditorClickResult.DONE_EDITING) {
						// end the editing session
						plugin.closeTradingForShopkeeper(id);
						// run event
						Bukkit.getPluginManager().callEvent(new ShopkeeperEditedEvent(player, shopkeeper));
						// save
						plugin.save();
					} else if (result == EditorClickResult.SAVE_AND_CONTINUE) {
						// run event
						Bukkit.getPluginManager().callEvent(new ShopkeeperEditedEvent(player, shopkeeper));
						// save
						plugin.save();
					} else if (result == EditorClickResult.SET_NAME) {
						// close editor window and ask for new name
						plugin.closeInventory(player);
						plugin.editing.remove(playerName);
						plugin.naming.put(playerName, id);
						plugin.sendMessage(player, Settings.msgTypeNewName);
						// run event
						Bukkit.getPluginManager().callEvent(new ShopkeeperEditedEvent(player, shopkeeper));
						// save
						plugin.save();
					}
				} else {
					event.setCancelled(true);
					plugin.closeInventory(player);
				}
			} else {
				event.setCancelled(true);
				plugin.closeInventory(event.getWhoClicked());
			}
			return;
		}

		// hire click
		if (plugin.isShopkeeperHireWindow(inventory)) {
			event.setCancelled(true);
			String id = plugin.hiring.get(playerName);
			Shopkeeper shopkeeper = plugin.activeShopkeepers.get(id);
			if (shopkeeper != null && shopkeeper instanceof PlayerShopkeeper) {
				int slot = event.getRawSlot();
				if (slot == 2 || slot == 6) {
					Player player = (Player) event.getWhoClicked();
					ItemStack[] inv = player.getInventory().getContents();
					ItemStack hireCost = ((PlayerShopkeeper) shopkeeper).getHireCost().clone();
					for (int i = 0; i < inv.length; i++) {
						ItemStack item = inv[i];
						if (item != null && item.isSimilar(hireCost)) {
							if (item.getAmount() > hireCost.getAmount()) {
								item.setAmount(item.getAmount() - hireCost.getAmount());
								hireCost.setAmount(0);
								break;
							} else if (item.getAmount() == hireCost.getAmount()) {
								inv[i] = null;
								hireCost.setAmount(0);
								break;
							} else {
								hireCost.setAmount(hireCost.getAmount() - item.getAmount());
								inv[i] = null;
							}
						}
					}
					if (hireCost.getAmount() == 0) {
						// hire it
						plugin.hiring.remove(playerName);
						plugin.closeInventory(player);
						player.getInventory().setContents(inv);
						((PlayerShopkeeper) shopkeeper).setForHire(false, null);
						((PlayerShopkeeper) shopkeeper).setOwner(playerName);
						plugin.save();
						plugin.sendMessage(player, Settings.msgHired);

					} else {
						// not enough money
						plugin.hiring.remove(playerName);
						plugin.closeInventory(player);
						plugin.sendMessage(player, Settings.msgCantHire);
					}
				}
			} else {
				plugin.hiring.remove(playerName);
				plugin.closeInventory(event.getWhoClicked());
			}
			return;
		}

		// purchase click
		if (inventory.getName().equals("mob.villager") && plugin.purchasing.containsKey(playerName)) {
			Player player = (Player) event.getWhoClicked();
			// prevent unwanted special clicks
			InventoryAction action = event.getAction();
			if (action == InventoryAction.COLLECT_TO_CURSOR || (event.getRawSlot() == 2 && !event.isLeftClick())) {
				event.setCancelled(true);
				updateInventoryLater(player);
				return;
			}
			
			if (event.getRawSlot() != 2) {
				return;
			}

			// get shopkeeper
			String id = plugin.purchasing.get(playerName);
			Shopkeeper shopkeeper = plugin.activeShopkeepers.get(id);
			ItemStack item = event.getCurrentItem();
			if (shopkeeper != null && item != null) {
				// prevent double-clicks (ugly fix, but necessary to prevent dupes)
				/*
				 * Long last = lastPurchase.remove(playerName);
				 * long curr = System.currentTimeMillis();
				 * if (last != null && last.longValue() > curr - 500) {
				 * event.setCancelled(true);
				 * return;
				 * }
				 * lastPurchase.put(playerName, curr);
				 */

				// verify purchase
				ItemStack item1 = inventory.getItem(0);
				ItemStack item2 = inventory.getItem(1);
				boolean ok = false;
				List<ItemStack[]> recipes = shopkeeper.getRecipes();
				ItemStack[] selectedRecipe = null;

				int currentRecipePage = NMSManager.getProvider().getCurrentRecipePage(inventory);
				if (currentRecipePage >= 0 && currentRecipePage < recipes.size()) {
					// scan the current recipe:
					selectedRecipe = recipes.get(currentRecipePage);
					if (itemEqualsAtLeast(item1, selectedRecipe[0], true) && itemEqualsAtLeast(item2, selectedRecipe[1], true) && itemEqualsAtLeast(item, selectedRecipe[2], false)) {
						ok = true;
					}
				} else {
					// scan all recipes:
					for (ItemStack[] recipe : recipes) {
						if (itemEqualsAtLeast(item1, recipe[0], true) && itemEqualsAtLeast(item2, recipe[1], true) && itemEqualsAtLeast(item, recipe[2], false)) {
							ok = true;
							break;
						}
					}
				}
				if (!ok) {
					ShopkeepersPlugin.debug("Invalid trade by " + playerName + " with shopkeeper at " + shopkeeper.getPositionString() + ":");
					ShopkeepersPlugin.debug("  " + itemStackToString(item1) + " and " + itemStackToString(item2) + " for " + itemStackToString(item));
					if (selectedRecipe != null) {
						ShopkeepersPlugin.debug("  Required:" + itemStackToString(selectedRecipe[0]) + " and " + itemStackToString(selectedRecipe[1]));
					}
					event.setCancelled(true);
					updateInventoryLater(player);
					return;
				}

				// send purchase click to shopkeeper
				shopkeeper.onPurchaseClick(event);

				// log purchase
				if (Settings.enablePurchaseLogging && !event.isCancelled()) {
					try {
						String owner = (shopkeeper instanceof PlayerShopkeeper ? ((PlayerShopkeeper) shopkeeper).getOwner() : "[Admin]");
						File file = new File(plugin.getDataFolder(), "purchases-" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".csv");
						boolean isNew = !file.exists();
						BufferedWriter writer = new BufferedWriter(new FileWriter(file, true));
						if (isNew) writer.append("TIME,PLAYER,SHOP TYPE,SHOP POS,OWNER,ITEM TYPE,DATA,QUANTITY,CURRENCY 1,CURRENCY 2\n");
						writer.append("\"" + new SimpleDateFormat("HH:mm:ss").format(new Date()) + "\",\"" + playerName + "\",\"" + shopkeeper.getType().name() + "\",\"" + shopkeeper
								.getPositionString() + "\",\"" + owner + "\",\"" + item.getType().name() + "\",\"" + item.getDurability() + "\",\"" + item.getAmount() + "\",\"" + (item1 != null ? item1
								.getType().name() + ":" + item1.getDurability() : "") + "\",\"" + (item2 != null ? item2.getType().name() + ":" + item2.getDurability() : "") + "\"\n");
						writer.close();
					} catch (IOException e) {
						plugin.getLogger().severe("IO exception while trying to log purchase");
					}
				}
			}
		}
	}
	
	@SuppressWarnings("deprecation")
	private void updateInventoryLater(final Player player) {
		Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
			
			@Override
			public void run() {
				player.updateInventory();
			}
		}, 3L);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onChat(AsyncPlayerChatEvent event) {
		final Player player = event.getPlayer();
		final String name = player.getName();
		if (plugin.naming.containsKey(name)) {
			event.setCancelled(true);
			final String message = event.getMessage().trim();
			Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
				public void run() {
					String id = plugin.naming.remove(name);
					Shopkeeper shopkeeper = plugin.activeShopkeepers.get(id);

					// update name
					if (message.isEmpty() || message.equals("-")) {
						// remove name
						shopkeeper.setName("");
					} else {
						// validate name
						if (!message.matches("^" + Settings.nameRegex + "$")) {
							plugin.sendMessage(player, Settings.msgNameInvalid);
							return;
						}
						// set name
						if (message.length() > 32) {
							shopkeeper.setName(message.substring(0, 32));
						} else {
							shopkeeper.setName(message);
						}
					}

					plugin.sendMessage(player, Settings.msgNameSet);
					plugin.closeTradingForShopkeeper(id);

					// run event
					Bukkit.getPluginManager().callEvent(new ShopkeeperEditedEvent(player, shopkeeper));

					// save
					plugin.save();
				}
			});
		}
	}

	@EventHandler
	void onEntityDamage(EntityDamageEvent event) {
		// don't allow damaging shopkeepers!
		if (plugin.activeShopkeepers.containsKey("entity" + event.getEntity().getEntityId())) {
			event.setCancelled(true);
			if (event instanceof EntityDamageByEntityEvent) {
				EntityDamageByEntityEvent evt = (EntityDamageByEntityEvent) event;
				if (evt.getDamager() instanceof Monster) {
					evt.getDamager().remove();
				}
			}
		}
	}

	private boolean itemEqualsAtLeast(ItemStack item1, ItemStack item2, boolean checkAmount) {
		boolean item1Empty = (item1 == null || item1.getType() == Material.AIR);
		boolean item2Empty = (item2 == null || item2.getType() == Material.AIR);
		if (item1Empty) {
			return item2Empty;
		} else {
			return item1.isSimilar(item2) && (!checkAmount || item1.getAmount() >= item2.getAmount());
		}
	}

	private String getNameOfItem(ItemStack item) {
		if (item != null && item.getType() != Material.AIR && item.hasItemMeta()) {
			ItemMeta meta = item.getItemMeta();
			if (meta.hasDisplayName()) {
				return meta.getDisplayName();
			}
		}
		return "";
	}

	private String itemStackToString(ItemStack item) {
		if (item == null || item.getType() == Material.AIR) return "(nothing)";
		String displayName = this.getNameOfItem(item);
		StringBuilder result = new StringBuilder();
		result.append(item.getType().name()).append(':').append(item.getDurability());
		if (!displayName.isEmpty()) result.append(':').append(displayName);
		if (ShopkeepersPlugin.isDebug()) {
			// append more, detailed (possibly ugly) information:
			result.append(':').append(item.getItemMeta().toString());
		}
		return result.toString();
	}

	/*
	 * private static boolean itemNamesEqual(ItemStack item1, ItemStack item2) {
	 * String name1 = getNameOfItem(item1);
	 * String name2 = getNameOfItem(item2);
	 * return (name1.equals(name2));
	 * }
	 */

	@EventHandler(priority = EventPriority.LOW)
	void onPlayerInteract1(PlayerInteractEvent event) {
		// prevent opening shop chests
		if (event.hasBlock() && event.getClickedBlock().getType() == Material.CHEST) {
			Player player = event.getPlayer();
			Block block = event.getClickedBlock();

			// check for protected chest
			if (!player.hasPermission("shopkeeper.bypass")) {
				if (plugin.isChestProtected(player, block)) {
					event.setCancelled(true);
					return;
				}
				for (BlockFace face : plugin.chestProtectFaces) {
					if (block.getRelative(face).getType() == Material.CHEST) {
						if (plugin.isChestProtected(player, block.getRelative(face))) {
							event.setCancelled(true);
							return;
						}
					}
				}
			}
		}
	}

	@EventHandler
	void onChunkLoad(ChunkLoadEvent event) {
		final Chunk chunk = event.getChunk();
		Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
			public void run() {
				if (chunk.isLoaded()) {
					plugin.loadShopkeepersInChunk(chunk);
				}
			}
		}, 2);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	void onChunkUnload(ChunkUnloadEvent event) {
		List<Shopkeeper> shopkeepers = plugin.allShopkeepersByChunk.get(event.getWorld().getName() + "," + event.getChunk().getX() + "," + event.getChunk().getZ());
		if (shopkeepers != null) {
			ShopkeepersPlugin.debug("Unloading " + shopkeepers.size() + " shopkeepers in chunk " + event.getChunk().getX() + "," + event.getChunk().getZ());
			for (Shopkeeper shopkeeper : shopkeepers) {
				// skip sign shops: those are meant to not be removed and stay 'active' all the time currently
				if (!shopkeeper.needsSpawned()) continue;
				plugin.activeShopkeepers.remove(shopkeeper.getId());
				shopkeeper.remove();
			}
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	void onWorldLoad(WorldLoadEvent event) {
		for (Chunk chunk : event.getWorld().getLoadedChunks()) {
			plugin.loadShopkeepersInChunk(chunk);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	void onWorldUnload(WorldUnloadEvent event) {
		String worldName = event.getWorld().getName();
		Iterator<Shopkeeper> iter = plugin.activeShopkeepers.values().iterator();
		int count = 0;
		while (iter.hasNext()) {
			Shopkeeper shopkeeper = iter.next();
			if (shopkeeper.getWorldName().equals(worldName)) {
				shopkeeper.remove();
				iter.remove();
				count++;
			}
		}
		ShopkeepersPlugin.debug("Unloaded " + count + " shopkeepers in unloaded world " + worldName);
	}

	@EventHandler
	void onPlayerQuit(PlayerQuitEvent event) {
		String name = event.getPlayer().getName();
		plugin.editing.remove(name);
		plugin.purchasing.remove(name);
		plugin.selectedShopType.remove(name);
		plugin.selectedChest.remove(name);
		plugin.recentlyPlacedChests.remove(name);
	}

}