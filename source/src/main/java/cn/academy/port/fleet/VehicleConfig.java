package cn.academy.port.fleet;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.world.phys.Vec3;

/** Shared metre-scale body, seat, control and collision data exported with each source model. */
public final class VehicleConfig {
 public record Node(String name,String parent,Vec3 origin){}
 public record Wheel(String name,Vec3 position,float radius,boolean front){}
 private static final Map<Integer,VehicleConfig> CACHE=new HashMap<>();
 public final String id,kind;public final float width,length,height,steeringMax,steeringRadius;
 public final Vec3 eye,steering;public final List<Vec3> seats,pedals;public final List<Node> nodes;public final List<Wheel> wheels;
 public final List<double[]> collision;public final Map<String,Integer> doorSides;
 private VehicleConfig(JsonObject j){
  id=j.get("id").getAsString();kind=j.get("kind").getAsString();width=j.get("width").getAsFloat();length=j.get("length").getAsFloat();height=j.get("height").getAsFloat();steeringMax=j.get("steering_max_degrees").getAsFloat();steeringRadius=j.get("steering_radius").getAsFloat();
  eye=vec(j.getAsJsonArray("eye"));steering=vec(j.getAsJsonArray("steering_center"));seats=points(j.getAsJsonArray("seats"));pedals=points(j.getAsJsonArray("pedals"));
  nodes=new ArrayList<>();for(var a:j.getAsJsonArray("nodes")){var n=a.getAsJsonObject();nodes.add(new Node(n.get("name").getAsString(),n.get("parent").isJsonNull()?null:n.get("parent").getAsString(),vec(n.getAsJsonArray("origin"))));}
  wheels=new ArrayList<>();for(var a:j.getAsJsonArray("wheels")){var w=a.getAsJsonObject();wheels.add(new Wheel(w.get("node").getAsString(),vec(w.getAsJsonArray("position")),w.get("radius").getAsFloat(),w.get("front").getAsBoolean()));}
  collision=new ArrayList<>();for(var a:j.getAsJsonArray("collision_segments")){var arr=a.getAsJsonArray();double[] v=new double[6];for(int i=0;i<6;i++)v[i]=arr.get(i).getAsDouble();collision.add(v);}
  doorSides=new HashMap<>();for(var a:j.getAsJsonArray("doors")){var d=a.getAsJsonObject();doorSides.put(d.get("node").getAsString(),d.get("side").getAsInt());}
  if(width<.4||length<1||length>16||seats.isEmpty()||collision.isEmpty())throw new IllegalArgumentException("vehicle descriptor limits");
 }
 /** Exterior hinges: mirrored side doors and rear doors open away from the cabin. */
 public float doorRotation(String node,float amount){return exteriorDoorDegrees(doorSides.getOrDefault(node,0),amount);}
 public static float exteriorDoorDegrees(int side,float amount){return -Integer.signum(side)*Math.max(0,Math.min(1,amount))*62f;}
 public boolean scooter(){return kind.equals("scooter");}
 private static List<Vec3> points(JsonArray a){var list=new ArrayList<Vec3>();for(var e:a)list.add(vec(e.getAsJsonArray()));return List.copyOf(list);}
 private static Vec3 vec(JsonArray a){return new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}
 public static VehicleConfig get(int variant){return CACHE.computeIfAbsent(Math.max(0,Math.min(5,variant)),v->{String id=InteractionLab.CAR_IDS[v];try(var in=VehicleConfig.class.getResourceAsStream("/assets/academy/vehicles/"+id+".json")){return new VehicleConfig(JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject());}catch(Exception ex){throw new IllegalStateException("Cannot load vehicle "+id,ex);}});}
}
