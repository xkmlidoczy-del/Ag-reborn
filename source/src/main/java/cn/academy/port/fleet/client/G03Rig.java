package cn.academy.port.fleet.client;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Versioned node hierarchy and sampled Blender actions. No ammunition mutations. */
public final class G03Rig {
 public record Node(String name,int parent,Vector3f origin) {}
 public static final class Pose {
  public final Vector3f pos=new Vector3f();public final Quaternionf rot=new Quaternionf();
  public float visible=1,curl=.92f,trigger;
  Pose copy(){Pose p=new Pose();p.pos.set(pos);p.rot.set(rot);p.visible=visible;p.curl=curl;p.trigger=trigger;return p;}
 }
 public static final class Frame {
  public final Pose[] poses;
  Frame(int n){poses=new Pose[n];for(int i=0;i<n;i++)poses[i]=new Pose();}
  public Frame copy(){Frame f=new Frame(poses.length);for(int i=0;i<poses.length;i++)f.poses[i]=poses[i].copy();return f;}
 }
 private record Clip(float duration,float[][][] tracks) {}
 private static final Map<Integer,G03Rig> INSTANCES=new HashMap<>();
 public final Node[] nodes;private final Map<String,Integer> index;private final Map<String,Clip> clips;private final float rate;
 private G03Rig(JsonObject json){
  if(json.get("version").getAsInt()!=2)throw new IllegalArgumentException("G03 rig version");
  rate=json.get("sample_rate").getAsFloat();if(rate!=60)throw new IllegalArgumentException("G03 sample rate");
  JsonArray ns=json.getAsJsonArray("nodes");if(ns.size()>128||ns.isEmpty())throw new IllegalArgumentException("node count");
  index=new LinkedHashMap<>();for(int i=0;i<ns.size();i++){String n=ns.get(i).getAsJsonObject().get("name").getAsString();if(index.put(n,i)!=null)throw new IllegalArgumentException("duplicate node");}
  nodes=new Node[ns.size()];for(int i=0;i<nodes.length;i++){JsonObject n=ns.get(i).getAsJsonObject();int parent=n.get("parent").isJsonNull()?-1:index.getOrDefault(n.get("parent").getAsString(),-2);if(parent==i||parent>=ns.size()||parent< -1)throw new IllegalArgumentException("node order/cycle");JsonArray a=n.getAsJsonArray("origin");nodes[i]=new Node(n.get("name").getAsString(),parent,new Vector3f(f(a,0),f(a,1),f(a,2)));}
  for(int n=0;n<nodes.length;n++){int k=n;for(int depth=0;k>=0;depth++){if(depth>nodes.length)throw new IllegalArgumentException("cyclic hierarchy");k=nodes[k].parent();}}
  clips=new HashMap<>();for(var entry:json.getAsJsonObject("clips").entrySet()){
   JsonObject c=entry.getValue().getAsJsonObject();float duration=c.get("duration").getAsFloat();JsonObject ts=c.getAsJsonObject("tracks");float[][][] tracks=new float[nodes.length][][];
   for(int n=0;n<nodes.length;n++){JsonArray frames=ts.getAsJsonArray(nodes[n].name());if(frames==null||frames.size()!=Math.round(duration*rate)+1)throw new IllegalArgumentException("bad track samples");tracks[n]=new float[frames.size()][10];for(int k=0;k<frames.size();k++){JsonArray a=frames.get(k).getAsJsonArray();if(a.size()!=10)throw new IllegalArgumentException("bad pose");for(int j=0;j<10;j++)tracks[n][k][j]=f(a,j);}}
   clips.put(entry.getKey(),new Clip(duration,tracks));
  }
 }
 private static float f(JsonArray a,int i){float x=a.get(i).getAsFloat();if(!Float.isFinite(x)||Math.abs(x)>100)throw new IllegalArgumentException("nonfinite rig value");return x;}
 public static void clear(){INSTANCES.clear();}
 public static G03Rig get(){return get(2);}
 public static G03Rig fromJson(JsonObject json){return new G03Rig(json);}
 public static G03Rig get(int variant){return INSTANCES.computeIfAbsent(variant,G03Rig::load);}
 private static G03Rig load(int variant){
  String id=cn.academy.port.fleet.InteractionLab.GUN_IDS[variant];
  try(var in=Minecraft.getInstance().getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath("academy","rig/"+id+".json")).orElseThrow().open()){
   byte[] b=in.readNBytes(4*1024*1024+1);if(b.length>4*1024*1024)throw new IllegalArgumentException("rig size");return new G03Rig(JsonParser.parseString(new String(b,StandardCharsets.UTF_8)).getAsJsonObject());
  }catch(Exception e){throw new IllegalStateException("Cannot load authored rig "+id,e);}
 }
 public int id(String name){Integer v=index.get(name);if(v==null)throw new IllegalArgumentException("unknown G03 node "+name);return v;}
 public boolean has(String name){return index.containsKey(name);}
 public float duration(String name){return clips.get(name).duration();}
 public Frame clip(String name,double seconds){
  Clip c=clips.get(name);if(c==null)throw new IllegalArgumentException("missing G03 clip "+name);float time=(float)Math.max(0,Math.min(c.duration(),seconds));float at=time*rate;int a=Math.min((int)at,c.tracks()[0].length-1),b=Math.min(a+1,c.tracks()[0].length-1);float t=at-a;Frame out=new Frame(nodes.length);
  for(int n=0;n<nodes.length;n++){float[] x=c.tracks()[n][a],y=c.tracks()[n][b];Pose o=out.poses[n];o.pos.set(x[0],x[1],x[2]).lerp(new Vector3f(y[0],y[1],y[2]),t);o.rot.set(x[3],x[4],x[5],x[6]).normalize().slerp(new Quaternionf(y[3],y[4],y[5],y[6]).normalize(),t);o.visible=x[7];o.curl=x[8]+(y[8]-x[8])*t;o.trigger=x[9]+(y[9]-x[9])*t;}
  return out;
 }
 public Frame blend(Frame a,Frame b,float amount){float t=Math.max(0,Math.min(1,amount));Frame f=a.copy();for(int i=0;i<nodes.length;i++){Pose x=f.poses[i],y=b.poses[i];x.pos.lerp(y.pos,t);x.rot.slerp(y.rot,t);x.curl+=(y.curl-x.curl)*t;x.trigger+=(y.trigger-x.trigger)*t;x.visible=t<.5?a.poses[i].visible:y.visible;}return f;}
 public void additive(Frame base,String name,double seconds,float weight){Frame d=clip(name,seconds),zero=clip(name,0);for(int i=0;i<nodes.length;i++){Pose o=base.poses[i];o.pos.add(new Vector3f(d.poses[i].pos).sub(zero.poses[i].pos).mul(weight));Quaternionf delta=new Quaternionf(zero.poses[i].rot).invert().mul(d.poses[i].rot);o.rot.mul(new Quaternionf().slerp(delta,weight));o.trigger=Math.max(o.trigger,d.poses[i].trigger*weight);}}
 public Matrix4f world(Frame f,int n){Node node=nodes[n];Matrix4f m=node.parent()<0?new Matrix4f():world(f,node.parent());Pose p=f.poses[n];return m.translate(node.origin()).translate(p.pos).rotate(p.rot);}
 public Vector3f point(Frame f,String node){return world(f,id(node)).getTranslation(new Vector3f());}
 public Quaternionf rotation(Frame f,String node){return world(f,id(node)).getUnnormalizedRotation(new Quaternionf()).normalize();}
 public void apply(PoseStack ps,Frame f,int n){Node node=nodes[n];if(node.parent()>=0)apply(ps,f,node.parent());Pose p=f.poses[n];ps.translate(node.origin().x+p.pos.x,node.origin().y+p.pos.y,node.origin().z+p.pos.z);ps.mulPose(p.rot);}
 public boolean visible(Frame f,int n){return f.poses[n].visible>=.5&&(nodes[n].parent()<0||visible(f,nodes[n].parent()));}
}
