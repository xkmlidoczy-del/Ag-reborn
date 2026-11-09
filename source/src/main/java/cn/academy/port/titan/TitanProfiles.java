package cn.academy.port.titan;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Server-authoritative, auditable values; health and damage use Minecraft HP. */
public final class TitanProfiles {
    private TitanProfiles() {}
    public enum Shape { RING, TARGET, BEAM }
    public enum Effect { FIRE, FROST, SHOCK, ROOT, SPORE, PRESSURE, GRAVITY, VENOM, SLASH, DRAIN, BLIND, RADIANCE }
    public enum Environment { LAVA, FROST, STORM, GROVE, TIDAL, VOID, CRYSTAL, SAND, FUNGUS, SOUL, METAL, NIGHT }
    public enum Mobility { GROUND, FLY, SWIM }

    /** range is acquisition distance; radius is area radius or beam half-width, in blocks. */
    public record Ability(String name, Shape shape, Effect effect, double range, double radius,
                          float damage, int windupTicks, int cooldownTicks) {
        public Ability {
            if (name.isBlank() || range <= 0 || radius <= 0 || damage <= 0 || windupTicks < 10 || cooldownTicks < 80)
                throw new IllegalArgumentException("Invalid titan ability: " + name);
        }
    }
    public record Profile(String id, String name, int color, double health, double armor,
                          double attack, double speed, Mobility mobility, Environment environment,
                          Ability first, Ability second) {
        public Profile {
            if (id.isBlank() || health < 1 || health > 1024 || armor < 0 || armor > 30 || attack <= 0 || speed <= 0)
                throw new IllegalArgumentException("Invalid titan profile: " + id);
        }
    }
    private static Ability a(String n, Shape s, Effect e, double range, double radius, float damage, int windup, int cooldown) {
        // V6 deliberately uses the same enlarged geometry for telegraph and damage.
        return new Ability(n,s,e,Math.round(range*1.4),Math.round(radius*1.5),damage,windup,cooldown);
    }
    private static Profile p(String id,String n,int color,double hp,double armor,double attack,double speed,Mobility m,Environment env,Ability first,Ability second) {
        return new Profile(id,n,color,hp,armor,attack,speed,m,env,first,second);
    }
    private static final Map<String,Profile> PROFILES;
    static {
        Map<String,Profile> p=new LinkedHashMap<>();
        add(p,p("lava_rex","熔岩暴龙",0xff6727,960,22,17,.24,Mobility.GROUND,Environment.LAVA,
            a("炽核震荡",Shape.RING,Effect.FIRE,46,24,16,32,240),a("熔星坠击",Shape.TARGET,Effect.FIRE,64,14,20,42,340)));
        add(p,p("frost_bastion","冰甲兽",0x8ee8ff,1000,26,15,.19,Mobility.GROUND,Environment.FROST,
            a("极寒环潮",Shape.RING,Effect.FROST,44,25,14,34,260),a("冰晶崩落",Shape.TARGET,Effect.FROST,60,15,18,42,340)));
        add(p,p("storm_drake","雷棘飞龙",0x7cbfff,700,14,13,.29,Mobility.FLY,Environment.STORM,
            a("离子雷枪",Shape.BEAM,Effect.SHOCK,64,5,17,28,220),a("雷暴落区",Shape.TARGET,Effect.SHOCK,64,18,18,38,320)));
        add(p,p("grove_titan","噬林长颈兽",0x82d16e,900,18,13,.20,Mobility.GROUND,Environment.GROVE,
            a("森罗根缚",Shape.RING,Effect.ROOT,44,25,12,34,250),a("繁生孢爆",Shape.TARGET,Effect.SPORE,60,16,15,38,320)));
        add(p,p("tidal_sail","深渊裂鳍兽",0x57d8dd,880,17,16,.25,Mobility.SWIM,Environment.TIDAL,
            a("深压涡流",Shape.RING,Effect.PRESSURE,46,26,15,32,240),a("裂潮水枪",Shape.BEAM,Effect.PRESSURE,64,6,19,36,300)));
        add(p,p("ironback","环核浮游兽",0x69e8e0,780,20,12,.24,Mobility.FLY,Environment.VOID,
            a("引力坍环",Shape.RING,Effect.GRAVITY,48,28,13,34,260),a("环核湮灭",Shape.TARGET,Effect.RADIANCE,64,18,20,42,340)));
        add(p,p("crystal_mantis","晶体螳螂",0xc3a0ff,680,18,14,.28,Mobility.GROUND,Environment.CRYSTAL,
            a("棱晶断空",Shape.BEAM,Effect.SLASH,58,6,18,26,210),a("碎晶风暴",Shape.RING,Effect.SLASH,44,22,15,32,280)));
        add(p,p("dune_scorpion","沙丘裂背蝎",0xe9ad53,860,23,15,.23,Mobility.GROUND,Environment.SAND,
            a("蚀毒刺雨",Shape.TARGET,Effect.VENOM,60,15,15,30,240),a("裂砂震域",Shape.RING,Effect.BLIND,46,27,16,38,330)));
        add(p,p("myco_frog","菌冠畸蛙",0xb8e36f,760,14,12,.23,Mobility.GROUND,Environment.FUNGUS,
            a("菌冠孢云",Shape.RING,Effect.SPORE,44,23,12,30,230),a("畸跃震落",Shape.TARGET,Effect.VENOM,56,16,17,38,320)));
        add(p,p("ossuary_dragon","骨骸裂口体",0xa5eadc,820,16,15,.23,Mobility.GROUND,Environment.SOUL,
            a("裂魂吸噬",Shape.RING,Effect.DRAIN,44,24,14,32,250),a("幽骸裂隙",Shape.TARGET,Effect.DRAIN,60,16,19,40,330)));
        add(p,p("clockwork_rex","机械收割蛛",0x69e4ec,980,25,16,.21,Mobility.GROUND,Environment.METAL,
            a("收割光刃",Shape.BEAM,Effect.SLASH,60,7,19,30,230),a("电磁拘束场",Shape.RING,Effect.GRAVITY,46,28,14,36,320)));
        add(p,p("astral_moth","星辉蛾",0xe8b5ff,620,12,10,.30,Mobility.FLY,Environment.NIGHT,
            a("星尘帷幕",Shape.RING,Effect.BLIND,44,26,11,30,220),a("星辉坠落",Shape.TARGET,Effect.RADIANCE,64,17,18,38,300)));
        if (p.size()!=12) throw new IllegalStateException("Expected twelve unique titan profiles");
        PROFILES=Collections.unmodifiableMap(p);
    }
    private static void add(Map<String,Profile> all,Profile p) {
        if (all.putIfAbsent(p.id(),p)!=null) throw new IllegalStateException("Duplicate titan id " + p.id());
    }
    public static Map<String,Profile> all() { return PROFILES; }
    public static Profile get(String id) {
        Profile p=PROFILES.get(id);
        if(p==null)throw new IllegalArgumentException("Unknown titan: "+id);
        return p;
    }
}
