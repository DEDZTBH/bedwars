package bedwars;

import org.bukkit.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.block.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;

import static bedwars.Buy_menu_commandKt.getMasterCommand;

import bedwars.protection.BlockProtection;
import bedwars.shop.ShopKeeperManager;


public final class Main extends JavaPlugin implements Listener {

	/* GLOBAL VARIABLES */

	int gamephase = 1; // 1 = lobby, 2 = ingame
	FileConfiguration config;
	Hashtable<String, TeamInfo> teaminfos = new Hashtable<String, TeamInfo>();
	ArrayList<ResourceSpawner> diamondgens = new ArrayList<ResourceSpawner>();
	ArrayList<ResourceSpawner> emeraldgens = new ArrayList<ResourceSpawner>();
	Location playloclow, playlochigh, structureloclow, structurelochigh, spectatespawn, lobby;
	Scoreboard sb;

    private final PlayerUpgrades playerUpgrades = new PlayerUpgrades();
    private BuyConfig buyConfig;

	public static Main INSTANCE;


	/* COMMAND LISTENER */

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (gamephase == 1) {

			if (label.equalsIgnoreCase("setloc")) {
				if (!(sender instanceof Player)) return true;
				if (getLocation(config, args[0]) == null) return true;
				saveLocation(config, args[0], ((Player)sender).getLocation());
				saveConfig();
				config = getConfig();
				initializeLocations();
				sender.sendMessage("Location " + args[0] + " successfully updated!");

			} else if (label.equalsIgnoreCase("jointeam")) {
				if (!(sender instanceof Player)) return true;
				Player p = (Player) sender;
				if (sb.getTeam(args[0]) != null) {
					sb.getTeam(args[0]).addPlayer(p);
					p.sendMessage("Joined team " + args[0]);
				} else {
					p.sendMessage("Please enter a valid team color (in lower case)!");
				}
				updateDisplay();

			} else if (label.equalsIgnoreCase("startgame")) {
				if (!(sender instanceof Player)) return true;
				startGame(sender);
				updateDisplay();
			} else if (label.equalsIgnoreCase("npc")) {
				if (!(sender instanceof Player)) return true;
				Player p = ((Player) sender);
				ShopKeeperManager.INSTANCE.spawn(args[0], p.getLocation(), (evt, shopKeeper) -> {
					sender.sendMessage(shopKeeper.getName() + ": ?");
					return null;
				});
			} else if (label.equalsIgnoreCase("cleannpc")) {
				if (!(sender instanceof Player)) return true;
				ShopKeeperManager.INSTANCE.cleanUp();
			}
		}

