package cn.academy.port.qa;

import cn.academy.port.city.CityCatalog;
import cn.academy.port.city.CityNpcEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/** Focused visual and conversation checks against the final JAR in one client. */
final class CityVisualQa {
    private static final Logger LOG=LogUtils.getLogger();
    private static UUID resident;
    private static int retries;
    private CityVisualQa() {}
    private static void require(boolean ok,String why) { if (!ok) throw new IllegalStateException("QA_CITY_VISUAL " + why); }

    static boolean step(Minecraft mc, String op) {
        switch (op) {
            case "models" -> {
                int tested=0;
                for (var good:CityCatalog.GOODS) if (good.tier()!=null) {
                    var item=BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("academy",good.id()));
                    var model=mc.getItemRenderer().getModel(new ItemStack(item),mc.level,mc.player,0);
                    var gui=model.applyTransform(ItemDisplayContext.GUI,new PoseStack(),false);
                    var hand=model.applyTransform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,new PoseStack(),false);
                    var ground=model.applyTransform(ItemDisplayContext.GROUND,new PoseStack(),false);
                    require(gui!=hand && hand==ground,"context routing failed for "+good.id());
                    require(hand!=mc.getModelManager().getMissingModel() && !hand.getQuads(null,null,RandomSource.create()).isEmpty(),
                            "3D food model not baked for "+good.id());
                    var sprite=ResourceLocation.fromNamespaceAndPath("academy","textures/items/city/"+good.id()+".png");
                    require(mc.getResourceManager().getResource(sprite).isPresent(),"missing food icon "+good.id());
                    tested++;
                }
                require(tested==50,"expected 50 foods, got "+tested);
                for (var look:CityNpcEntity.Look.values()) {
                    var texture=ResourceLocation.fromNamespaceAndPath("academy","textures/entity/city_npc/"+look.id()+".png");
                    require(mc.getResourceManager().getResource(texture).isPresent(),"missing resident skin "+look.id());
                }
                LOG.info("QA_CITY_VISUAL_MODELS foods={} GUI=authored_sprite hand=3d ground=3d residentSkins={} missing=0",tested,CityNpcEntity.Look.values().length);
            }
            case "talk" -> {
                var server=mc.getSingleplayerServer();
                if (resident==null) resident=server.submit(() -> {
                    var p=server.getPlayerList().getPlayer(mc.player.getUUID());
                    return p.serverLevel().getEntitiesOfClass(CityNpcEntity.class,p.getBoundingBox().inflate(72),
                            npc -> npc.ambient() && npc.getCustomName()==null && npc.activity()!=CityNpcEntity.Activity.CHAT)
                            .stream().findFirst().map(CityNpcEntity::getUUID).orElse(null);
                }).join();
                if (resident==null) { require(++retries<1000,"no natural resident spawned"); return false; }
                var npc=mc.level.getEntitiesOfClass(CityNpcEntity.class,mc.player.getBoundingBox().inflate(110),
                        n->n.getUUID().equals(resident)).stream().findFirst().orElse(null);
                if (npc==null) { require(++retries<1000,"resident not visible to client"); return false; }
                if (npc.distanceToSqr(mc.player)>10) {
                    if (++retries%20==1) mc.player.connection.sendCommand("tp @s "+(npc.getX()+2.4)+" "+npc.getY()+" "+npc.getZ());
                    require(retries<1000,"resident not reachable");return false;
                }
                retries=0;mc.gameMode.interact(mc.player,npc,InteractionHand.MAIN_HAND);
                LOG.info("QA_CITY_VISUAL_TALK_INPUT look={} viaClientPacket=true",npc.look().id());
            }
            case "talk_check" -> {
                var actual=mc.getSingleplayerServer().submit(() -> {
                    var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    var npc=(CityNpcEntity)p.serverLevel().getEntity(resident);
                    return npc==null ? "missing" : npc.activity().id()+":"+npc.expression().id();
                }).join();
                require(actual.equals("chat:smile"),"resident did not turn to conversation: "+actual);
                LOG.info("QA_CITY_VISUAL_TALK_OK state={} realAmbientResident=true",actual);
            }
            case "aim_resident" -> {
                var npc=mc.level.getEntitiesOfClass(CityNpcEntity.class,mc.player.getBoundingBox().inflate(24),
                        n->n.getUUID().equals(resident)).stream().findFirst().orElseThrow();
                LiveInputQa.aim(mc,npc.getX()+","+(npc.getY()+1.25)+","+npc.getZ());
            }
            case "talk_after" -> {
                var actual=mc.getSingleplayerServer().submit(() -> {
                    var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    var npc=(CityNpcEntity)p.serverLevel().getEntity(resident);
                    if (npc==null) return "missing";
                    try {
                        var method=CityNpcEntity.class.getDeclaredMethod("conversing");method.setAccessible(true);
                        return ((boolean)method.invoke(npc) ? "talking:" : "resumed:")+npc.activity().id();
                    } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
                }).join();
                require(actual.startsWith("resumed:"),"resident did not return to normal routine: "+actual);
                LOG.info("QA_CITY_VISUAL_TALK_END restored={}",actual);
            }
            default -> throw new IllegalArgumentException("Unknown city visual step: "+op);
        }
        return true;
    }
}
