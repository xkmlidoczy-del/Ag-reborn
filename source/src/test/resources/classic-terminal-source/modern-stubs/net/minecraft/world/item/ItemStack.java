package net.minecraft.world.item;
public class ItemStack {private final Item item;private int count;public ItemStack(Item i,int c){item=i;count=c;}public boolean isEmpty(){return count<=0;}public Item getItem(){return item;}public int getCount(){return count;}public void shrink(int n){count=Math.max(0,count-n);}}