		return true;
  }


  /* GAME MANAGER */

	@Override
	public void onEnable() {
		INSTANCE = this;
		// initialize shit
		saveDefaultConfig();
		config = getConfig();
		sb = Bukkit.getScoreboardManager().getNewScoreboard();
		initializeLocations();
		getServer().getPluginManager().registerEvents(this, this);
		getServer().getPluginManager().registerEvents(ShopKeeperManager.INSTANCE, this);
		getServer().getPluginManager().registerEvents(BlockProtection.INSTANCE, this);

		// initialize the teams and team infos
		ConfigurationSection allteamconfigs = config.getConfigurationSection("teams");
		for (String teamid : allteamconfigs.getKeys(false)) {
			ConfigurationSection teamconfig = allteamconfigs.getConfigurationSection(teamid);
			TeamInfo info = new TeamInfo(teamconfig, this);
			teaminfos.put(teamid, info);

			Team newteam = sb.registerNewTeam(teamid);
			newteam.setPrefix(info.chatcolor + "[" + info.name + "] ");
			newteam.setCanSeeFriendlyInvisibles(true);
			newteam.setAllowFriendlyFire(false);
		}

		// initialize resourcespawners
		// diamonds
		ConfigurationSection diamonds = config.getConfigurationSection("diamondgens");
		ItemStack is = new ItemStack(Material.DIAMOND);
		for (String genid : diamonds.getKeys(false)) {
			ResourceSpawner gen = new ResourceSpawner(this, is, getLocation(diamonds, genid));
			gen.setInterval(600);
			diamondgens.add(gen);
		}
		// emeralds
		ConfigurationSection emeralds = config.getConfigurationSection("emeraldgens");
		is = new ItemStack(Material.EMERALD);
		for (String genid : emeralds.getKeys(false)) {
			ResourceSpawner gen = new ResourceSpawner(this, is, getLocation(emeralds, genid));
			gen.setInterval(1200);
			emeraldgens.add(gen);
		}

		// set protected area
		BlockProtection.loadProtectedAreas(config, "protected");

		// initialize player health/kills display
		Objective health = sb.registerNewObjective("health", "health");
		health.setDisplaySlot(DisplaySlot.BELOW_NAME);
		health.setDisplayName(ChatColor.RED + "❤");
		Objective kills = sb.registerNewObjective("kills", "playerKillCount");
		kills.setDisplaySlot(DisplaySlot.PLAYER_LIST);

		// initialize scoreboard display
		Objective display = sb.registerNewObjective("display", "dummy");
		display.setDisplaySlot(DisplaySlot.SIDEBAR);
		display.setDisplayName("BED WARS");

		for (Player p : Bukkit.getOnlinePlayers()) {
			p.setScoreboard(sb);
		}

        // initialize buyConfig
        buyConfig = Buy_configKt.initBuyConfig(playerUpgrades, teaminfos);
		// configure commands
        getCommand("buy").setExecutor(getMasterCommand(buyConfig));
	}

	private void startGame(CommandSender sender) {
		if (gamephase != 1) return;

		// reset player upgrades from the previous round
		playerUpgrades.resetUpgrades();

		// load chunks
		Chunk low = playloclow.getChunk();
		Chunk high = playlochigh.getChunk();
		if (high.getX() - low.getX() > 100) Bukkit.broadcastMessage("chunk coords bad");
		for (int x=low.getX();x<=high.getX();x++) {
			for (int z=low.getZ();z<=high.getZ();z++) {
				Bukkit.getWorld("world").getChunkAt(x, z).load();
			}
		}

		// remove entities
		for (Entity e : Bukkit.getWorld("world").getEntities()) {
			if (e.getType() != EntityType.PLAYER) {
				e.remove();
			}
		}

		// clone region
		clone(structureloclow, structurelochigh, playloclow);
		clone(structureloclow, structurelochigh, playloclow);

		for (Team t : sb.getTeams()) {
			// update team info for new game
			Set<OfflinePlayer> players = t.getPlayers();
			TeamInfo info = getInfo(t);
			info.newGame(players.size());

			// teleport players
			for (OfflinePlayer op : players) {
				if (!op.isOnline()) continue;
				Player p = (Player) op;
				p.setDisplayName(info.chatcolor + p.getName() + ChatColor.RESET);
				p.teleport(info.spawn);
				p.setGameMode(GameMode.SURVIVAL);
				p.getEnderChest().clear();
				p.getInventory().clear();
				p.getInventory().addItem(new ItemStack(Material.WOOD_SWORD));
			}
		}

		// teleport non-participating players as spectators
		for (Player p : Bukkit.getOnlinePlayers()) {
			if (sb.getPlayerTeam(p) == null) {
				p.setGameMode(GameMode.SPECTATOR);
				p.teleport(spectatespawn);
			}
		}

		// start resource generators
		for (ResourceSpawner rs : diamondgens) {
			rs.start();
		}
		for (ResourceSpawner rs : emeraldgens) {
			rs.start();
		}

		Bukkit.broadcastMessage("Game started!");
		gamephase = 2;
	}

	private void endGame() {
		// clean up and refresh
		// stop generators
		for (ResourceSpawner rs : diamondgens) {
			rs.stop();
		}
		for (ResourceSpawner rs : emeraldgens) {
			rs.stop();
		}
		for (Team t : sb.getTeams()) {
			getInfo(t).stopGenerators();
			// reset teams
			for (OfflinePlayer p : t.getPlayers()) {
				t.removePlayer(p);
			}
		}

		for (Player p : Bukkit.getOnlinePlayers()) {
			sb.resetScores((OfflinePlayer) p);
			p.setDisplayName(p.getName());
			p.getInventory().clear();
		}

		// teleport everyone to lobby
		for (Player p : Bukkit.getOnlinePlayers()) {
			p.setGameMode(GameMode.ADVENTURE);
			p.teleport(lobby);
		}

		Bukkit.broadcastMessage("Game ended!");
		gamephase = 1;
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent e) {
		Player p = e.getPlayer();

		// if in lobby phase, remove player from any team
		if (gamephase == 1) {
			if (sb.getPlayerTeam(p) != null) {
				sb.getPlayerTeam(p).removePlayer(p);
			}

		// if in game phase, mark him as dead
		} else if (gamephase == 2) {
			getInfo(sb.getPlayerTeam(p)).playersalive--;
		}
		updateDisplay();
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent e) {
		final Player p = e.getPlayer();
		p.setScoreboard(sb);

		// if in lobby phase, tp to lobby
		if (gamephase == 1) {
			p.setGameMode(GameMode.ADVENTURE);
			p.teleport(lobby);

		// in game
		} else if (gamephase == 2) {
			// make him spectate first
			p.teleport(spectatespawn);
			p.setGameMode(GameMode.SPECTATOR);
			Team t = sb.getPlayerTeam(p);
			final TeamInfo info = getInfo(t);

			// if he was in a team and team still has bed then bring him back
			if (t != null && info.hasbed) {
				info.playersalive++;
				new BukkitRunnable() {
					@Override
					public void run() {
						p.teleport(info.spawn);
						p.setGameMode(GameMode.SURVIVAL);
					}
				}.runTaskLater(this, 100);
			}
		}
	}


	/* IN-GAME EVENT LISTENERS */

	@EventHandler
	public void onInventoryClick(InventoryClickEvent e) {
		if (gamephase != 2) return;
		switch (e.getSlotType()) {
			case ARMOR:
			case CRAFTING:
			case FUEL:
			case RESULT:
				e.setCancelled(true);
				break;
			default:
				break;
		}
	}

	@EventHandler
	public void onBlockBreak(BlockBreakEvent e) {
		if (gamephase != 2) return;
		Material blocktype = e.getBlock().getType();
		Location loc = e.getBlock().getLocation();

		// bed destroyed logic
		if (blocktype == Material.BED_BLOCK) {
			for (Team t : sb.getTeams()) {
				TeamInfo info = getInfo(t);
				if (loc.distance(info.bed) < 3) {
					info.hasbed = false;
					loc.getWorld().playSound(loc, Sound.ENDERDRAGON_GROWL, 100, 1);
					for (OfflinePlayer op : t.getPlayers()) {
						if (op.isOnline()) {
							((Player) op).sendTitle(ChatColor.DARK_RED + "BED DESTROYED",
																			"You will no longer respawn!");
						}
					}
					TeamInfo destroyerinfo = getInfo(sb.getPlayerTeam(e.getPlayer()));
					getServer().broadcastMessage(info.chatcolor + info.name + " bed" + ChatColor.RESET +
																			 " was destroyed by " + destroyerinfo.chatcolor +
																			 e.getPlayer().getName() + ChatColor.RESET + "!");
				}
			}
			updateDisplay();

		// only allow breaking player-placed blocks
		} else {
			if (loc.getWorld().getBlockAt(loc.subtract(playloclow).add(structureloclow))
					.getType() != Material.AIR) {
				e.setCancelled(true);
			}
		}
	}

	@EventHandler
	public void onBlockPlace(BlockPlaceEvent e) {
		if (gamephase != 2) return;
		if (!isBetween(playloclow, playlochigh, e.getBlockPlaced().getLocation())) {
			e.setCancelled(true);
		} else {
			if (e.getBlockPlaced().getType() == Material.TNT) {
				e.setCancelled(true);
				Location loc = e.getBlockPlaced().getLocation();
				loc.getWorld().spawnEntity(loc.add(0.5,0.5,0.5), EntityType.PRIMED_TNT);
				e.getPlayer().getInventory().removeItem(new ItemStack(Material.TNT));
			}
		}
	}

	@EventHandler
	public void onPlayerInteract(PlayerInteractEvent e) {
		if (gamephase != 2) return;
		if (e.hasItem() && e.getMaterial() == Material.FIREBALL) {
			e.setCancelled(true);
			Player p = e.getPlayer();
			p.getInventory().removeItem(new ItemStack(Material.FIREBALL));
			Vector direction = p.getLocation().getDirection();
			Entity fireball = Bukkit.getWorld("world").spawnEntity(p.getLocation().add(0,1.62,0).add(direction), EntityType.FIREBALL);
			((Fireball)fireball).setShooter(p);
			fireball.setVelocity(direction);
		}
	}

	@EventHandler
	public void onEntityExplode(EntityExplodeEvent e) {
		List<Block> removedblocks = e.blockList();
		removedblocks.removeIf(b -> b.getLocation().getWorld().getBlockAt(
			  												b.getLocation().subtract(playloclow).add(structureloclow))
																.getType() != Material.AIR);
	}

	@EventHandler
	public void onItemSpawn(ItemSpawnEvent e) {
		// don't drop the bed
		if (e.getEntity().getItemStack().getType() == Material.BED) {
			e.setCancelled(true);
		}
	}

	@EventHandler
	public void onPlayerMove(PlayerMoveEvent e) {
		if (gamephase == 2 &&
			  e.getPlayer().getGameMode() == GameMode.SURVIVAL &&
			  e.getTo().getY() < 0) {
			e.getPlayer().setHealth(0.0);
			e.setCancelled(true);
		}
	}

	@EventHandler
	public void onPlayerDeath(PlayerDeathEvent e) {
		if (gamephase != 2) return;
		final Player p = e.getEntity();
		final TeamInfo info = getInfo(sb.getPlayerTeam(p));

		// add color to death message and play death ding
		String msg = e.getDeathMessage();
		msg = msg.replace(p.getName(), p.getDisplayName() + ChatColor.RESET);
		Player killer = p.getKiller();
		if (killer != null) {
			msg = msg.replace(killer.getName(), killer.getDisplayName() + ChatColor.RESET);
			killer.playSound(killer.getLocation(), Sound.ORB_PICKUP, 100, 1);
		}
		p.playSound(p.getLocation(), Sound.ORB_PICKUP, 100, 1);
		if (!info.hasbed) {
			msg += "." + ChatColor.AQUA + " FINAL KILL!";
		}
		e.setDeathMessage(msg);

		// don't drop loot
		List<ItemStack> drops = e.getDrops();
		if (killer != null) {
			drops.removeIf(is -> is.getType() != Material.IRON_INGOT &&
													 is.getType() != Material.GOLD_INGOT &&
													 is.getType() != Material.DIAMOND &&
													 is.getType() != Material.EMERALD);
			killer.getInventory().addItem(drops.toArray(new ItemStack[drops.size()]));
		}
		drops.clear();


		// bypass respawn screen
        p.spigot().respawn();

		// if team hasbed, put him back to warzone after 5 sec
		if (info.hasbed) {
			new BukkitRunnable() {
				@Override
				public void run() {
					p.teleport(info.spawn);
					p.setGameMode(GameMode.SURVIVAL);

                    // hijacking respawn logic here to add tools and armor!
                    // TODO improve this
                    PlayerUpgrade upgrade = playerUpgrades.getUpgrade(p).downgradeTools();
                    playerUpgrades.setUpgrade(p, upgrade);


					p.getInventory().addItem(Game_itemsKt.getWoodSword().render());

					ItemStack[] armor = upgrade.getArmorSet(info.color);
                    ItemStack pick = upgrade.getPick();
                    ItemStack axe = upgrade.getAxe();
                    ItemStack shears = upgrade.getShears();
                    p.getInventory().setArmorContents(armor);
                    if (pick != null) {
                        p.getInventory().addItem(pick);
                    }
                    if (axe != null) {
                        p.getInventory().addItem(axe);
                    }
                    if (shears != null) {
                        p.getInventory().addItem(shears);
                    }
				}
			}.runTaskLater(this, 100);

		// else no bed!
		} else {
			// final kill!
			info.playersalive--;
			updateDisplay();

			// test for game end condition
			int numaliveteams = 0;
			Team aliveteam = null;
			for (Team temp : sb.getTeams()) {
				if (getInfo(temp).playersalive > 0) {
					aliveteam = temp;
					numaliveteams++;
				}
			}
			if (numaliveteams <= 1) {

				// game over!
				if (numaliveteams == 0) {
					getServer().broadcastMessage(ChatColor.YELLOW + "\n            GAME OVER!\n\n");
				} else if (numaliveteams == 1) {
					getServer().broadcastMessage(ChatColor.YELLOW + "\n            GAME OVER!\n            " +
																			 getInfo(aliveteam).chatcolor + getInfo(aliveteam).name +
																			 ChatColor.YELLOW + " team wins!!!\n\n");
				}

				// clean up game 10 seconds later
				new BukkitRunnable() {
					@Override
					public void run() {
						endGame();
					}
				}.runTaskLater(this, 200);
			}

		}

	}

	@EventHandler
	public void onPlayerRespawn(PlayerRespawnEvent e) {
		if (gamephase == 1) {
			e.setRespawnLocation(lobby);
			e.getPlayer().setGameMode(GameMode.ADVENTURE);
		} else if (gamephase == 2) {
			Player p = e.getPlayer();
			Team t = sb.getPlayerTeam(p);
			assert t != null : " A non-player respawned???";
			TeamInfo info = getInfo(t);

			// respawn as spectator first at least for 5 sec
			e.setRespawnLocation(spectatespawn);
			p.setGameMode(GameMode.SPECTATOR);
		}
	}

	/* HELPER FUNCTIONS */

	private TeamInfo getInfo(Team t) {
		return teaminfos.get(t.getName());
	}

	private void updateDisplay() {
		Objective display = sb.getObjective("display");
		for (OfflinePlayer op : sb.getPlayers()) {
			if (!(op.isOnline())) {
				sb.resetScores(op);
			}
		}
		display.getScore(".").setScore(1);
		int i = 2;
		for (Team t : sb.getTeams()) {
			TeamInfo info = getInfo(t);
			String base = info.chatcolor + info.name + ChatColor.RESET + ": ";
			if (gamephase == 1) {
				display.getScore(base + String.valueOf(t.getPlayers().size())).setScore(i++);
			} else if (gamephase == 2) {
				if (info.hasbed) {
					display.getScore(base + "✓").setScore(i++);
				} else {
					if (info.playersalive == 0) {
						display.getScore(base + "✗").setScore(i++);
					} else {
						display.getScore(base + String.valueOf(info.playersalive)).setScore(i++);
					}
				}
			}
		}
		display.getScore(". ").setScore(i);
	}

	private void initializeLocations() {
		playloclow = getLocation(config, "playregion.low");
		playlochigh = getLocation(config, "playregion.high");
		structureloclow = getLocation(config, "structureregion.low");
		structurelochigh = getLocation(config, "structureregion.high");
		spectatespawn = getLocation(config, "spectatespawn");
		lobby = getLocation(config, "lobby");
	}

	private void clone(Location loc1, Location loc2, Location loc3) {
		int xmin = loc1.getBlockX();
		int ymin = loc1.getBlockY();
		int zmin = loc1.getBlockZ();
		int xmax = loc2.getBlockX();
		int ymax = loc2.getBlockY();
		int zmax = loc2.getBlockZ();
		int xdest = loc3.getBlockX();
		int ydest = loc3.getBlockY();
		int zdest = loc3.getBlockZ();

		for (int x=xmin;x<=xmax;x++) {
			for (int y=ymin;y<=ymax;y++) {
				for (int z=zmin;z<=zmax;z++) {
					Block fromblock = Bukkit.getWorld("world").getBlockAt(x,y,z);
					Block toblock = Bukkit.getWorld("world").getBlockAt(x-xmin+xdest, y-ymin+ydest, z-zmin+zdest);

					toblock.setBiome(fromblock.getBiome());
					toblock.setType(fromblock.getType());

					BlockState fromstate = fromblock.getState();
					BlockState tostate = toblock.getState();

					tostate.setData(fromstate.getData());
					tostate.update();

					if (tostate instanceof Chest) {
						((Chest) tostate).getInventory().clear();
					}
				}
			}
		}
	}

	private boolean isBetween(Location loc1, Location loc2, Location test) {
		return loc1.getX() <= test.getX() && test.getX() <= loc2.getX() &&
					 loc1.getY() <= test.getY() && test.getY() <= loc2.getY() &&
					 loc1.getZ() <= test.getZ() && test.getZ() <= loc2.getZ();
	}

	static Location getLocation(ConfigurationSection config, String path) {
		if (!config.contains(path)) return null;
		World world = Bukkit.getWorld("world");
		float x = (float) config.getDouble(path + ".x");
		float y = (float) config.getDouble(path + ".y");
		float z = (float) config.getDouble(path + ".z");
		float pitch = (float) config.getDouble(path + ".pitch");
		float yaw = (float) config.getDouble(path + ".yaw");
		return new Location(world, x, y, z, yaw, pitch);
	}

	void saveLocation(ConfigurationSection config, String path, Location loc) {
		config.set(path + ".x", loc.getX());
		config.set(path + ".y", loc.getY());
		config.set(path + ".z", loc.getZ());
		config.set(path + ".pitch", loc.getPitch());
		config.set(path + ".yaw", loc.getYaw());
		saveConfig();
	}

}
