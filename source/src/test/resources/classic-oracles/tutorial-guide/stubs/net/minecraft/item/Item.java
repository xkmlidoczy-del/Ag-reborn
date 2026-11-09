package net.minecraft.item;
public class Item { public final String id;public Item(){this("unnamed");}public Item(String id){this.id=id;cn.academy.misc.tutorial.OracleSupport.items.put(id,this);}public static Item getItemFromBlock(net.minecraft.block.Block block){return block.item;} }
