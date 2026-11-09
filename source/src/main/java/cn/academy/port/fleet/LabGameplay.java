package cn.academy.port.fleet;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Server-authoritative ammunition, reload, fire and driver input. */
public final class LabGameplay {
    public static int reserve(ServerPlayer p,GunItem gun){return CoilReloads.reserve(p,gun.variant);}
    public static void request(ServerPlayer p,LabNetwork.Request req){
        if(req.action().equals("drive")){if(p.getVehicle() instanceof LabVehicle v)v.input(p,req.a(),req.b());return;}
        if(req.action().equals("horn")||req.action().equals("siren")){
            if(p.getVehicle() instanceof LabVehicle v && v.getFirstPassenger()==p){if(req.action().equals("horn"))v.horn();else v.toggleSiren();}return;
        }
        ItemStack stack=p.getMainHandItem(); if(!(stack.getItem() instanceof GunItem gun))return;
        long now=p.level().getGameTime();
        if(req.action().equals("reload")){if(CoilReloads.active(p))CoilReloads.cancel(p);else CoilReloads.begin(p,stack);return;}
        if(req.action().equals("fire")){
            if(CoilReloads.active(p)||p.getCooldowns().isOnCooldown(gun))return;
            if(GunItem.ammo(stack)<=0 || !GunItem.hasMagazine(stack)){AudioDefs.emit(p,gun.variant,"dry",now);return;}
            GunItem.ammo(stack,GunItem.ammo(stack)-1);GunItem.lastFire(stack,now);p.getCooldowns().addCooldown(gun,InteractionLab.COOLDOWN[gun.variant]);
            AudioDefs.emit(p,gun.variant,"fire",now);
            boolean damaged=FleetBallistics.fire(p,gun.variant,p.getLookAngle());

        }
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post e){
        if(!(e.getEntity() instanceof ServerPlayer p))return;
        CoilReloads.tick(p);

    }
    @SubscribeEvent public static void death(net.neoforged.neoforge.event.entity.living.LivingDeathEvent e){if(e.getEntity() instanceof ServerPlayer p)CoilReloads.cancel(p);}
    @SubscribeEvent public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e){
        if(e.getEntity() instanceof ServerPlayer p)CoilReloads.cancel(p);
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("aca_fleet").requires(s->s.hasPermission(2))
            .then(Commands.literal("kit").executes(c->kit(c.getSource().getPlayerOrException(),2))
                .then(Commands.argument("weapon",IntegerArgumentType.integer(1,6)).executes(c->kit(c.getSource().getPlayerOrException(),IntegerArgumentType.getInteger(c,"weapon")-1))))
            .then(Commands.literal("car").executes(c->car(c.getSource().getPlayerOrException(),0))
                .then(Commands.argument("vehicle",IntegerArgumentType.integer(1,6)).executes(c->car(c.getSource().getPlayerOrException(),IntegerArgumentType.getInteger(c,"vehicle")-1)))));
    }
    private static int kit(ServerPlayer p,int i){
        ItemStack stack=new ItemStack(InteractionLab.GUNS[i].get());GunItem.ammo(stack,InteractionLab.CAPS[i]);
        if(!p.getInventory().add(stack))p.drop(stack,false);
        if(i>=0){for(int k=0;k<3;k++)p.getInventory().add(CoilMagazineItem.create(i,InteractionLab.CAPS[i]));}
        else p.getInventory().add(new ItemStack(i>=4?InteractionLab.HEAVY_CELL.get():InteractionLab.LIGHT_CELL.get(),64));
        p.displayClientMessage(Component.literal("ACA LAB: " + InteractionLab.GUN_IDS[i] + " · J 换弹 · 右键瞄准 · 左键射击"),true);return 1;
    }
    private static int car(ServerPlayer p,int i){
        if(p.isPassenger())return 0;
        LabVehicle v=InteractionLab.CAR.get().create(p.serverLevel());if(v==null)return 0;
        v.variant(i);v.moveTo(p.getX(),p.getY(),p.getZ(),p.getYRot(),0);p.serverLevel().addFreshEntity(v);p.startRiding(v);
        p.displayClientMessage(Component.literal("ACA LAB: " + InteractionLab.CAR_IDS[i] + " · W/S 行驶 · A/D 转向 · Shift 下车"),true);
        InteractionLab.LOGGER.info("ACLAB_CAR_SPAWN variant={} mounted={}",i,p.isPassenger());return 1;
    }
}
