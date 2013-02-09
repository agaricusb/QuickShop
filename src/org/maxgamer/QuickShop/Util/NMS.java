package org.maxgamer.QuickShop.Util;

import java.util.ArrayList;

import org.bukkit.ChatColor;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class NMS{
	/**
	 * Sets the given items stack size to 0,
	 * as well as gives it a custom NBT tag
	 * called "quickshop" in the root, to
	 * prevent it from merging with other
	 * items.  This is all through NMS code.
	 * @param item The given item
	 * @throws ClassNotFoundException 
	 */
	public static void safeGuard(Item item) throws ClassNotFoundException{
		rename(item.getItemStack());
		ItemStack iStack = item.getItemStack();

		//Fetch the NMS item
		net.minecraft.server.v1_4_R1.ItemStack nmsI = org.bukkit.craftbukkit.v1_4_R1.inventory.CraftItemStack.asNMSCopy(iStack);
		//Force the count to 0, don't notify anything though.
		nmsI.count = 0;
		//Get the itemstack back as a bukkit stack
		iStack = org.bukkit.craftbukkit.v1_4_R1.inventory.CraftItemStack.asBukkitCopy(nmsI);

		//Set the display item to the stack.
		item.setItemStack(iStack);
	}
	
	/** 
	 * Renames the given itemstack to ChatColor.RED + "QuickShop " + Util.getName(iStack).
	 * This prevents items stacking (Unless, ofcourse, the other item has a jerky name too - Rare)
	 * @param iStack the itemstack to rename.
	 */
	private static void rename(ItemStack iStack){
		//This stops it merging with other items. * Unless they're named funnily... In which case, shit.
		ItemMeta meta = iStack.getItemMeta();
		meta.setDisplayName(ChatColor.RED + "QuickShop " + Util.getName(iStack));
		iStack.setItemMeta(meta);
	}
	
	/**
	 * Returns a byte array of the given ItemStack.
	 * @param iStack The given itemstack
	 * @return The compressed NBT tag version of the given stack.
	 * Will return null if used with an invalid Bukkit version.
	 * @throws ClassNotFoundException if QS is not updated to this build of bukkit.
	 */
	public static byte[] getNBTBytes(ItemStack iStack) throws ClassNotFoundException{
		net.minecraft.server.v1_4_R1.ItemStack is = org.bukkit.craftbukkit.v1_4_R1.inventory.CraftItemStack.asNMSCopy(iStack);
		//Save the NMS itemstack to a new NBT tag
		net.minecraft.server.v1_4_R1.NBTTagCompound itemCompound = new net.minecraft.server.v1_4_R1.NBTTagCompound();
		itemCompound = is.save(itemCompound);

		//Convert the NBT tag to a byte[]
		return net.minecraft.server.v1_4_R1.NBTCompressedStreamTools.a(itemCompound);
	}
	
	/**
	 * Converts the given bytes into an itemstack
	 * @param bytes The bytes to convert to an itemstack
	 * @return The itemstack
	 * @throws ClassNotFoundException if QS is not updated to this build of bukkit.
	 */
	public static ItemStack getItemStack(byte[] bytes) throws ClassNotFoundException{
		net.minecraft.server.v1_4_R1.NBTTagCompound c = net.minecraft.server.v1_4_R1.NBTCompressedStreamTools.a(bytes);
		net.minecraft.server.v1_4_R1.ItemStack is = net.minecraft.server.v1_4_R1.ItemStack.createStack(c);
		return org.bukkit.craftbukkit.v1_4_R1.inventory.CraftItemStack.asBukkitCopy(is);
	}
}