package cn.academy.port.gametest;

import cn.academy.port.energy.ClassicEnergyItems;
import cn.academy.port.fusion.ClassicFusion;
import cn.academy.port.fusion.ClassicFusorBlockEntity;
import cn.academy.port.machine.MachineDevelopers;
import cn.academy.port.survival.ClassicMaterials;
import cn.academy.port.wireless.ClassicWirelessDevices;
import java.util.List;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Opt-in development-only physical UI fixture. Existing jar exclusion removes this entire package. */
@EventBusSubscriber(modid="academy")
public final class AcademyWirelessScene {
    private static final String KEY="academy:wireless_scene_origin";
    private AcademyWirelessScene(){}
    @SubscribeEvent public static void commands(RegisterCommandsEvent event){
        if(!Boolean.getBoolean("academy.wireless.qa"))return;
        event.getDispatcher().register(Commands.literal("academy_wireless_scene").requires(source->source.hasPermission(2))
            .then(Commands.literal("setup").executes(context->setup(context.getSource().getPlayerOrException())))
            .then(Commands.literal("normal").executes(context->normal(context.getSource().getPlayerOrException())))
            .then(Commands.literal("view").then(Commands.argument("device",com.mojang.brigadier.arguments.StringArgumentType.word()).executes(context->view(context.getSource().getPlayerOrException(),com.mojang.brigadier.arguments.StringArgumentType.getString(context,"device"))))));
    }
    private static ItemStack s(Item item){return new ItemStack(item);}private static ItemStack e(){return ItemStack.EMPTY;}
    private static ItemStack craft(ServerPlayer player,String recipe,int width,int height,ItemStack... cells){var actual=(CraftingRecipe)player.serverLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("academy","classic/"+recipe)).orElseThrow().value();var input=CraftingInput.of(width,height,List.of(cells));if(!actual.matches(input,player.serverLevel()))throw new IllegalStateException("Source fixture recipe mismatch "+recipe);return actual.assemble(input,player.registryAccess());}
    private static void give(ServerPlayer player,ItemStack item){if(!player.getInventory().add(item))player.drop(item,false);}
    private static boolean place(ServerPlayer player,ItemStack item,BlockPos pos){var old=player.getMainHandItem();boolean creative=player.getAbilities().instabuild;float yaw=player.getYRot(),pitch=player.getXRot();try{player.setYRot(0);player.getAbilities().instabuild=false;player.setItemInHand(InteractionHand.MAIN_HAND,item);return ((BlockItem)item.getItem()).place(new BlockPlaceContext(player,InteractionHand.MAIN_HAND,item,new BlockHitResult(Vec3.atCenterOf(pos),net.minecraft.core.Direction.UP,pos,false))).consumesAction();}finally{player.getAbilities().instabuild=creative;player.setItemInHand(InteractionHand.MAIN_HAND,old);player.setYRot(yaw);player.setXRot(pitch);}}
    public static int setup(ServerPlayer player){
        if(!Boolean.getBoolean("academy.wireless.qa"))return 0;var level=player.serverLevel();var base=player.blockPosition().offset(3,1,3);
        // No clear/fill operation: abort if any intended source cell or access space is occupied/unloaded.
        for(int x=-3;x<=10;x++)for(int z=-4;z<=10;z++)for(int y=-1;y<=4;y++){var pos=base.offset(x,y,z);if(!level.hasChunkAt(pos)||!level.isEmptyBlock(pos)){player.sendSystemMessage(Component.literal("Scene needs an empty, loaded, open-sky area. No existing blocks were removed."));return 0;}}
        if(!level.canSeeSky(base.offset(3,1,0))){player.sendSystemMessage(Component.literal("Scene needs open sky; nothing was cleared."));return 0;}
        for(int x=-3;x<=10;x++)for(int z=-4;z<=10;z++)level.setBlock(base.offset(x,-1,z),Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);
        level.setDayTime(1000);level.setWeatherParameters(240000,0,false,false);
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,level.getServer());
        var solar=craft(player,"solar_gen_09",3,3,s(Items.GLASS_PANE),s(Items.GLASS_PANE),s(Items.GLASS_PANE),e(),s(ClassicMaterials.WAFER.get()),e(),s(ClassicMaterials.ENERGY_CONVERT_COMPONENT.get()),s(ClassicMaterials.MACHINE_FRAME.get().asItem()),s(ClassicMaterials.ENERGY_CONVERT_COMPONENT.get()));
        var node=craft(player,"wireless_node_basic_14",3,3,e(),s(ClassicMaterials.CALC_CHIP.get()),e(),s(Items.IRON_INGOT),s(ClassicMaterials.MACHINE_FRAME.get().asItem()),s(Items.IRON_INGOT),s(ClassicMaterials.CRYSTAL_LOW.get()),s(ClassicMaterials.RESO_CRYSTAL.get()),s(ClassicMaterials.CRYSTAL_LOW.get()));
        var fusor=craft(player,"imag_fusor_23",3,3,s(ClassicMaterials.CONSTRAINT_PLATE.get()),s(ClassicMaterials.CRYSTAL_LOW.get()),s(ClassicMaterials.CONSTRAINT_PLATE.get()),s(ClassicMaterials.CALC_CHIP.get()),s(ClassicMaterials.MACHINE_FRAME.get().asItem()),s(ClassicMaterials.CALC_CHIP.get()),s(ClassicMaterials.CONSTRAINT_PLATE.get()),s(ClassicFusion.MATTER_UNIT.get()),s(ClassicMaterials.CONSTRAINT_PLATE.get()));
        var matrix=craft(player,"wireless_matrix_27",3,3,e(),s(ClassicMaterials.RESO_CRYSTAL.get()),e(),s(Items.REDSTONE),s(ClassicMaterials.MACHINE_FRAME.get().asItem()),s(Items.REDSTONE),s(ClassicMaterials.DATA_CHIP.get()),s(ClassicMaterials.RESO_CRYSTAL.get()),s(ClassicMaterials.DATA_CHIP.get()));
        var suppliedCore=craft(player,"matrix_core_0_28",3,3,e(),s(ClassicMaterials.CRYSTAL_LOW.get()),e(),s(ClassicMaterials.CALC_CHIP.get()),s(ClassicMaterials.RESO_CRYSTAL.get()),s(ClassicMaterials.DATA_CHIP.get()),e(),s(ClassicMaterials.ENERGY_CONVERT_COMPONENT.get()),e());
        var suppliedNormal=craft(player,"developer_normal_45",3,3,s(ClassicMaterials.BRAIN_COMPONENT.get()),s(ClassicMaterials.INFO_COMPONENT.get()),s(ClassicMaterials.ENERGY_CONVERT_COMPONENT.get()),suppliedCore,s(Items.RED_BED),s(Items.PISTON),s(ClassicMaterials.CRYSTAL_NORMAL.get()),s(ClassicMaterials.MACHINE_FRAME.get().asItem()),s(Items.REDSTONE));
        if(!place(player,node,base)||!place(player,solar,base.offset(3,0,0))||!place(player,fusor,base.offset(0,0,3))||!place(player,matrix,base.offset(3,0,3))||!place(player,suppliedNormal,base.offset(6,0,3)))throw new IllegalStateException("Fixture placement refused; any successfully placed empty devices remain ordinary recoverable blocks");
        for(int index=0;index<3;index++)level.setBlock(base.offset(index,0,8),ClassicFusion.PHASE_BLOCK.get().defaultBlockState(),Block.UPDATE_ALL);
        give(player,craft(player,"matter_unit_17",3,3,e(),s(ClassicMaterials.CONSTRAINT_PLATE.get()),e(),s(ClassicMaterials.CONSTRAINT_PLATE.get()),s(Items.GLASS),s(ClassicMaterials.CONSTRAINT_PLATE.get()),e(),s(ClassicMaterials.CONSTRAINT_PLATE.get()),e()));
        give(player,craft(player,"matrix_core_0_28",3,3,e(),s(ClassicMaterials.CRYSTAL_LOW.get()),e(),s(ClassicMaterials.CALC_CHIP.get()),s(ClassicMaterials.RESO_CRYSTAL.get()),s(ClassicMaterials.DATA_CHIP.get()),e(),s(ClassicMaterials.ENERGY_CONVERT_COMPONENT.get()),e()));
        give(player,new ItemStack(ClassicMaterials.CONSTRAINT_PLATE.get(),3));give(player,new ItemStack(ClassicMaterials.CRYSTAL_LOW.get(),2));give(player,craft(player,"energy_unit_18",3,3,e(),s(ClassicMaterials.CONSTRAINT_PLATE.get()),e(),s(ClassicMaterials.CONSTRAINT_PLATE.get()),s(ClassicMaterials.CRYSTAL_LOW.get()),s(ClassicMaterials.CONSTRAINT_PLATE.get()),e(),s(ClassicMaterials.DATA_CHIP.get()),e()));
        player.getPersistentData().putLong(KEY,base.asLong());player.sendSystemMessage(Component.literal("Wireless UI fixture at "+base+": base materials/3phase cells/a Normal crystal/platform supplied; daylight controlled. Real recipe-derived solar/node/Fusor/matrix/Normal are EMPTY; existing abilities and portable payload are preserved. Link solar and Fusor through their wireless pages; charge Fusor before inserting crystal, collect phase with matter units. No energy, category or mastery granted. /academy_wireless_scene view solar|node|fusor|matrix|normal|phase_1|phase_2|phase_3|overview teleports to a fixed view. /academy_wireless_scene normal consumes an actually produced Fusor crystal to craft an empty Normal developer."));return 1;
    }
    public static int view(ServerPlayer player,String device){
        if(!Boolean.getBoolean("academy.wireless.qa")||!player.getPersistentData().contains(KEY))return 0;var base=BlockPos.of(player.getPersistentData().getLong(KEY));double dx,dz,dy=0,targetX,targetY=.5,targetZ;
        switch(device){case "node"->{targetX=.5;targetZ=.5;dx=.5;dz=-2.5;}case "solar"->{targetX=3.5;targetZ=.5;targetY=.25;dx=3.5;dz=-2.5;}case "fusor"->{targetX=.5;targetZ=3.5;dx=.5;dz=.5;dy=1;}case "matrix"->{targetX=4;targetY=1;targetZ=4;dx=4;dz=.5;}case "normal"->{targetX=6.5;targetY=1;targetZ=4.5;dx=6.5;dz=1;}case "phase_1","phase_2","phase_3"->{int index=device.charAt(device.length()-1)-'1';targetX=index+.5;targetY=.5;targetZ=8.5;dx=targetX;dz=targetZ-3;dy=0;}case "overview"->{targetX=3;targetY=.5;targetZ=3;dx=-2.5;dz=-3.5;}default->{player.sendSystemMessage(Component.literal("Views: solar,node,fusor,matrix,normal,phase_1,phase_2,phase_3,overview"));return 0;}}
        double x=base.getX()+dx,y=base.getY()+dy,z=base.getZ()+dz;double tx=base.getX()+targetX-x,tz=base.getZ()+targetZ-z,ty=base.getY()+targetY-y-player.getEyeHeight();float yaw=(float)Math.toDegrees(Math.atan2(-tx,tz)),pitch=(float)-Math.toDegrees(Math.atan2(ty,Math.hypot(tx,tz)));player.teleportTo(player.serverLevel(),x,y,z,java.util.Set.of(),yaw,pitch);return 1;
    }
    public static int normal(ServerPlayer player){
        if(!Boolean.getBoolean("academy.wireless.qa")||!player.getPersistentData().contains(KEY))return 0;var base=BlockPos.of(player.getPersistentData().getLong(KEY));var level=player.serverLevel();var pos=base.offset(0,0,3);
        if(!level.hasChunkAt(pos)||!(level.getBlockEntity(pos) instanceof ClassicFusorBlockEntity fusor)||!fusor.getItem(1).is(ClassicMaterials.CRYSTAL_NORMAL.get())){player.sendSystemMessage(Component.literal("First obtain a real Normal crystal in this scene's wirelessly powered Fusor output. No crystal was granted."));return 0;}
        var core=craft(player,"matrix_core_0_28",3,3,e(),s(ClassicMaterials.CRYSTAL_LOW.get()),e(),s(ClassicMaterials.CALC_CHIP.get()),s(ClassicMaterials.RESO_CRYSTAL.get()),s(ClassicMaterials.DATA_CHIP.get()),e(),s(ClassicMaterials.ENERGY_CONVERT_COMPONENT.get()),e());
        var normal=craft(player,"developer_normal_45",3,3,s(ClassicMaterials.BRAIN_COMPONENT.get()),s(ClassicMaterials.INFO_COMPONENT.get()),s(ClassicMaterials.ENERGY_CONVERT_COMPONENT.get()),core,s(Items.RED_BED),s(Items.PISTON),fusor.getItem(1).copyWithCount(1),s(ClassicMaterials.MACHINE_FRAME.get().asItem()),s(Items.REDSTONE));fusor.removeItem(1,1);give(player,normal);player.sendSystemMessage(Component.literal("Real Fusor output consumed by authentic Normal recipe; other base recipe ingredients are declared fixture supplies. Empty Normal developer added. Place it near node, connect in Current Node, and earn all power/development through actual gameplay."));return 1;
    }
}
