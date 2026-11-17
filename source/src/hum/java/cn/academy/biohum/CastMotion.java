package cn.academy.biohum;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** One motion track for the model and the server's casting origin. */
public final class CastMotion {
    public enum Anchor { RIGHT, LEFT, BETWEEN, CHEST, GROUND, EYES, MOUTH, BACK, THIRD_HAND, RIGHT_SHOULDER }

    private record Key(float time, float[] angles) {}
    public record Style(String pattern, Anchor anchor, int tint, int accent, int core,
                        float scale, int trails, boolean quiet, String domain) {
      public boolean technical(){return "esper".equals(domain)||"technology".equals(domain);}
      public boolean ritual(){return "magic".equals(domain)||"occult".equals(domain);}
    }
    private record Clip(List<Key> keys, Style style) {}
    private static final Gson GSON = new Gson();
    private static final Map<String, Clip> CLIPS = new java.util.concurrent.ConcurrentHashMap<>();

    public static Style style(Skills.Skill skill) {
        return clip(skill.id()).style;
    }

    public static float[] sample(Skills.Skill skill, float ticks) {
        float time = ticks / Math.max(1, skill.length());
        var keys = clip(skill.id()).keys;
        if (time <= keys.getFirst().time) return radians(keys.getFirst());
        if (time >= keys.getLast().time) return radians(keys.getLast());
        int end = 1;
        while (keys.get(end).time < time) end++;
        Key start = keys.get(end-1), finish = keys.get(end);
        float length = finish.time-start.time;
        float t = (time-start.time)/length, t2 = t*t, t3 = t2*t;
        float base = 3*t2-2*t3;
        var angles = new float[18];
        for (int i=0; i<angles.length; i++) {
            float from = start.angles[i], to = finish.angles[i];
            float slope = (to-from)/length;
            float m0 = end>1 ? tangent(keys.get(end-2),start,i,slope) : 0;
            float m1 = end<keys.size()-1 ? tangent(finish,keys.get(end+1),i,slope) : 0;
            float value = Mth.lerp(base,from,to)+length*((t3-2*t2+t)*m0+(t3-t2)*m1);
            angles[i] = Mth.clamp(value,Math.min(from,to),Math.max(from,to))*Mth.DEG_TO_RAD;
        }
        return angles;
    }

    private static float tangent(Key start, Key end, int joint, float adjacent) {
        float slope = (end.angles[joint]-start.angles[joint])/(end.time-start.time);
        return slope*adjacent > 0 ? 2*slope*adjacent/(slope+adjacent) : 0;
    }

    private static float[] radians(Key key) {
        float[] result = key.angles.clone();
        for (int i=0; i<result.length; i++) result[i] *= Mth.DEG_TO_RAD;
        return result;
    }

    public static float weight(float ticks, int windup, int recovery) {
        if (ticks < 0) return 0;
        float in = windup == 0 ? 1 : Mth.clamp(ticks / Math.min(4,windup),0,1);
        float out = Mth.clamp((windup+recovery-ticks) / Math.min(8,recovery),0,1);
        float x = Math.min(in,out);
        return x*x*(3-2*x);
    }

    public static float blend(Skills.Skill skill, float ticks) {
        int release=skill.windup();
        // The active sweep includes the segment into the release frame as well as follow-through.
        if (CloseCombat.isAttack(skill) && ticks>=release-1 && ticks<=release+3) return 1;
        return weight(ticks,release,skill.recovery());
    }

    public static float yaw(Vec3 from, Vec3 to, float fallback) {
        Vec3 delta=to.subtract(from);
        return delta.horizontalDistanceSqr()<1e-7 ? fallback
                : (float) (Math.atan2(-delta.x,delta.z)*Mth.RAD_TO_DEG);
    }

    public static Vec3 release(HumNpc npc, Skills.Skill skill, float yaw) {
        return world(npc, skill, aimed(npc,skill,skill.windup()), yaw);
    }

    public static Vec3 world(HumNpc npc, Skills.Skill skill, float[] angles, float yaw) {
        if(npc.suspended())return WindowlessBuilding.console(npc,yaw);
        return modelToWorld(npc,anchor(style(skill).anchor,angles,npc.profile().slim()),yaw);
    }

