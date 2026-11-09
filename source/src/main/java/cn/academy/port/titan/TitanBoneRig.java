package cn.academy.port.titan;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Vector3d;

/**
 * Evaluates the bundled GeckoLib geometry and the same keyframes used by the
 * renderer. No client classes or trust in client-sent hit positions are needed.
 * Matrices mirror GeckoLib 4.7: X-mirrored geometry, Z/Y/X rotations, then scale.
 */
public final class TitanBoneRig {
    private static final Map<String,Model> CACHE=new ConcurrentHashMap<>();
    private TitanBoneRig() {}
    public record Pose(Vec3 position,Vec3 rotation,Vec3 scale) {}
    public record Shape(String bone,Vec3 root,Vector3d[] vertices,AABB bounds) {}
    private record Cube(Vector3d[] restVertices) {}
    private record Bone(String name,String parent,Vec3 pivot,Vec3 baseRotation,List<Cube> cubes) {}
    private record Key(double time,Vec3 value) {}
    private record Channels(Track position,Track rotation,Track scale) {}
    private record Clip(Map<String,Channels> channels) {}
    private record Model(Map<String,Bone> bones,Clip a,Clip b) {}

    public static boolean supported(String id,boolean variantB) {
        return switch(id) {
            case "lava_rex","frost_bastion","grove_titan","tidal_sail" -> variantB;
            case "dune_scorpion" -> !variantB;
            case "ironback" -> true;
            default -> false;
        };
    }
    public static boolean turnsTailToTarget(String id,boolean variantB) {
        return (variantB && switch(id) {
            case "lava_rex","frost_bastion","grove_titan","tidal_sail" -> true;
            default -> false;
        }) || (id.equals("dune_scorpion") && !variantB);
    }
    public static List<String> collisionBones(String id,boolean variantB) {
        if(!supported(id,variantB))return List.of();
        if(id.equals("ironback")) {
            var names=new ArrayList<String>();
            for(int j:variantB?new int[]{5,6,7}:new int[]{6})
                for(int i=0;i<4;i++)names.add("tendril_"+j+"_"+i);
            return List.copyOf(names);
        }
        int count=switch(id){case "lava_rex" -> 6;case "frost_bastion" -> 3;
            case "grove_titan","dune_scorpion" -> 5;case "tidal_sail" -> 7;default -> 0;};
        var names=new ArrayList<String>();for(int i=0;i<count;i++)names.add("tail_"+i);return List.copyOf(names);
    }
    /** Raw JSON-space offsets and degrees. X/Y rotation signs are applied by the renderer. */
    public static Map<String,Pose> poses(String id,boolean variantB,double seconds) {
        Model m=model(id);Clip clip=variantB?m.b():m.a();
        Map<String,Pose> result=new LinkedHashMap<>();
        // A colliding appendage and all its parents must use the same pose. All
        // animated bones are included so the whole pose shares one server clock.
        for(Bone bone:m.bones().values()) {
            Channels c=clip.channels().get(bone.name());
            Vec3 position=c==null?Vec3.ZERO:c.position().at(seconds,Vec3.ZERO);
            Vec3 rotation=bone.baseRotation().add(c==null?Vec3.ZERO:c.rotation().at(seconds,Vec3.ZERO));
            Vec3 scale=c==null?new Vec3(1,1,1):c.scale().at(seconds,new Vec3(1,1,1));
            result.put(bone.name(),new Pose(position,rotation,scale));
        }
        return result;
    }
    public static List<Shape> shapes(String id,boolean variantB,double seconds,Vec3 entityPosition,float bodyYaw,float entityScale) {
        if(!supported(id,variantB))return List.of();
        Model model=model(id);Map<String,Pose> pose=poses(id,variantB,seconds);
        Matrix4d root=new Matrix4d().translate(entityPosition.x,entityPosition.y,entityPosition.z)
            .scale(entityScale).rotateY(Math.toRadians(180-bodyYaw)).translate(0,.01,0);
        Map<String,Matrix4d> matrices=new HashMap<>();var shapes=new ArrayList<Shape>();
        for(String name:collisionBones(id,variantB)) {
            Bone bone=model.bones().get(name);
            if(bone==null)throw new IllegalStateException("Missing collision bone "+id+"/"+name);
            Matrix4d matrix=boneMatrix(model,bone,pose,root,matrices);
            String rootName=name.startsWith("tail_")?"tail_0":name.substring(0,name.lastIndexOf('_')+1)+"0";
            Bone limbRoot=model.bones().get(rootName);
            Matrix4d rootMatrix=boneMatrix(model,limbRoot,pose,root,matrices);
            Vector3d p=new Vector3d(limbRoot.pivot().x,limbRoot.pivot().y,limbRoot.pivot().z);
            rootMatrix.transformPosition(p);Vec3 anchor=new Vec3(p.x,p.y,p.z);
            for(Cube cube:bone.cubes()) {
                Vector3d[] vs=new Vector3d[8];
                double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
                for(int i=0;i<8;i++) {
                    Vector3d v=matrix.transformPosition(new Vector3d(cube.restVertices()[i]));vs[i]=v;
                    minX=Math.min(minX,v.x);minY=Math.min(minY,v.y);minZ=Math.min(minZ,v.z);
                    maxX=Math.max(maxX,v.x);maxY=Math.max(maxY,v.y);maxZ=Math.max(maxZ,v.z);
                }
                shapes.add(new Shape(name,anchor,vs,new AABB(minX,minY,minZ,maxX,maxY,maxZ)));
            }
        }
        return List.copyOf(shapes);
    }
    public static Vec3 worldPivot(String id,boolean variantB,double seconds,Vec3 entityPosition,float bodyYaw,float entityScale,String boneName) {
        return worldBonePoint(id,variantB,seconds,entityPosition,bodyYaw,entityScale,boneName,Vec3.ZERO);
    }
    public static Vec3 worldBonePoint(String id,boolean variantB,double seconds,Vec3 entityPosition,float bodyYaw,float entityScale,String boneName,Vec3 offset) {
        Model model=model(id);Bone bone=model.bones().get(boneName);
        if(bone==null)throw new IllegalArgumentException("Unknown bone "+id+"/"+boneName);
        Matrix4d root=new Matrix4d().translate(entityPosition.x,entityPosition.y,entityPosition.z)
            .scale(entityScale).rotateY(Math.toRadians(180-bodyYaw)).translate(0,.01,0);
        Matrix4d matrix=boneMatrix(model,bone,poses(id,variantB,seconds),root,new HashMap<>());
        Vector3d p=matrix.transformPosition(new Vector3d(bone.pivot().x+offset.x,bone.pivot().y+offset.y,bone.pivot().z+offset.z));
        return new Vec3(p.x,p.y,p.z);
    }
    /** Pivot and three one-block basis endpoints, useful for renderer alignment tests. */
    public static List<Vec3> worldBoneFrame(String id,boolean variantB,double seconds,Vec3 entityPosition,float bodyYaw,float entityScale,String boneName) {
        Model model=model(id);Bone bone=model.bones().get(boneName);
        if(bone==null)throw new IllegalArgumentException("Unknown bone "+id+"/"+boneName);
        Matrix4d root=new Matrix4d().translate(entityPosition.x,entityPosition.y,entityPosition.z)
            .scale(entityScale).rotateY(Math.toRadians(180-bodyYaw)).translate(0,.01,0);
        Matrix4d matrix=boneMatrix(model,bone,poses(id,variantB,seconds),root,new HashMap<>());
        var result=new ArrayList<Vec3>();
        for(Vec3 offset:List.of(Vec3.ZERO,new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1))) {
            Vector3d p=matrix.transformPosition(new Vector3d(bone.pivot().x+offset.x,bone.pivot().y+offset.y,bone.pivot().z+offset.z));
            result.add(new Vec3(p.x,p.y,p.z));
        }
        return List.copyOf(result);
    }
    private static Matrix4d boneMatrix(Model model,Bone bone,Map<String,Pose> poses,Matrix4d root,Map<String,Matrix4d> cache) {
        Matrix4d found=cache.get(bone.name());if(found!=null)return found;
        Matrix4d parent=bone.parent()==null?new Matrix4d(root):new Matrix4d(boneMatrix(model,model.bones().get(bone.parent()),poses,root,cache));
        Pose p=poses.get(bone.name());Vec3 o=p.position(),r=p.rotation(),s=p.scale(),q=bone.pivot();
        parent.translate(-o.x/16,o.y/16,o.z/16).translate(q.x,q.y,q.z)
            .rotateZ(Math.toRadians(r.z)).rotateY(Math.toRadians(-r.y)).rotateX(Math.toRadians(-r.x))
            .scale(s.x,s.y,s.z).translate(-q.x,-q.y,-q.z);
        cache.put(bone.name(),parent);return parent;
    }
    private static Model model(String id){return CACHE.computeIfAbsent(id,TitanBoneRig::load);}
    private static JsonObject json(String path) {
        try(InputStream stream=TitanBoneRig.class.getResourceAsStream("/assets/academy/"+path)) {
            if(stream==null)throw new IllegalStateException("Missing rig resource "+path);
            return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        } catch(Exception e){throw new IllegalStateException("Cannot load rig "+path,e);}
    }
    private static Model load(String id) {
        JsonObject root=json("geo/"+id+".geo.json").getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        Map<String,Bone> bones=new LinkedHashMap<>();
        for(JsonElement item:root.getAsJsonArray("bones")) {
            JsonObject b=item.getAsJsonObject();String name=b.get("name").getAsString();
            String parent=b.has("parent")?b.get("parent").getAsString():null;
            Vec3 pivot=vec(b.get("pivot"),Vec3.ZERO);pivot=new Vec3(-pivot.x/16,pivot.y/16,pivot.z/16);
            Vec3 rotation=vec(b.get("rotation"),Vec3.ZERO);List<Cube> cubes=new ArrayList<>();
            if(b.has("cubes"))for(JsonElement element:b.getAsJsonArray("cubes"))cubes.add(cube(element.getAsJsonObject()));
            bones.put(name,new Bone(name,parent,pivot,rotation,List.copyOf(cubes)));
        }
        JsonObject animations=json("animations/"+id+".animation.json").getAsJsonObject("animations");
        return new Model(Map.copyOf(bones),clip(animations.getAsJsonObject("animation."+id+".attack_a")),
            clip(animations.getAsJsonObject("animation."+id+".attack_b")));
    }
    private static Cube cube(JsonObject c) {
        Vec3 origin=vec(c.get("origin"),Vec3.ZERO),size=vec(c.get("size"),Vec3.ZERO);
        Vec3 pivot=vec(c.get("pivot"),Vec3.ZERO),rot=vec(c.get("rotation"),Vec3.ZERO);
        double x=-(origin.x+size.x)/16,y=origin.y/16,z=origin.z/16;
        double sx=size.x/16,sy=size.y/16,sz=size.z/16;
        double inflate=c.has("inflate")?c.get("inflate").getAsDouble()/16:0;
        x-=inflate;y-=inflate;z-=inflate;sx+=2*inflate;sy+=2*inflate;sz+=2*inflate;
        Vec3 q=new Vec3(-pivot.x/16,pivot.y/16,pivot.z/16);
        Matrix4d transform=new Matrix4d().translate(q.x,q.y,q.z)
            .rotateZ(Math.toRadians(rot.z)).rotateY(Math.toRadians(-rot.y)).rotateX(Math.toRadians(-rot.x))
            .translate(-q.x,-q.y,-q.z);
        Vector3d[] vertices=new Vector3d[8];
        for(int i=0;i<8;i++)vertices[i]=transform.transformPosition(new Vector3d(x+((i&4)!=0?sx:0),y+((i&2)!=0?sy:0),z+((i&1)!=0?sz:0)));
        return new Cube(vertices);
    }
    private static Clip clip(JsonObject c) {
        Map<String,Channels> out=new HashMap<>();
        for(var entry:c.getAsJsonObject("bones").entrySet()) {
            JsonObject x=entry.getValue().getAsJsonObject();
            out.put(entry.getKey(),new Channels(track(x.get("position")),track(x.get("rotation")),track(x.get("scale"))));
        }
        return new Clip(Map.copyOf(out));
    }
    private static Track track(JsonElement element) {
        if(element==null)return new Track(new double[0],new Vec3[0]);
        if(element.isJsonArray())return new Track(new double[]{0},new Vec3[]{vec(element,Vec3.ZERO)});
        var keys=new ArrayList<Key>();
        for(var e:element.getAsJsonObject().entrySet())keys.add(new Key(Double.parseDouble(e.getKey()),vec(e.getValue(),Vec3.ZERO)));
        keys.sort(java.util.Comparator.comparingDouble(Key::time));double[] times=new double[keys.size()];Vec3[] values=new Vec3[keys.size()];
        for(int i=0;i<keys.size();i++){times[i]=keys.get(i).time();values[i]=keys.get(i).value();}
        return new Track(times,values);
    }
    private static Vec3 vec(JsonElement e,Vec3 fallback) {
        if(e==null)return fallback;JsonArray a=e.getAsJsonArray();
        return new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());
    }
    private record Track(double[] times,Vec3[] values) {
        Vec3 at(double time,Vec3 fallback) {
            if(times.length==0)return fallback;
            int at=Arrays.binarySearch(times,time);
            if(at>=0)return values[at];at=-at-1;
            if(at==0)return values[0];if(at==times.length)return values[times.length-1];
            double f=(time-times[at-1])/(times[at]-times[at-1]);return values[at-1].lerp(values[at],f);
        }
    }
}
