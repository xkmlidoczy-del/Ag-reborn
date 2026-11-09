package cn.academy.port.city;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Civilian specialists. These bounded demonstrations are separate from the player's combat skill engine. */
public final class CityEspers {
    public enum Profile {
        NONE(0,0), ELECTRO(2,0x80dfff), MELT(3,0x94f7b0), TELEPORT(3,0xbc9eff), VECTOR(2,0xf1f0dc),
        MENTAL(3,0xf0c4e8), AIR(2,0xb1efde), FLOAT(1,0xe6d291), NITROGEN(3,0xa0bcd4),
        DARK(4,0xe0d7ff), WATER(2,0x65bde3), PAPER(1,0xffedc8), SHADOW(2,0x6577aa);
        private final int level,color;
        Profile(int level,int color) {this.level=level;this.color=color;}
        public int level() {return level;}
        public String id() {return name().toLowerCase(Locale.ROOT);}
        public Component label() {return Component.translatable("academy.city.esper."+id()+".name");}
        public Component description() {return Component.translatable("academy.city.esper."+id()+".description");}
        static Profile byId(String id) {return Arrays.stream(values()).filter(p -> p.id().equals(id)).findFirst().orElse(NONE);}
    }
    private CityEspers() {}
    static void assign(CityNpcEntity npc,int district,CityCrowdPlan.Role role,long seed) {
        // Most people retain their ordinary identity. Level 4 is restricted to research staff.
        if(Math.floorMod(seed,5)>1) return;
        Profile[] choices=switch(role) {
            case STUDENT -> new Profile[]{Profile.ELECTRO,Profile.AIR,Profile.FLOAT,Profile.PAPER,Profile.WATER};
            case RESEARCHER -> new Profile[]{Profile.MELT,Profile.VECTOR,Profile.DARK,Profile.MENTAL};
            case TECHNICIAN -> new Profile[]{Profile.ELECTRO,Profile.AIR,Profile.TELEPORT};
            case GUARD -> new Profile[]{Profile.VECTOR,Profile.NITROGEN};
            case ATHLETE -> new Profile[]{Profile.AIR,Profile.NITROGEN,Profile.FLOAT};
            case PERFORMER -> new Profile[]{Profile.PAPER,Profile.SHADOW,Profile.MENTAL};
            case MEDICAL -> new Profile[]{Profile.WATER,Profile.MENTAL};
            case AIRPORT,RAIL -> new Profile[]{Profile.TELEPORT,Profile.FLOAT};
            default -> new Profile[]{Profile.FLOAT,Profile.PAPER,Profile.WATER,Profile.SHADOW};
        };
        npc.setEsper(choices[(int)Math.floorMod(seed>>>9,choices.length)]);
        int variation=(int)Math.floorMod(seed>>>20,3)-1;
        npc.abilityLevel(Math.max(1,Math.min(role==CityCrowdPlan.Role.RESEARCHER?4:3,npc.esper().level()+variation)));
        var look=switch(role) {
            case STUDENT -> CityNpcEntity.Look.ESPER_UNIFORM;
            case ATHLETE -> CityNpcEntity.Look.ESPER_TRACK;
            case RESEARCHER -> CityNpcEntity.Look.ESPER_LAB;
            case PERFORMER -> CityNpcEntity.Look.ESPER_ART;
            case RESIDENT,VISITOR -> CityNpcEntity.Look.ESPER_FIELD;
            case OFFICE -> CityNpcEntity.Look.ESPER_WORK;
            default -> npc.look();
        };
        npc.setLook(look);
    }
    static void prepareService(CityNpcEntity npc,CityServicePlan.Role role) {
        npc.setEsper(switch(role) {
            case MENTOR -> Profile.ELECTRO;
            case FIELD_MEDIC -> Profile.WATER;
            case ENGINEER -> Profile.AIR;
            case RESEARCH_CONTACT -> Profile.MELT;
            case CURATOR -> Profile.DARK;
            case LOGISTICS -> Profile.TELEPORT;
            case COACH -> Profile.NITROGEN;
            case RESERVOIR -> Profile.WATER;
            default -> Profile.NONE;
        });
    }
    static int cooldown(CityNpcEntity npc) {
        var brain=npc.combat();if(brain.busy())return 1;
        return brain.runtime().available().stream().mapToInt(d -> (brain.ability().cooldowns.getOrDefault(d.id(),0)+19)/20).min().orElse(0);
    }
    static boolean canDemonstrate(CityNpcEntity npc) {
        var brain=npc.combat();return !brain.busy()&&brain.runtime().available().stream().anyMatch(d -> brain.runtime().canStart(d,null,true));
    }
    static Component demonstrate(ServerPlayer player,CityNpcEntity npc) {
        return Component.translatable(npc.combat().practice(player)?"academy.city.esper.started":"academy.city.combat.not_ready",npc.esper().label());
    }
    static void practice(CityNpcEntity npc) {if(canDemonstrate(npc))npc.combat().practice(null);}
}
