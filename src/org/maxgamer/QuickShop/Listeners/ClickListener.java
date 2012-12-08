package org.maxgamer.QuickShop.Listeners;

import java.util.HashMap;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BlockIterator;
import org.maxgamer.QuickShop.MsgUtil;
import org.maxgamer.QuickShop.QuickShop;
import org.maxgamer.QuickShop.Util;
import org.maxgamer.QuickShop.Shop.Info;
import org.maxgamer.QuickShop.Shop.Shop;
import org.maxgamer.QuickShop.Shop.ShopAction;


/**
 * @author Netherfoam
 */
public class ClickListener implements Listener{
	QuickShop plugin;
	
	public ClickListener(QuickShop plugin){
		this.plugin = plugin;
	}
	
	/**
	 * Handles players left clicking a chest.
	 * Left click a NORMAL chest with item	: Send creation menu
	 * Left click a SHOP   chest			: Send purchase menu
	 */
	@EventHandler
	public void onClick(PlayerInteractEvent e){
		if(e.isCancelled()) return;
		if(e.getClickedBlock() == null) return; //Interacted with air
		if(e.getClickedBlock().getType() != Material.CHEST && e.getClickedBlock().getType() != Material.WALL_SIGN) return;
		
		if(plugin.lock && e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock().getType() == Material.CHEST){
			Shop shop = plugin.getShopManager().getShop(e.getClickedBlock().getLocation());
			
			//Make sure they're not using the non-shop half of a double chest.
			if(shop == null){
				Block b = Util.getSecondHalf(e.getClickedBlock());
				if(b != null){
					shop = plugin.getShopManager().getShop(b.getLocation());
				}
			}
			
			if(shop != null && !shop.getOwner().equalsIgnoreCase(e.getPlayer().getName())){
				if(e.getPlayer().hasPermission("quickshop.other.open")){
					e.getPlayer().sendMessage(MsgUtil.getMessage("bypassing-lock"));
					return;
				}
				e.getPlayer().sendMessage(MsgUtil.getMessage("that-is-locked"));
				e.setCancelled(true);
				return;
			}
		}
		else if(e.getAction() != Action.LEFT_CLICK_BLOCK){
			return;
		}
		
		Player p = e.getPlayer();
		
		if(plugin.sneak && !p.isSneaking()){
			//Sneak only
			return;
		}
		
		Block b = e.getClickedBlock();
		Location loc = b.getLocation();
		ItemStack item = e.getItem();
		
		//Get the shop
		Shop shop = plugin.getShopManager().getShop(loc);
		//If that wasn't a shop, search nearby shops
		if(shop == null && b.getType() == Material.WALL_SIGN){
			shop = plugin.getShopManager().getShop(Util.getAttached(b).getLocation());
		}

		//Purchase handling
		if(shop != null && p.hasPermission("quickshop.use")){
			//Text menu
			MsgUtil.sendShopInfo(p, shop);
			if(shop.isSelling()){
				p.sendMessage(MsgUtil.getMessage("how-many-buy"));
			}
			else{
				int items = Util.countItems(p.getInventory(), shop.getItem());
				
				p.sendMessage(MsgUtil.getMessage("how-many-sell", ""+items));
			}
			
			//Add the new action
			HashMap<String, Info> actions = plugin.getActions();
			Info info = new Info(shop.getLocation(), ShopAction.BUY, null, null, shop);
			actions.put(p.getName(), info);
			
			return;
		}
		//Handles creating shops
		else if(shop == null && item != null && item.getType() != Material.AIR && p.hasPermission("quickshop.create.sell") && b.getType() == Material.CHEST){
			if(!plugin.canBuildShop(p, b, e.getBlockFace())){
				//As of the new checking system, most plugins will tell the player why they can't create a shop there.
				//So telling them a message would cause spam etc.
				return;
			}
			if(Util.getSecondHalf(b) != null  && !p.hasPermission("quickshop.create.double")){
				p.sendMessage(MsgUtil.getMessage("no-double-chests"));
				return;
			}
			
			if(Util.isBlacklisted(item.getType()) && !p.hasPermission("quickshop.bypass."+item.getTypeId())){
				p.sendMessage(MsgUtil.getMessage("blacklisted-item"));
				return;
			}
			
			//Finds out where the sign should be placed for the shop
			Block last = null;
			Location from = p.getLocation().clone();
			from.setY(b.getY());
			from.setPitch(0);
			BlockIterator bIt = new BlockIterator(from, 0, 7);
			while(bIt.hasNext()){
				Block n = bIt.next();
				if(n.equals(b)) break;
				last = n;
			}
			
			//Send creation menu.
			Info info = new Info(b.getLocation(), ShopAction.CREATE, e.getItem(), last);
			plugin.getActions().put(p.getName(), info);
			p.sendMessage(MsgUtil.getMessage("how-much-to-trade-for", Util.getDataName(info.getItem().getType(), info.getItem().getDurability())));
		}
	}
}