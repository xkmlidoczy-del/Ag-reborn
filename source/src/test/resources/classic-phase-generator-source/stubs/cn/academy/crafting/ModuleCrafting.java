package cn.academy.crafting;
import cn.academy.crafting.item.ItemMatterUnit; import net.minecraftforge.fluids.Fluid;
public final class ModuleCrafting { public static final Fluid fluidImagProj = new Fluid("imagproj"); public static final ItemMatterUnit matterUnit = new ItemMatterUnit(); public static final Phase imagPhase = new Phase(); public static final class Phase { public final ItemMatterUnit.MatterMaterial mat = ItemMatterUnit.PHASE; } }

