/* AcademyCraft 1.0.7 ItemApp adapter. GPLv3. See NOTICE. */
package cn.academy.port.terminal;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
public final class TerminalAppItem extends Item {
    public final String app;
    public TerminalAppItem(Properties properties,String app){super(properties);if(!TerminalState.knownApp(app)||TerminalState.PREINSTALLED.contains(app))throw new IllegalArgumentException("No source installer for "+app);this.app=app;}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        var stack=player.getItemInHand(hand);
        if(!level.isClientSide&&player instanceof ServerPlayer server&&TerminalNetwork.valid(server)){
            var state=TerminalStorage.get(server);var name=Component.translatable("ac.app."+app+".name");
            if(!state.terminalInstalled())server.sendSystemMessage(Component.translatable("ac.terminal.notinstalled"));
            else if(state.isInstalled(app))server.sendSystemMessage(Component.translatable("ac.terminal.app_alrdy_installed",name));
            else if(!stack.isEmpty()&&stack.getItem()==this){
                if(!player.getAbilities().instabuild){stack.shrink(1);player.getInventory().setChanged();}
                state.installApp(app);TerminalStorage.save(server);TerminalNetwork.sync(server);
                NeoForge.EVENT_BUS.post(new AppInstalledEvent(server,app));TerminalNetwork.installed(server,app);
                server.sendSystemMessage(Component.translatable("ac.terminal.app_installed",name));
            }
        }
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){lines.add(Component.translatable("ac.app."+app+".name"));}
}
