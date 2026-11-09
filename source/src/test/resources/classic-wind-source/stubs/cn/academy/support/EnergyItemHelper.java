package cn.academy.support;
import net.minecraft.item.ItemStack;
public final class EnergyItemHelper { public interface EnergyItemManager { double getEnergy(ItemStack stack); void setEnergy(ItemStack stack, double amount); double charge(ItemStack stack, double amount, boolean ignoreBandwidth); boolean isSupported(ItemStack stack); double pull(ItemStack stack, double amount, boolean ignoreBandwidth); } }

