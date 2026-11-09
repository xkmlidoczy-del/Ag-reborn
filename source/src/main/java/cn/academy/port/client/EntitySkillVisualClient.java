package cn.academy.port.client;

import cn.academy.port.skill.EntitySkillVisual;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid="academy",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class EntitySkillVisualClient {
    private EntitySkillVisualClient() {}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        EntitySkillVisual.clientReceiver=v -> {
            var mc=Minecraft.getInstance();if(mc.level==null||!mc.level.dimension().location().equals(v.dimension())) return;
            if(!(mc.level.getEntity(v.entity()) instanceof LivingEntity caster)||!caster.getUUID().equals(v.owner()))return;
            var delta=v.to().subtract(v.from());double length=delta.length();if(length>128)return;
            if(v.phase().equals("HIT")&&(v.skill().equals("meltdowner")||v.skill().equals("electron_bomb")))
                ClassicMeltdownerBeamEffects.endMainRay(caster);
            if(v.phase().equals("HIT")&&(v.skill().equals("arc_gen")||v.skill().equals("thunder_bolt"))) {
                if(length>.01) ClassicEffects.addArc(v.from(),delta.normalize(),length);
            } else if(v.skill().equals("meltdowner")&&v.phase().equals("HIT")) {
                if(length>.01) ClassicMeltdownerBeamEffects.entityRay(caster,v.from(),delta.normalize(),length);
            } else if(v.skill().equals("electron_bomb")) {
                var tag=new CompoundTag();tag.putInt("entity",caster.getId());
                tag.putString("kind",v.phase().equals("START")?"electron_bomb_charge":"electron_bomb");
                tag.putDouble("x",v.from().x);tag.putDouble("y",v.from().y);tag.putDouble("z",v.from().z);
                if(v.phase().equals("START")) {
                    var offset=v.from().subtract(caster.position());tag.putInt("life",v.ticks());
                    tag.putDouble("offset_x",offset.x);tag.putDouble("offset_y",offset.y);tag.putDouble("offset_z",offset.z);
                }
                else if(length>.01) {var dir=delta.normalize();tag.putDouble("dx",dir.x);tag.putDouble("dy",dir.y);tag.putDouble("dz",dir.z);tag.putDouble("length",length);}
                ClassicFirstSkillEffects.receive(tag);
            } else {
                var particle=v.skill().contains("teleport")||v.skill().equals("mark_teleport")||v.skill().equals("flesh_ripping")?ParticleTypes.REVERSE_PORTAL:ParticleTypes.CLOUD;
                for(int i=0;i<16;i++) {
                    double a=i*Math.PI/8;mc.level.addParticle(particle,v.to().x+Math.cos(a)*.45,v.to().y+.5,v.to().z+Math.sin(a)*.45,0,.015,0);
                }
            }
        };
    }
}
