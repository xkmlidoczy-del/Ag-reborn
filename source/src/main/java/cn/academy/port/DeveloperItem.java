package cn.academy.port;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.network.PacketDistributor;
public final class DeveloperItem extends Item {
    public DeveloperItem(Properties properties) {super(properties);}
    @Override public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        if (cn.academy.port.energy.ClassicEnergyItemReequip.isEnergyOnlyUpdate(this, oldStack, newStack, slotChanged,
                cn.academy.port.develop.DeveloperItemEnergy.KEY, cn.academy.port.develop.DeveloperType.PORTABLE.energy, false)) return false;
        return slotChanged || super.shouldCauseReequipAnimation(oldStack, newStack, slotChanged);
    }
    private static double energy(ItemStack stack){return new cn.academy.port.develop.DeveloperItemEnergy(stack,cn.academy.port.develop.DeveloperType.PORTABLE).energy();}
    @Override public boolean isBarVisible(ItemStack stack){return cn.academy.port.develop.ClassicPortableDisplay.visible(energy(stack));}
    @Override public int getBarWidth(ItemStack stack){return cn.academy.port.develop.ClassicPortableDisplay.width(energy(stack));}
    @Override public int getBarColor(ItemStack stack){return net.minecraft.util.Mth.hsvToRgb(cn.academy.port.develop.ClassicPortableDisplay.hue(energy(stack)),1,1);}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,java.util.List<net.minecraft.network.chat.Component> tooltip,net.minecraft.world.item.TooltipFlag flag){
        tooltip.add(net.minecraft.network.chat.Component.literal(cn.academy.port.develop.ClassicPortableDisplay.tooltip(energy(stack))));
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        if(hand!=InteractionHand.MAIN_HAND)return InteractionResultHolder.fail(player.getItemInHand(hand));
        if(player instanceof ServerPlayer server) {cn.academy.port.machine.MachineDeveloperSessions.close(server);AcademyNetwork.sync(server);var t=new CompoundTag();t.putString("kind","developer");PacketDistributor.sendToPlayer(server,new AcademyNetwork.ClientData(t));}
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
    }
}
