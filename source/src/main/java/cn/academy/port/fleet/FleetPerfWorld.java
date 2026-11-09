package cn.academy.port.fleet;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
/** Identical parked-car fixture for both baseline and revised renderer measurements. */
public final class FleetPerfWorld {
 @SubscribeEvent public static void register(RegisterCommandsEvent e){if(!Boolean.getBoolean("academy.qa.showcase")||!Boolean.getBoolean("academy.qa.confirmOwnWorld"))return;e.getDispatcher().register(Commands.literal("aclab_qa_perfcar").requires(s->s.hasPermission(2)).then(Commands.argument("variant",IntegerArgumentType.integer(1,6)).then(Commands.argument("count",IntegerArgumentType.integer(1,16)).executes(c->{var p=c.getSource().getPlayerOrException();p.stopRiding();p.getInventory().clearContent();for(var old:p.serverLevel().getEntitiesOfClass(LabVehicle.class,p.getBoundingBox().inflate(150)))old.discard();int n=IntegerArgumentType.getInteger(c,"count"),variant=IntegerArgumentType.getInteger(c,"variant")-1;for(int i=0;i<n;i++){var v=InteractionLab.CAR.get().create(p.serverLevel());v.variant(variant);v.moveTo((i%4)*4,-60,(i/4)*14,0,0);p.serverLevel().addFreshEntity(v);}InteractionLab.LOGGER.info("FLEET_PERF_WORLD variant={} count={}",variant,n);return 1;}))));}
}