    static Vec3 modelToWorld(HumNpc npc,Vec3 local,float yaw) {
        // Vanilla translates the baked humanoid down from its 24-pixel standing height.
        return npc.position().add(new Vec3(local.x,1.501-local.y,-local.z)
                .scale(npc.phaseScale()*npc.getScale()).yRot(-yaw*Mth.DEG_TO_RAD));
    }

    public static float[] aimed(HumNpc npc, Skills.Skill skill, float ticks) {
        float[] angles=sample(skill,ticks);
        float pitch=npc.castPitch()*Mth.DEG_TO_RAD;
        // Minecraft pitch is negative upward. Arm +Y (at rest) rotates towards -Z
        // when its X angle decreases. The head and the holding arm must agree.
        switch (style(skill).anchor) {
            case RIGHT, RIGHT_SHOULDER -> angles[0]+=pitch*.86f;
            case LEFT -> angles[3]+=pitch*.86f;
            case BETWEEN -> {angles[0]+=pitch*.64f;angles[3]+=pitch*.64f;}
            default -> {}
        }
        angles[6]=Mth.clamp(angles[6],-.18f,.18f);
        angles[8]=Mth.clamp(angles[8],-.024f,.024f);
        angles[9]=Mth.clamp(angles[9]+pitch*.73f,-1.22f,1.22f);
        return angles;
    }

    public static Vec3 anchor(Anchor anchor, float[] angles, boolean slim) {
        return switch (anchor) {
            case RIGHT -> hand(angles,slim,true);
            case LEFT -> hand(angles,slim,false);
            case BETWEEN -> hand(angles,slim,true).lerp(hand(angles,slim,false),.5);
            case CHEST -> upperBody(angles,new Vec3(0,.34,-.18));
            case GROUND -> new Vec3(0,1.47,-.48);
            case EYES -> face(angles,-.24);
            case MOUTH -> face(angles,-.115);
            case BACK -> upperBody(angles,new Vec3(0,.21,.22));
            case THIRD_HAND -> upperBody(angles,new Vec3(-.38,-.21,-.22));
            case RIGHT_SHOULDER -> shoulder(angles,slim,true).add(0,.06,-.10);
        };
    }

    private static Vec3 face(float[] angles, double height) {
        var point = new Vector3f(0,(float)height,-.26f);
        new Quaternionf().rotationZYX(angles[11],angles[10],angles[9]).transform(point);
        return upperBody(angles,Vec3.ZERO).add(point.x,point.y,point.z);
    }

    private static Vec3 hand(float[] angles, boolean slim, boolean right) {
        return armPoint(angles,slim,right,9.4f,-.35f);
    }

    static Vec3 armPoint(float[] angles,boolean slim,boolean right,float along,float depth) {
        int i = right ? 0 : 3;
        var tip = new Vector3f(0,along/16f,depth/16f);
        new Quaternionf().rotationZYX(angles[i+2],angles[i+1],angles[i]).transform(tip);
        return shoulder(angles,slim,right).add(tip.x,tip.y,tip.z);
    }

    public static Vec3 shoulder(float[] angles, boolean slim, boolean right) {
        return upperBody(angles,new Vec3((right?-5:5)/16.0,(slim?2.5:2)/16.0,0));
    }

    public static Vec3 upperBody(float[] angles, Vec3 modelPoint) {
        var point=new Vector3f((float)modelPoint.x,(float)modelPoint.y-.72f,(float)modelPoint.z);
        new Quaternionf().rotationZYX(angles[8],angles[7],angles[6]).transform(point);
        return new Vec3(point.x,point.y+.72,point.z);
    }

    private static Clip clip(String id) {
        return CLIPS.computeIfAbsent(id,key->{
            try (var in=CastMotion.class.getResourceAsStream("/assets/acabiohum/motions/"+key+".json")) {
                if (in==null) throw new IllegalStateException("Missing cast motion "+key);
                var clip=GSON.fromJson(new InputStreamReader(in,StandardCharsets.UTF_8),Clip.class);
                if (clip.style==null || clip.keys==null || clip.keys.size()<2) {
                    throw new IllegalStateException("Invalid cast motion "+key);
                }
                return clip;
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
    }

    private CastMotion() {}
}
