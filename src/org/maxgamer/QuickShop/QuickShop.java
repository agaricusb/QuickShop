package org.maxgamer.QuickShop;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionType;
import org.maxgamer.QuickShop.Database.Database;
import org.maxgamer.QuickShop.Listeners.BlockListener;
import org.maxgamer.QuickShop.Listeners.ChatListener;
import org.maxgamer.QuickShop.Listeners.ChunkListener;
import org.maxgamer.QuickShop.Listeners.ClickListener;
import org.maxgamer.QuickShop.Listeners.MoveListener;
import org.maxgamer.QuickShop.Shop.Info;
import org.maxgamer.QuickShop.Shop.Shop;
import org.maxgamer.QuickShop.Watcher.BufferWatcher;
import org.maxgamer.QuickShop.Watcher.ItemWatcher;


public class QuickShop extends JavaPlugin{
	private Economy economy;
	private HashMap<Location, Shop> shops = new HashMap<Location, Shop>(30);
	private HashMap<String, Info> actions = new HashMap<String, Info>(30);
	private HashSet<Material> tools = new HashSet<Material>(50);
	
	private HashMap<Shop, Item> spawnedItems = new HashMap<Shop, Item>(30);
	
	private Database database;
	public HashSet<String> queries = new HashSet<String>(5);
	public boolean queriesInUse = false;
	
	private ChatListener chatListener = new ChatListener(this);
	private ClickListener clickListener = new ClickListener(this);
	private BlockListener blockListener = new BlockListener(this);
	private MoveListener moveListener = new MoveListener(this);
	private ChunkListener chunkListener = new ChunkListener(this);
	
