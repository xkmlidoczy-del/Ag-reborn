package cn.academy.port.develop;
import cn.academy.port.AcademyCraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.server.level.ServerPlayer;
import java.util.Optional;
public final class InductionFactors {
    private static final String KEY="academy:induction_category";
    private InductionFactors() {}
    public static ItemStack stack(String category) {
        var item=new ItemStack(AcademyCraft.INDUCTION_FACTOR.get());if(DevelopmentActions.CATEGORIES.contains(category))CustomData.update(DataComponents.CUSTOM_DATA,item,tag->tag.putString(KEY,category));return item;
    }
    public static Optional<String> category(ItemStack stack) {
        if(!stack.is(AcademyCraft.INDUCTION_FACTOR.get()))return Optional.empty();String category=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getString(KEY);return DevelopmentActions.CATEGORIES.contains(category)?Optional.of(category):Optional.empty();
    }
    public static DevelopmentActions.CategoryItems inventory(ServerPlayer player) {
        return new DevelopmentActions.CategoryItems() {
            private int slot(String current) {for(int i=0;i<player.getInventory().items.size();i++){var category=category(player.getInventory().items.get(i));if(category.isPresent()&&!category.get().equals(current))return i;}return -1;}
            public Optional<String> differentFactor(String current){int index=slot(current);return index<0?Optional.empty():category(player.getInventory().items.get(index));}
            public boolean hasHeldMagneticCoil(){return player.getMainHandItem().is(AcademyCraft.MAGNETIC_COIL.get());}
            public void consumeFactor(String category){for(int i=0;i<player.getInventory().items.size();i++)if(category(player.getInventory().items.get(i)).filter(category::equals).isPresent()){player.getInventory().items.set(i,ItemStack.EMPTY);player.getInventory().setChanged();return;}}
            public void consumeHeldMagneticCoil(){player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.getInventory().setChanged();}
        };
    }
}
