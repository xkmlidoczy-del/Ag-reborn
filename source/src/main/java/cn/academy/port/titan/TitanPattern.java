package cn.academy.port.titan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A server sample of one skill. The very same volumes travel to the renderer. */
public final class TitanPattern {
    public enum Move {
        SPORES, LEAP, SCISSORS, SHARDS, ROOTS, PODS, STINGS, SAND, VEIL, STARS, VORTEX, JET,
        FIREWAVE, METEOR, ICEWAVE, ICICLES, SAW, GRID, DRAIN, RIFTS, LANCE, STORM, COLLAPSE, NOVA
    }
    public enum Kind { STRIP, RAY, ARC, TRAIL }
    public record Zone(Kind kind, Vec3 a, Vec3 b, double outer, double inner, double angle, double height) {
        /** Angles are horizontal half angles, height is a vertical half extent. */
        public boolean touches(AABB box) {
            if(kind==Kind.TRAIL)return false;
            Vec3 center=box.getCenter(),axis=b.subtract(a),d=center.subtract(a);
            double padding=Math.hypot(box.getXsize(),box.getZsize())*.5;
            if(kind==Kind.ARC) {
                if(box.maxY<a.y-height || box.minY>a.y+height)return false;
                double r=Math.hypot(d.x,d.z);
                if(r>outer+padding || r+padding<inner)return false;
                if(angle>=179.999 || r<=padding)return true;
                double error=Math.asin(Math.min(1,padding/r));
                return d.x*axis.x+d.z*axis.z>=r*Math.cos(Math.min(Math.PI,Math.toRadians(angle)+error));
            }
            double u=kind==Kind.RAY?d.dot(axis)/Math.max(1e-8,axis.lengthSqr())
                    :(d.x*axis.x+d.z*axis.z)/Math.max(1e-8,axis.horizontalDistanceSqr());
            Vec3 closest=a.add(axis.scale(Mth.clamp(u,0,1)));
            double dx=Math.max(0,Math.max(box.minX-closest.x,closest.x-box.maxX));
            double dy=Math.max(0,Math.max(box.minY-closest.y,closest.y-box.maxY));
            double dz=Math.max(0,Math.max(box.minZ-closest.z,closest.z-box.maxZ));
            return (kind==Kind.RAY?dx*dx+dy*dy+dz*dz:dx*dx+dz*dz)<=outer*outer && (kind==Kind.RAY||dy<=height);
        }
        public AABB bounds() {
            if(kind==Kind.ARC)return new AABB(a,a).inflate(outer,height,outer);
            return new AABB(a,b).inflate(outer,kind==Kind.RAY?outer:height,outer);
        }
    }
    private TitanPattern() {}
    public static Move move(String id,boolean second) {
        return switch(id) {
            case "myco_frog" -> second?Move.LEAP:Move.SPORES;
            case "crystal_mantis" -> second?Move.SHARDS:Move.SCISSORS;
            case "grove_titan" -> second?Move.PODS:Move.ROOTS;
            case "dune_scorpion" -> second?Move.SAND:Move.STINGS;
            case "astral_moth" -> second?Move.STARS:Move.VEIL;
            case "tidal_sail" -> second?Move.JET:Move.VORTEX;
            case "lava_rex" -> second?Move.METEOR:Move.FIREWAVE;
            case "frost_bastion" -> second?Move.ICICLES:Move.ICEWAVE;
            case "clockwork_rex" -> second?Move.GRID:Move.SAW;
            case "ossuary_dragon" -> second?Move.RIFTS:Move.DRAIN;
            case "storm_drake" -> second?Move.STORM:Move.LANCE;
            case "ironback" -> second?Move.NOVA:Move.COLLAPSE;
            default -> throw new IllegalArgumentException(id);
        };
    }
    public static boolean sequence(Move m) {
        return switch(m){case PODS,STINGS,STARS,ICICLES,RIFTS,STORM,GRID -> true;default -> false;};
    }
    public static boolean travelling(Move m) {
        return switch(m){case FIREWAVE,SAND,ICEWAVE,VORTEX,COLLAPSE,SAW -> true;default -> false;};
    }
    public static int duration(Move m){return sequence(m)?24:travelling(m)?20:0;}
    public static boolean active(Move m,int elapsed) {
        return elapsed>=0 && elapsed<=duration(m) && (sequence(m)?elapsed%12==0:travelling(m)?elapsed%2==0:elapsed==0);
    }
    public static Vec3 surface(ServerLevel level,Vec3 p) {
        BlockPos pos=BlockPos.containing(p);
        if(!level.hasChunkAt(pos))return p;
        int h=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,pos.getX(),pos.getZ());
        return Math.abs(h-p.y)<=48?new Vec3(p.x,h+.14,p.z):p;
    }
    private static Vec3 turn(Vec3 f,double degrees){return f.yRot((float)Math.toRadians(degrees));}
    private static Zone arc(Vec3 center,Vec3 dir,double inner,double outer,double halfAngle,double halfY) {
        return new Zone(Kind.ARC,center,center.add(dir),outer,inner,halfAngle,halfY);
    }
    private static Zone strip(Vec3 a,Vec3 b,double r,double h){return new Zone(Kind.STRIP,a,b,r,0,0,h);}
    private static void trail(List<Zone> out,Vec3 a,Vec3 b,double rise) {
        Vec3 mid=a.lerp(b,.5).add(0,rise,0);
        out.add(new Zone(Kind.TRAIL,a,mid,.25,0,0,0));out.add(new Zone(Kind.TRAIL,mid,b,.25,0,0,0));
    }
    private static void ray(List<Zone> out,ServerLevel w,TitanEntity e,Vec3 a,Vec3 b,double reach,double width) {
        Vec3 dir=b.subtract(a).normalize();
        Vec3 end=TitanCombat.clippedBeamEnd(w,e,a,a.add(dir.scale(reach)),reach);
        out.add(new Zone(Kind.RAY,a,end,width,0,0,width));
    }
    /** elapsed < 0 is the warning, showing all locked lanes and impact markings. */
    public static List<Zone> sample(ServerLevel world,TitanEntity titan,boolean second,Vec3 base,Vec3 aim,int age,int elapsed) {
        var ability=second?titan.profile().second():titan.profile().first();
        Move move=move(titan.species(),second); var zones=new ArrayList<Zone>();
        boolean warning=elapsed<0;
        Vec3 flat=aim.subtract(base).multiply(1,0,1);
        Vec3 f=flat.horizontalDistanceSqr()<.001?Vec3.directionFromRotation(0,titan.yBodyRot):flat.normalize();
        Vec3 right=turn(f,90);
        String[] names=move==Move.GRID?new String[]{"emit","foot_0_-1","foot_0_1","foot_2_-1","foot_2_1"}:
            move==Move.SCISSORS||move==Move.SAW?new String[]{"emit","emit_left"}:new String[]{"emit"};
        if(move==Move.SPORES)names=new String[]{"spore_vent"};
        Map<String,Vec3> sockets=TitanBoneRig.skillSockets(titan,second,age/20.,names);
        Vec3 muzzle=sockets.get(move==Move.SPORES?"spore_vent":"emit"),center=surface(world,base),target=surface(world,aim);
        double r=ability.radius(),reach=ability.range();
        int first=warning?0:Math.max(0,elapsed/12),last=warning?2:first;
        switch(move) {
            case SPORES,DRAIN -> {
                Vec3 source=surface(world,new Vec3(muzzle.x,aim.y,muzzle.z));
                zones.add(arc(source,f,0,r,move==Move.SPORES?46:32,6));
                trail(zones,muzzle,source.add(f.scale(r*.7)),0);
            }
            case LEAP -> zones.add(arc(warning?target:surface(world,titan.position()),f,0,Math.min(9,r),180,3));
            case SCISSORS -> {
                for(int side:new int[]{-1,1}) {
                    Vec3 source=side<0?muzzle:sockets.get("emit_left");
                    ray(zones,world,titan,source,aim.add(0,1,0),Math.min(reach,source.distanceTo(aim)+15),1.5);
                }
            }
            case SHARDS -> {
                for(int i=0;i<6;i++)zones.add(arc(center,turn(f,60*i),4,r,15,5));
            }
            case ROOTS -> {
                for(int i=-1;i<=1;i++) {
                    Vec3 lane=turn(f,24*i);
                    zones.add(strip(surface(world,center.add(lane.scale(4))),surface(world,center.add(lane.scale(r))),1.65,3));
                }
            }
            case PODS,STINGS,STARS,STORM -> {
                for(int i=first;i<=last;i++) {
                    // First mark is centered on the locked victim. Further strikes advance
                    // along the tail or alternate across the player's route.
                    Vec3 p=target;
                    if(i>0)p=surface(world,target.add(right.scale((i==1?1:-1)*(move==Move.STINGS?3:7))).add(f.scale(move==Move.STINGS?i*6:4)));
                    double radius=switch(move){case STINGS -> 3;case STARS -> 4.2;case STORM -> 4.8;default -> 5.4;};
                    zones.add(arc(p,f,0,radius,180,move==Move.STORM?14:4));
                    if(move==Move.PODS||move==Move.STINGS)trail(zones,muzzle,p,move==Move.PODS?12:5);
                    else trail(zones,p.add(0,14+4*i,0),p,0);
                }
            }
            case VEIL -> {
                for(int side:new int[]{-1,1})zones.add(arc(target.subtract(f.scale(r*.6)),turn(f,side*32),2,r,23,7));
            }
            case VORTEX,FIREWAVE,ICEWAVE,SAND,COLLAPSE -> {
                boolean water=move==Move.VORTEX;
                Vec3 pivot=water?new Vec3(base.x,aim.y,base.z):center;
                boolean inward=move==Move.COLLAPSE;
                double progress=warning?0:Mth.clamp(elapsed/20.,0,1);
                double radius=4+(r-4)*(inward?1-progress:progress);
                double span=move==Move.ICEWAVE?41:move==Move.SAND?135:180;
                for(int i=0;i<(move==Move.ICEWAVE?3:1);i++) {
                    Vec3 axis=turn(f,move==Move.ICEWAVE?i*120:0);
                    zones.add(arc(pivot,axis,Math.max(0,radius-1.7),radius+1.7,span,water?2.4:3));
                    if(warning)zones.add(arc(pivot,axis,inward?3:r-1,inward?5:r+1,span,water?2.4:3));
                }
            }
            case JET,LANCE -> ray(zones,world,titan,muzzle,aim.add(0,1,0),Math.min(reach,muzzle.distanceTo(aim)+10),move==Move.JET?1.9:1.35);
            case METEOR,NOVA -> {
                zones.add(arc(target,f,0,move==Move.METEOR?7:9,180,5));
                trail(zones,muzzle,target,move==Move.METEOR?19:2);
            }
            case ICICLES,RIFTS -> {
                for(int i=first;i<=last;i++) {
                    Vec3 mid=target.add(f.scale((i==0?0:i==1?-1:1)*7));
                    Vec3 axis=move==Move.ICICLES?right:turn(right,35*(i%2==0?1:-1));
                    zones.add(strip(surface(world,mid.subtract(axis.scale(r*.82))),surface(world,mid.add(axis.scale(r*.82))),move==Move.ICICLES?2.1:1.8,move==Move.ICICLES?12:3));
                }
            }
            case SAW -> {
                for(int i=0;i<(warning?3:1);i++) {
                    double t=warning?i*.5:elapsed/20.;
                    Vec3 source=t<.5?muzzle:sockets.get("emit_left");
                    Vec3 end=target.add(right.scale((t-.5)*32));
                    ray(zones,world,titan,source,end.add(0,1,0),Math.min(reach,source.distanceTo(target)+10),1.7);
                }
            }
            case GRID -> {
                var feet=List.of(sockets.get("foot_0_-1"),sockets.get("foot_2_-1"),sockets.get("foot_2_1"),sockets.get("foot_0_1"));
                for(int i=first;i<=last;i++)zones.add(strip(surface(world,feet.get(i)),surface(world,feet.get(i+1)),2.8,4));
                // The fourth edge stays open throughout the discharge.
            }
        }
        if(warning){
            String[] order=switch(move){
                case STARS -> new String[]{"ocellus_-1","ocellus_1","primary_tip_1"};
                case FIREWAVE,METEOR -> new String[]{"vent_-1","vent_1","neck"};
                case LANCE -> new String[]{"crown_-1","crown_1","emit"};
                case NOVA,COLLAPSE -> new String[]{"outer_ring","inner_ring","core"};
                case SHARDS -> new String[]{"crest_-1","crest_1"};
                default -> new String[0];
            };
            if(order.length>0){
                int index=Math.min(order.length-1,age*order.length/ability.windupTicks());
                Vec3 lamp=TitanBoneRig.skillSockets(titan,second,age/20.,order[index]).get(order[index]);
                double size=1.2+1.2*age/ability.windupTicks();
                for(Vec3 d:List.of(new Vec3(size,0,0),new Vec3(0,size,0),new Vec3(0,0,size)))
                    zones.add(new Zone(Kind.TRAIL,lamp.subtract(d),lamp.add(d),.2,0,0,0));
            }
        }
        return List.copyOf(zones);
    }
}
