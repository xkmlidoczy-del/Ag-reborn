/* AcademyCraft1.0.7 client CGuiScreen adapted to a sender-bound native control menu. GPLv3. */
package cn.academy.port.interferer;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
public final class ClassicAbilityInterfererMenu extends AbstractContainerMenu {
    private final BlockPos pos;private final UUID token;private final ClassicAbilityInterfererBlockEntity tile;
    private long sequence;private ClassicInterfererNetwork.Snapshot snapshot;
    public ClassicAbilityInterfererMenu(int id,Inventory inventory,FriendlyByteBuf wire){this(id,wire.readBlockPos(),wire.readUUID(),null);}
    public ClassicAbilityInterfererMenu(int id,BlockPos pos,UUID token,ClassicAbilityInterfererBlockEntity tile){super(ClassicAbilityInterferers.MENU.get(),id);this.pos=pos.immutable();this.token=token;this.tile=tile;}
    public BlockPos sourcePos(){return pos;}public UUID token(){return token;}public long sequence(){return sequence;}
    public boolean isFor(ClassicAbilityInterfererBlockEntity candidate){return tile==candidate;}
    public boolean next(long value){if(value!=sequence+1||sequence==Long.MAX_VALUE)return false;sequence=value;return true;}
    public ClassicInterfererNetwork.Snapshot snapshot(){return snapshot;}
    public void acceptSnapshot(ClassicInterfererNetwork.Snapshot value){snapshot=value;}
    @Override public boolean stillValid(Player player){return tile==null?true:tile.available()&&player.level()==tile.getLevel()&&player.isAlive()&&!player.isRemoved()&&!player.isSpectator()&&player.level().mayInteract(player,pos)&&player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<64&&tile.mayConfigure(player);}
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
}