	public void onEnable(){
		getLogger().info("Hooking Vault");
		setupEconomy();
		getLogger().info("Registering Listeners");
		Bukkit.getServer().getPluginManager().registerEvents(chatListener, this);
		Bukkit.getServer().getPluginManager().registerEvents(clickListener, this);
		Bukkit.getServer().getPluginManager().registerEvents(blockListener, this);
		Bukkit.getServer().getPluginManager().registerEvents(moveListener, this);
		Bukkit.getServer().getPluginManager().registerEvents(chunkListener, this);
		
		/* Create plugin folder */
		if(!this.getDataFolder().exists()){
			this.getDataFolder().mkdir();
		}
		/* Create config file */
		File configFile = new File(this.getDataFolder(), "config.yml");
		this.getConfig().options().copyDefaults(true);
		if(!configFile.exists()){
			//Copy config with comments
			this.saveDefaultConfig();
		}
		
		/* Start database - Also creates DB file. */
		this.database = new Database(this, this.getDataFolder() + File.separator + "shops.db");
		
		getLogger().info("Loading tools");
		loadTools();
		
		getLogger().info("Starting item scheduler");
		
		/* Creates DB table 'shops' */
		if(!getDB().hasTable()){
			try {
				getDB().createTable();
			} catch (SQLException e) {
				e.printStackTrace();
				getLogger().severe("Could not create database table");
			}
		}
		/* Load shops from database to memory */
		Connection con = database.getConnection();
		try {
			PreparedStatement ps = con.prepareStatement("SELECT * FROM shops");
			ResultSet rs = ps.executeQuery();
			while(rs.next()){
				int x = rs.getInt("x");
				int y = rs.getInt("y");
				int z = rs.getInt("z");
				World world = Bukkit.getWorld(rs.getString("world"));
				
				ItemStack item = this.makeItem(rs.getString("itemString"));				
				
				String owner = rs.getString("owner");
				double price = rs.getDouble("price");
				Location loc = new Location(world, x, y, z);
				
				Shop shop = new Shop(loc, price, item, owner);
				
				this.getShops().put(loc, shop);
			}
			
		} catch (SQLException e) {
			e.printStackTrace();
			getLogger().severe("Could not load shops.");
		}
		/**
		 * Database query handler thread
		 */
		Bukkit.getScheduler().scheduleAsyncRepeatingTask(this, new BufferWatcher(), 300, 300);
		/**
		 * Display item handler thread
		 */
		Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new ItemWatcher(), 150, 150);
		
	}
	public void onDisable(){
		/* Remove all display items, and any dupes we can find */
		for(Shop shop : shops.values()){
			shop.getDisplayItem().removeDupe();
			shop.getDisplayItem().remove();
		}
		
		/* Empty the buffer */
		new BufferWatcher().run();
		
		this.actions.clear();
		this.shops.clear();
		this.tools.clear();
	}
	/**
	 * Returns the vault economy
	 * @return The vault economy
	 */
	public Economy getEcon(){
		return economy;
	}
	/**
	 * Returns a hashmap of (key: location) and (value: shop)'s.
	 * @return A hashmap of (key: location) and (value: shop)'s.
	 */
	public HashMap<Location, Shop> getShops(){
		return this.shops;
	}
	/**
	 * @return Returns the HashMap<Player name, shopInfo>. Info contains what their last question etc was.
	 */
	public HashMap<String, Info> getActions(){
		return this.actions;
	}
	
	/**
	 * Sets up the vault economy for hooking into & purchases.
	 * @return True is success
	 */
	private boolean setupEconomy(){
	        RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
	        if (economyProvider != null) {
	            economy = economyProvider.getProvider();
	        }

	        return (economy != null);
    }
	
	 /**
	  * Fetches a shop in a particular location, or null.
	  * @param loc The location to check.
	  * @return The shop at the location.
	  */
	public Shop getShop(Location loc){
		Shop shop = this.getShops().get(loc);
		if(shop != null) return shop;
		return null;
		
	}
	/**
	 * @param mat The material to check
	 * @return Returns true if the item is a tool (Has durability) or false if it doesn't.
	 */
	public boolean isTool(Material mat){
		return this.tools.contains(mat);
	}
	/**
	 * @return Returns the database handler for queries etc.
	 */
	public Database getDB(){
		return this.database;
	}
	 
	 /**
	  * Gets the percentage (Without trailing %) damage on a tool.
	  * @param item The ItemStack of tools to check
	  * @return The percentage 'health' the tool has. (Opposite of total damage)
	  */
	public String getToolPercentage(ItemStack item){
		double dura = item.getDurability();
		double max = item.getType().getMaxDurability();
		
		DecimalFormat formatter = new DecimalFormat("0");
		return formatter.format((1 - dura/max)* 100.0);
	}
	
	public boolean isProtectedItem(Item item){
		return this.spawnedItems.containsValue(item);
	}
	
	/**
	 * @return Returns a hashmap<shop, item> of the protected items.
	 */
	public HashMap<Shop, Item> getProtectedItems(){
		return this.spawnedItems;
	}
	/**
	 * Converts a string into an item from the database.
	 * @param itemString The database string.  Is the result of makeString(ItemStack item).
	 * @return A new itemstack, with the properties given in the string
	 */
	public ItemStack makeItem(String itemString){
		String[] itemInfo = itemString.split(":");
		
		ItemStack item = new ItemStack(Material.getMaterial(itemInfo[0]));
		MaterialData data = new MaterialData(Integer.parseInt(itemInfo[1]));
		item.setData(data);
		item.setDurability( Short.parseShort(itemInfo[2]));
		item.setAmount(Integer.parseInt(itemInfo[3]));
		
		for(int i = 4; i < itemInfo.length; i = i + 2){
			item.addEnchantment(Enchantment.getByName(itemInfo[i]), Integer.parseInt(itemInfo[i+1]));
		}
		return item;
	}
	
	/**
	 * Converts an itemstack into a string for database storage.  See makeItem(String itemString) for 
	 * reversing this.
	 * @param item The item to model it off of.
	 * @return A new string with the properties of the item.
	 */
	public String makeString(ItemStack item){
		String itemString = item.getType().toString() + ":" + item.getData().getData() + ":" + item.getDurability() + ":" + item.getAmount();
		
		for(Entry<Enchantment, Integer> ench : item.getEnchantments().entrySet()){
			itemString += ":" + ench.getKey().getName() + ":" + ench.getValue();
		}
		return itemString;
	}
	
	/**
	 * Stores all the tools in a hashset to easily check if an item is a tool.
	 * Data on tools is converted to durability %.
	 */
	public void loadTools(){
		tools.add(Material.BOW);
		tools.add(Material.SHEARS);
		tools.add(Material.FISHING_ROD);
		tools.add(Material.FLINT_AND_STEEL);

		tools.add(Material.CHAINMAIL_BOOTS);
		tools.add(Material.CHAINMAIL_CHESTPLATE);
		tools.add(Material.CHAINMAIL_HELMET);
		tools.add(Material.CHAINMAIL_LEGGINGS);
		
		tools.add(Material.WOOD_AXE);
		tools.add(Material.WOOD_HOE);
		tools.add(Material.WOOD_PICKAXE);
		tools.add(Material.WOOD_SPADE);
		tools.add(Material.WOOD_SWORD);
		
		tools.add(Material.LEATHER_BOOTS);
		tools.add(Material.LEATHER_CHESTPLATE);
		tools.add(Material.LEATHER_HELMET);
		tools.add(Material.LEATHER_LEGGINGS);
		
		tools.add(Material.DIAMOND_AXE); 
		tools.add(Material.DIAMOND_HOE);
		tools.add(Material.DIAMOND_PICKAXE);
		tools.add(Material.DIAMOND_SPADE);
		tools.add(Material.DIAMOND_SWORD);

		tools.add(Material.DIAMOND_BOOTS);
		tools.add(Material.DIAMOND_CHESTPLATE);
		tools.add(Material.DIAMOND_HELMET);
		tools.add(Material.DIAMOND_LEGGINGS);
		tools.add(Material.STONE_AXE); 
		tools.add(Material.STONE_HOE);
		tools.add(Material.STONE_PICKAXE);
		tools.add(Material.STONE_SPADE);
		tools.add(Material.STONE_SWORD);

		tools.add(Material.GOLD_AXE); 
		tools.add(Material.GOLD_HOE);
		tools.add(Material.GOLD_PICKAXE);
		tools.add(Material.GOLD_SPADE);
		tools.add(Material.GOLD_SWORD);

		tools.add(Material.GOLD_BOOTS);
		tools.add(Material.GOLD_CHESTPLATE);
		tools.add(Material.GOLD_HELMET);
		tools.add(Material.GOLD_LEGGINGS);
		tools.add(Material.IRON_AXE); 
		tools.add(Material.IRON_HOE);
		tools.add(Material.IRON_PICKAXE);
		tools.add(Material.IRON_SPADE);
		tools.add(Material.IRON_SWORD);

		tools.add(Material.IRON_BOOTS);
		tools.add(Material.IRON_CHESTPLATE);
		tools.add(Material.IRON_HELMET);
		tools.add(Material.IRON_LEGGINGS);
	}
	
	/**
	 * Gets the chest (or null) directly next to a block. Does not check vertical or diagonal.
	 * @param b The block to check next to.
	 * @return The chest.
	 */
	public Block getChestNextTo(Block b){
		Block[] c = new Block[4];
		c[0] = (b.getLocation().add(1, 0, 0).getBlock());
		c[1] = (b.getLocation().add(-1, 0, 0).getBlock());
		c[2] = (b.getLocation().add(0, 0, 1).getBlock());
		c[3] = (b.getLocation().add(0, 0, -1).getBlock());

		for(Block d : c){
			if(d.getType() == Material.CHEST){
				return d;
			}
		}
		return null;
	}
	
	/**
	 * Converts a given material and data value into a format similar to Material.<?>.toString().
	 * Upper case, with underscores.  Includes material name in result.
	 * @param mat The base material.
	 * @param damage The durability/damage of the item.
	 * @return A string with the name of the item.
	 */
	public String getDataName(Material mat, short damage){
		int id = mat.getId();
		switch(id){
		case 35: 
			switch((int) damage){
			case 0: return "WHITE_WOOL";
			case 1: return "ORANGE_WOOL";
			case 2: return "MAGENTA_WOOL";
			case 3: return "LIGHT_BLUE_WOOL";
			case 4: return "YELLOW_WOOL";
			case 5: return "LIME_WOOL";
			case 6: return "PINK_WOOL";
			case 7: return "GRAY_WOOL";
			case 8: return "LIGHT_GRAY_WOOL";
			case 9: return "CYAN_WOOL";
			case 10: return "PURPLE_WOOL";
			case 11: return "BLUE_WOOL";
			case 12: return "BROWN_WOOL";
			case 13: return "GREEN_WOOL";
			case 14: return "RED_WOOL";
			case 15: return "BLACK_WOOL";
			}
		case 351:
			switch((int) damage){
			case 0: return "INK_SAC";
			case 1: return "ROSE_RED";
			case 2: return "CACTUS_GREEN";
			case 3: return "COCOA_BEANS";
			case 4: return "LAPIS_LAZULI";
			case 5: return "PURPLE_DYE";
			case 6: return "CYAN_DYE";
			case 7: return "LIGHT_GRAY_DYE";
			case 8: return "GRAY_DYE";
			case 9: return "PINK_DYE";
			case 10: return "LIME_DYE";
			case 11: return "DANDELION_YELLOW";
			case 12: return "LIGHT_BLUE_DYE";
			case 13: return "MAGENTA_DYE";
			case 14: return "ORANGE_DYE";
			case 15: return "BONE_MEAL";
			}
		case 98:
			switch((int) damage){
			case 0: return "STONE_BRICKS";
			case 1: return "MOSSY_STONE_BRICKS";
			case 2: return "CRACKED_STONE_BRICKS";
			case 3: return "CHISELED_STONE_BRICKS";
			}
		case 373:
			//Special case
			if(damage == 64) return "MUNDANE_POTION";
			Potion pot = null;
			try{
				pot = Potion.fromDamage(damage);
			}
			catch(IllegalArgumentException ex){
				pot = new Potion(PotionType.WATER);
			}
			
			String prefix = "";
			String suffix = "";
			if(pot.getLevel() > 0) suffix += "_" + pot.getLevel();
			if(pot.hasExtendedDuration()) prefix += "EXTENDED_";
			if(pot.isSplash()) prefix += "SPLASH_";
			
			switch((int) pot.getNameId()){
			case 0: return prefix + "WATER_BOTTLE" + suffix;
			case 1: return prefix + "POTION_OF_REGENERATION" + suffix;
			case 2: return prefix + "POTION_OF_SWIFTNESS" + suffix;
			case 3: return prefix + "POTION_OF_FIRE_RESISTANCE" + suffix;
			case 4: return prefix + "POTION_OF_POISON" + suffix;
			case 5: return prefix + "POTION_OF_HEALING" + suffix;
			case 6: return prefix + "CLEAR_POTION" + suffix;
			case 7: return prefix + "CLEAR_POTION" + suffix;
			case 8: return prefix + "POTION_OF_WEAKNESS" + suffix;
			case 9: return prefix + "POTION_OF_STRENGTH" + suffix;
			case 10: return prefix + "POTION_OF_SLOWNESS" + suffix;
			case 11: return prefix + "DIFFUSE_POTION" + suffix;
			case 12: return prefix + "POTION_OF_HARMING" + suffix;
			case 13: return prefix + "ARTLESS_POTION" + suffix;
			case 14: return prefix + "THIN_POTION" + suffix;
			case 15: return prefix + "THIN_POTION" + suffix;
			case 16: return prefix + "AWKWARD_POTION" + suffix;
			case 32: return prefix + "THICK_POTION" + suffix;
			}
			getLogger().info("Potion ID: " + pot.getNameId());
		
		case 6:
			switch((int) damage){
			case 0: return "OAK_SAPLING";
			case 1: return "PINE_SAPLING";
			case 2: return "BIRCH_SAPLING";
			case 3: return "JUNGLE_TREE_SPALING";
			}
		
		case 5:
			switch((int) damage){
			case 0: return "OAK_PLANKS";
			case 1: return "PINE_PLANKS";
			case 2: return "BIRCH_PLANKS";
			case 3: return "JUNGLE_PLANKS";
			}
		case 17:
			switch(damage){
			case 0: return "OAK_LOG";
			case 1: return "PINE_LOG";
			case 2: return "BIRCH_LOG";
			case 3: return "JUNGLE_LOG";
			}
		case 18:
			switch(damage){
			case 0: return "OAK_LEAVES";
			case 1: return "PINE_LEAVES";
			case 2: return "BIRCH_LEAVES";
			case 3: return "JUNGLE_LEAVES";
			case 7: return "JUNGLE_LEAVES";
			}
		case 263:
			switch(damage){
			case 0: return "COAL";
			case 1: return "CHARCOAL";
			}
		case 24:
			switch((int) damage){
			case 0: return "SANDSTONE";
			case 1: return "CHISELED_SANDSTONE";
			case 2: return "SMOOTH_SANDSTONE";
			}
		case 31:
			switch((int) damage){
			case 0: return "DEAD_SHRUB";
			case 1: return "TALL_GRASS";
			case 2: return "FERN";
			}
		case 44:
			switch((int) damage){
			case 0: return "STONE_SLAB";
			case 1: return "SANDSTONE_SLAB";
			case 2: return "WOODEN_SLAB";
			case 3: return "COBBLESTONE_SLAB";
			case 4: return "BRICK_SLAB";
			case 5: return "STONE_BRICK_SLAB";
			}
		case 383:
			switch((int) damage){
			case 50: return "CREEPER_EGG";
			case 51: return "SKELETON_EGG";
			case 52: return "SPIDER_EGG";
			case 53: return "GIANT_EGG";
			case 54: return "ZOMBIE_EGG";
			case 55: return "SLIME_EGG";
			case 56: return "GHAST_EGG";
			case 57: return "ZOMBIE_PIGMAN_EGG";
			case 58: return "ENDERMAN_EGG";
			case 59: return "CAVE_SPIDER_EGG";
			case 60: return "SILVERFISH_EGG";
			case 61: return "BLAZE_EGG";
			case 62: return "MAGMA_CUBE_EGG";
			case 63: return "ENDER_DRAGON_EGG";
			case 90: return "PIG_EGG";
			case 91: return "SHEEP_EGG";
			case 92: return "COW_EGG";
			case 93: return "CHICKEN_EGG";
			case 94: return "SQUID_EGG";
			case 95: return "WOLF_EGG";
			case 96: return "MOOSHROOM_EGG";
			case 97: return "SNOW_GOLEM_EGG";
			case 98: return "OCELOT_EGG";
			case 99: return "IRON_GOLEM_EGG";
			case 120: return "VILLAGER_EGG";
			case 200: return "ENDER_CRYSTAL_EGG";
			case 14: return "PRIMED_TNT_EGG";
			}
		}
		if(damage == 0 || isTool(mat)) return mat.toString();
		else return mat.toString()+ ":" + damage;
	}
}