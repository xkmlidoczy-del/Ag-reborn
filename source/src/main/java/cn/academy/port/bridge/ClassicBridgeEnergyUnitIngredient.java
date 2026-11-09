/* AcademyCraft1.0.7 direct RFSupport recipe metadata0; genuine modern empty-unit adaptation. GPLv3. */
package cn.academy.port.bridge;
import cn.academy.port.energy.*;
import com.mojang.serialization.MapCodec;
import java.util.stream.Stream;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.*;
/** Stateless native predicate: source-equivalent gauge0 charge, plus the established genuinely empty native unit. */
public record ClassicBridgeEnergyUnitIngredient() implements ICustomIngredient {
    public static final MapCodec<ClassicBridgeEnergyUnitIngredient> CODEC=MapCodec.unit(new ClassicBridgeEnergyUnitIngredient());
    public static boolean acceptsEnergy(double energy){return Double.isFinite(energy)&&energy>=0&&energy<=ClassicEnergy.ENERGY_UNIT_CAPACITY&&(energy==0||ClassicEnergy.damage(energy,ClassicEnergy.ENERGY_UNIT_CAPACITY)==0);}
    @Override public boolean test(ItemStack stack){return stack!=null&&!stack.isEmpty()&&stack.is(ClassicEnergyItems.ENERGY_UNIT.get())&&acceptsEnergy(ClassicEnergyItemHelper.getEnergy(stack));}
    @Override public Stream<ItemStack> getItems(){var empty=new ItemStack(ClassicEnergyItems.ENERGY_UNIT.get());var full=empty.copy();ClassicEnergyItemHelper.setEnergy(full,ClassicEnergy.ENERGY_UNIT_CAPACITY);return Stream.of(empty,full);}
    @Override public boolean isSimple(){return false;}
    @Override public IngredientType<?> getType(){return ClassicEnergyBridges.ENERGY_UNIT_INGREDIENT.get();}
}
