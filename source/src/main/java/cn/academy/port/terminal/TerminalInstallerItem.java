/* AcademyCraft 1.0.7 ItemTerminalInstaller adapter. GPLv3. See NOTICE. */
package cn.academy.port.terminal;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
public final class TerminalInstallerItem extends Item {
    public TerminalInstallerItem(Properties properties){super(properties.stacksTo(1));}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        var stack=player.getItemInHand(hand);
        if(!level.isClientSide&&player instanceof ServerPlayer server&&TerminalNetwork.valid(server)){
            var state=TerminalStorage.get(server);
            if(state.terminalInstalled())server.sendSystemMessage(Component.translatable("ac.terminal.alrdy_installed"));
            else if(!stack.isEmpty()&&stack.getItem()==this){
                if(!player.getAbilities().instabuild){stack.shrink(1);player.getInventory().setChanged();}
                state.installTerminal();TerminalStorage.save(server);TerminalNetwork.sync(server);
                NeoForge.EVENT_BUS.post(new TerminalInstalledEvent(server));TerminalNetwork.installed(server,null);TerminalNetwork.installEffect(server);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
}
