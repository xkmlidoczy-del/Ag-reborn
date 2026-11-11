package cn.academy.port.qa;

import cn.academy.port.highpower.HighPowerCombat;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.channel.embedded.EmbeddedChannel;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** R4 environment/consenting-partner preparation only. Never calls an ability,
 * installs its lifecycle state, debits its payment, or manufactures VFX receipts.
 * The caster is the real client; the embedded ally is explicitly a mock connection. */
final class MacroCoverageFixturesQa {
    private static ServerPlayer ally;
    private static EmbeddedChannel channel;
    private MacroCoverageFixturesQa() {}

    static void step(Minecraft mc,String op) {
        if(!Boolean.getBoolean("academy.qa.confirmOwnWorld") || mc.getSingleplayerServer()==null)
            throw new IllegalStateException("Owned isolated integrated test world required");
        Vec3 aim=mc.getSingleplayerServer().submit(()-> {
            var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            var world=p.serverLevel();
            if(op.equals("clear")) {clear();return null;}
            if(op.startsWith("bind:")) {
                String id=op.substring(5);var state=cn.academy.port.AbilityStorage.get(p);
                if(!state.learned(id)||!state.presets.edit(state.presets.current(),2,id,state::learned))
                    throw new IllegalStateException("Learned same-category fixture binding required: "+id);
                cn.academy.port.AbilityStorage.save(p);cn.academy.port.AcademyNetwork.sync(p);return null;
            }
            if(op.startsWith("ally:")) {
                clear();String mode=op.substring(5);
                var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"R4Ally"),false);
                ally=new ServerPlayer(p.server,world,cookie.gameProfile(),cookie.clientInformation());
                var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);
                NetworkRegistry.configureMockConnection(connection);p.server.getPlayerList().placeNewPlayer(connection,ally,cookie);
                ally.setGameMode(GameType.SURVIVAL);ally.setShiftKeyDown(true);
                Vec3 at=p.position().add(0,mode.equals("float")?5:0,4);
                ally.moveTo(at,180,0);ally.connection.resetPosition();ally.setOnGround(!mode.equals("float"));
                ally.setDeltaMovement(mode.equals("float")?new Vec3(0,-.3,0):Vec3.ZERO);
                HighPowerCombat.enroll(ally,5);
                var board=p.getScoreboard();var team=board.getPlayerTeam("ag_r4_ally");
                if(team==null)team=board.addPlayerTeam("ag_r4_ally");
                board.addPlayerToTeam(p.getScoreboardName(),team);board.addPlayerToTeam(ally.getScoreboardName(),team);
                // An embedded connection has no remote movement heartbeat. Run the real
                // native entity update once so water/pose flags reflect the prepared world.
                ally.doTick();
                if(mode.equals("fire")) {
                    HighPowerCombat.spendShieldFraction(ally,BigDecimal.ONE);
                    ally.setRemainingFireTicks(600);
                }
                if(mode.equals("mental"))for(int i=0;i<4;i++) {
                    var mob=EntityType.HUSK.create(world);mob.moveTo(p.position().add((i-1.5)*2,0,9),180,0);
                    mob.setSilent(true);world.addFreshEntity(mob);HighPowerCombat.enroll(mob,5);
                    mob.setTarget(ally);mob.getNavigation().moveTo(ally,1);
                }
                LogUtils.getLogger().info("QA_R4_FIXTURE ally={} mockConnection=true consenting=true mode={} feet={} noAbilityInvoked=true",ally.getUUID(),mode,at);
                return ally.getBoundingBox().getCenter();
            }
            if(op.startsWith("water:")) {
                String id=op.substring(6);boolean dry=id.equals("hydro_irrigate")||id.startsWith("hydro_douse_");
                if(dry) {
                    // Recessed source stays contained; a surface source flows toward the
                    // farmland and can hydrate it before the tested irrigation key.
                    for(int x=-4;x<=-1;x++)for(int z=-2;z<=0;z++)world.setBlock(new BlockPos(x,-61,z),Blocks.WATER.defaultBlockState(),3);
                    if(id.equals("hydro_irrigate")) {
                        var target=new BlockPos(0,-61,6);world.setBlock(target,Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,0),3);
                        return target.getCenter().add(0,.4,0);
                    }
                    if(id.equals("hydro_douse_self")) {
                        // An intact hydro shield extinguishes fire before the
                        // input. Prepare the real burning, depleted-shield case.
                        HighPowerCombat.spendShieldFraction(p,BigDecimal.ONE);
                        p.setRemainingFireTicks(600);
                    }
                } else {
                    for(int x=-5;x<=5;x++)for(int z=-3;z<=10;z++) {
                        world.setBlock(new BlockPos(x,-64,z),Blocks.STONE.defaultBlockState(),3);
                        for(int y=-63;y<=-59;y++)world.setBlock(new BlockPos(x,y,z),Blocks.WATER.defaultBlockState(),3);
                    }
                    p.teleportTo(p.getX(),-61,p.getZ());p.setOnGround(false);
                }
                LogUtils.getLogger().info("QA_R4_FIXTURE water id={} dryCaster={} noAbilityInvoked=true",id,dry);
                return ally==null?null:ally.getBoundingBox().getCenter();
            }
            if(op.startsWith("air:")) {
                double height=Double.parseDouble(op.substring(4));
                p.teleportTo(p.getX(),-60+height,p.getZ());p.setOnGround(false);p.setDeltaMovement(0,-.1,0);
                LogUtils.getLogger().info("QA_R4_FIXTURE naturalFall height={} noGravity={} noAbilityInvoked=true",height,p.isNoGravity());return null;
            }
            if(op.equals("shield-gap")) {
                HighPowerCombat.spendShieldFraction(p,new BigDecimal("0.25"));
                LogUtils.getLogger().info("QA_R4_FIXTURE shieldDepletedBeforeInput=true noAbilityInvoked=true");return null;
            }
            if(op.equals("aim-item")) {
                var item=world.getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(16),e->!e.getItem().isEmpty())
                        .stream().min(Comparator.comparingDouble(p::distanceToSqr)).orElseThrow();
                return item.getBoundingBox().getCenter();
            }
            if(op.equals("aim-ally")) {if(ally==null)throw new IllegalStateException("ally fixture required");return ally.getBoundingBox().getCenter();}
            if(op.equals("float-state")) {
                try {
                    var type=cn.academy.port.canon.CanonFloatItems.class;
                    var lookup=type.getDeclaredMethod("target",ServerPlayer.class,String.class);lookup.setAccessible(true);
                    var leases=type.getDeclaredField("ACTIVE");leases.setAccessible(true);
                    var items=world.getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(20));
                    LogUtils.getLogger().info("QA_R4_FLOAT_STATE target={} leases={} items={} liftClock={} pullClock={}",
                            lookup.invoke(null,p,"float_item_pull"),leases.get(null),
                            items.stream().map(i->i.getUUID()+":"+i.position()+":"+i.getDeltaMovement()).toList(),
                            p.getPersistentData().getCompound(cn.academy.port.highpower.HighPowerResources.DATA_KEY).getLong("next_float_item_lift"),
                            p.getPersistentData().getCompound(cn.academy.port.highpower.HighPowerResources.DATA_KEY).getLong("next_float_item_pull"));
                }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
                return null;
            }
            if(op.equals("state")) {
                Object source;
                try {
                    var lookup=cn.academy.port.canon.CanonWaterSkills.class.getDeclaredMethod("source",ServerPlayer.class);
                    lookup.setAccessible(true);source=lookup.invoke(null,p);
                }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
                LogUtils.getLogger().info("QA_R4_PREFLIGHT feet={} velocity={} water={} ground={} source={} allyWater={} allyCrouch={} allyFeet={}",
                        p.position(),p.getDeltaMovement(),p.isInWaterOrBubble(),p.onGround(),source,
                        ally!=null&&ally.isInWaterOrBubble(),ally!=null&&ally.isShiftKeyDown(),ally==null?null:ally.position());return null;
            }
            throw new IllegalArgumentException(op);
        }).join();
        if(aim!=null) {
            var d=aim.subtract(mc.player.getEyePosition());mc.player.setYRot((float)-Math.toDegrees(Math.atan2(d.x,d.z)));
            mc.player.setYHeadRot(mc.player.getYRot());mc.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));
        }
    }
    private static void clear() {
        if(ally!=null){ally.server.getPlayerList().remove(ally);ally.discard();ally=null;}
        if(channel!=null){channel.finishAndReleaseAll();channel=null;}
    }
}
