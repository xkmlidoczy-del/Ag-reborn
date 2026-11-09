/* AcademyCraft1.0.7 EntityMineRayExpert/EntityMineRayLuck adaptation,
 * Copyright Lambda Innovation 2013-2016, GPLv3; see NOTICE. */
package cn.academy.port.client;

import java.util.List;
import cn.academy.port.client.ClassicMeltdownerBeamTimeline.Spec;

/** Exact additional source variants; geometry/lifetime and replay fences remain the m13 algorithms. */
public final class ClassicAdvancedMineRayTimeline {
    public static final String EXPERT_ID="mine_ray_expert",LUCK_ID="mine_ray_luck";
    public static final Spec EXPERT=new Spec(233333,200,400,300,.045,.056,.5,.5,.6),
            LUCK=new Spec(233333,200,400,300,.04,.05,.45,.6,.6);
    public static final String EXPERT_START="md.mine_expert_startup",LUCK_START="md.mine_luck_startup";
    public static final String EXPERT_TEXTURE="mdray_expert",LUCK_TEXTURE="mdray_luck";
    public record Color(int r,int g,int b,int a) {}
    public static final Color EXPERT_INNER=new Color(216,248,216,180),EXPERT_OUTER=new Color(106,242,106,50),
            LUCK_INNER=new Color(241,229,247,230),LUCK_OUTER=new Color(205,166,232,50);
    public static final List<String> KINDS=List.of("mine_ray_expert_start","mine_ray_expert_end","mine_ray_expert_particles",
            "mine_ray_luck_start","mine_ray_luck_end","mine_ray_luck_particles");
    private ClassicAdvancedMineRayTimeline(){}
    public static boolean owns(String id){return EXPERT_ID.equals(id)||LUCK_ID.equals(id);}
    public static String skill(String kind){if(kind==null||!KINDS.contains(kind))return "";return kind.startsWith(EXPERT_ID)?EXPERT_ID:LUCK_ID;}
    public static Spec spec(String id){return EXPERT_ID.equals(id)?EXPERT:LUCK_ID.equals(id)?LUCK:ClassicMeltdownerBeamTimeline.BASIC;}
    public static String startup(String id){return EXPERT_ID.equals(id)?EXPERT_START:LUCK_ID.equals(id)?LUCK_START:ClassicMeltdownerBeamTimeline.MINE_START_SOUND;}
}
