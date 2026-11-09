package net.minecraft.item;
public final class ItemStack { private final Item item;private final int meta;public ItemStack(Item item){this(item,0);}public ItemStack(Item item,int meta){this.item=item;this.meta=meta;}public Item getItem(){return item;}public int getItemDamage(){return meta;} }
