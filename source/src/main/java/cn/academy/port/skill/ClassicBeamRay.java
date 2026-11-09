/* Literal RangedRayDamage/Plotter behavior, Copyright Lambda Innovation2013-2016, GPLv3; see NOTICE.
 * Float lookup/geometry bridge from Minecraft1.7 and LambdaLib1.2.3 (MIT). */
package cn.academy.port.skill;
import java.util.*;
/** World-free faithful wide-beam algorithm; deliberately separate from the older Railgun adapter. */
public final class ClassicBeamRay {
    public static final double STEP=.9; public static final int MAX_INCREMENT=50;
    private static final float[] SINES=new float[65536];
    static {for(int i=0;i<SINES.length;i++)SINES[i]=(float)Math.sin(i*Math.PI*2/65536);}
    private ClassicBeamRay(){}
    public record Vec(double x,double y,double z){
        public Vec add(Vec v){return new Vec(x+v.x,y+v.y,z+v.z);}public Vec subtract(Vec v){return new Vec(x-v.x,y-v.y,z-v.z);}public Vec scale(double s){return new Vec(x*s,y*s,z*s);}public double square(){return x*x+y*y+z*z;}public double length(){return Math.sqrt(square());}public Vec normalize(){return scale(1/length());}public Vec cross(Vec v){return new Vec(y*v.z-z*v.y,z*v.x-x*v.z,x*v.y-y*v.x);}
    }
    public record Cell(int x,int y,int z){}
    public record Box(double minX,double minY,double minZ,double maxX,double maxY,double maxZ){}
    public record Basis(Vec first,Vec second){}
    public record Target(Object identity,Vec feet){}
    public record Block(float hardness,boolean air){}
    public interface Random {double nextDouble();float nextFloat();int nextInt(int bound);}
    public interface World {
        List<Target> targets(Box box);/** true=ordinary attack, false=reflect and stop */ boolean attack(Target target,float damage);
        boolean canBreak();Block block(Cell cell);boolean denied(Cell cell);void destroy(Cell cell,float dropChance,boolean soundProbe,Random random);
    }
    public record Result(int targets,int lines,int blockProbes,double maximumDistanceSquared){}
    public static Vec direction(float headYaw,float pitch){float a=headYaw/180F*(float)Math.PI,b=pitch/180F*(float)Math.PI;return new Vec(-sin(a)*cos(b),-sin(b),cos(a)*cos(b)).normalize();}
    private static float sin(float f){return SINES[(int)(f*10430.378F)&65535];}private static float cos(float f){return SINES[(int)(f*10430.378F+16384F)&65535];}
    public static Basis basis(Vec slope){float yaw=-(float)Math.PI*.5F-(-(float)Math.atan2(slope.x,slope.z));float pitch=-(float)Math.atan2(slope.y,Math.sqrt(slope.x*slope.x+slope.z*slope.z));
        // 1.7 Vec3 rotates with lookup-table Float trig, not modern Vec3.xRot/yRot Double trig.
        return new Basis(new Vec(sin(yaw),0,cos(yaw)),new Vec((double)sin(pitch)*cos(yaw),cos(pitch),-(double)sin(pitch)*sin(yaw)));
    }
    public static Box bounds(Vec start,Vec slope,Basis basis,double radius){double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;for(int s:new int[]{-1,1})for(int t:new int[]{-1,1})for(int d:new int[]{0,MAX_INCREMENT}){Vec v=start.add(basis.first.scale(s*radius).add(basis.second.scale(t*radius))).add(slope.scale(d));minX=Math.min(minX,v.x);minY=Math.min(minY,v.y);minZ=Math.min(minZ,v.z);maxX=Math.max(maxX,v.x);maxY=Math.max(maxY,v.y);maxZ=Math.max(maxZ,v.z);}return new Box(minX,minY,minZ,maxX,maxY,maxZ);}
    /** Source int[] has identity equality: geometric duplicates are NOT deduplicated. */
    public static Set<int[]> startingCells(Vec start,Basis basis,double radius,Random random){Set<int[]> result=new HashSet<>();for(double s=-radius;s<=radius;s+=STEP)for(double t=-radius;t<=radius;t+=STEP){double rr=radius*(.9+random.nextDouble()*(1.1-.9));if(s*s+t*t>rr*rr)continue;Vec p=start.add(basis.first.scale(s).add(basis.second.scale(t)));int[] cell={(int)p.x,(int)p.y,(int)p.z};if(result.contains(cell))continue;result.add(cell);}return result;}
    public static float attackDamage(float base,Vec start,Vec slope,Vec target){float dist=Math.min(MAX_INCREMENT,(float)target.subtract(start).cross(slope).length());return base*(1+(.2F-1)*(dist/MAX_INCREMENT));}
    public static Result perform(World world,Random random,Vec playerFeet,Vec eye,Vec direction,float radius,float energy,float damage){Vec slope=direction.normalize(),start=eye.add(direction.scale(.1));Basis basis=basis(slope);double maxDistance=Double.MAX_VALUE;int attacked=0;
        var targets=new ArrayList<Target>();for(var target:world.targets(bounds(start,slope,basis,radius)))if(target.feet.subtract(start).cross(slope).length()<radius*1.2)targets.add(target);targets.sort(Comparator.comparingDouble(target->target.feet.subtract(playerFeet).square()));
        for(var target:targets){++attacked;if(!world.attack(target,attackDamage(damage,start,slope,target.feet))){maxDistance=target.feet.subtract(playerFeet).square();break;}}
        if(!world.canBreak())return new Result(attacked,0,0,maxDistance);var starts=startingCells(start,basis,radius,random);float average=energy/starts.size();int probes=0;
        for(int[] coords:starts){float remaining=average*(.95F+random.nextFloat()*(1.05F-.95F));Plotter plotter=new Plotter(coords[0],coords[1],coords[2],slope.x,slope.y,slope.z);int increments=0;
            for(int i=0;i<=MAX_INCREMENT&&remaining>0;++i){++increments;int[] next=plotter.next();int dx=coords[0]-next[0],dy=coords[1]-next[1],dz=coords[2]-next[2];int squared=dx*dx+dy*dy+dz*dz;if(squared>maxDistance)break;boolean sound=increments<20;Cell hit=new Cell(next[0],next[1],next[2]);++probes;remaining=destroy(world,random,remaining,hit,sound);
                if(random.nextDouble()<.05){int side=random.nextInt(6);int[][] sides={{0,-1,0},{0,1,0},{0,0,-1},{0,0,1},{-1,0,0},{1,0,0}};++probes;remaining=destroy(world,random,remaining,new Cell(hit.x+sides[side][0],hit.y+sides[side][1],hit.z+sides[side][2]),sound);}
            }
        }return new Result(attacked,starts.size(),probes,maxDistance);
    }
    private static float destroy(World world,Random random,float energy,Cell cell,boolean sound){Block block=world.block(cell);float hardness=block.hardness;if(hardness<0)hardness=233333;if(!world.denied(cell)&&energy>=hardness){if(!block.air)world.destroy(cell,.05F,sound,random);return energy-hardness;}return 0;}
}
