package cn.academy.port.fleet;

import cn.academy.port.fleet.client.AcmMeshReader;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

/** Uses actual authored vertices at all three LODs: opening must move each leaf away from the cabin. */
public final class VehicleDoorExteriorRegressionTest {
    public static void main(String[] args)throws Exception{
        Path assets=Path.of("src/main/resources/assets/academy");int checked=0,oldInward=0,sliders=0;
        try(var paths=Files.list(assets.resolve("vehicles"))){for(var config:paths.filter(p->p.toString().endsWith(".json")).toList()){
            var j=JsonParser.parseString(Files.readString(config)).getAsJsonObject();if(!j.has("doors"))continue;String id=j.get("id").getAsString();Map<String,JsonObject> doors=new HashMap<>();for(var e:j.getAsJsonArray("doors"))doors.put(e.getAsJsonObject().get("node").getAsString(),e.getAsJsonObject());
            for(String suffix:List.of("","_lod1","_lod2")){
                var mesh=AcmMeshReader.read(Files.newInputStream(assets.resolve("mesh/"+id+suffix+".bin")));
                for(var part:mesh.parts()){
                    var door=doors.get(part.name());if(door==null)continue;if(part.name().equals("door_slide")){sliders++;continue;}
                    var hinge=door.getAsJsonArray("hinge");double hx=hinge.get(0).getAsDouble(),hz=hinge.get(2).getAsDouble(),cx=0,cz=0;int n=part.values().length/8;for(int k=0;k<part.values().length;k+=8){cx+=part.values()[k];cz+=part.values()[k+2];}cx/=n;cz/=n;
                    int side=door.get("side").getAsInt();double closed=part.name().startsWith("door_rear")?hz+cz:Math.abs(hx+cx);
                    for(float amount:new float[]{.2f,.5f,1f}){
                        double a=Math.toRadians(VehicleConfig.exteriorDoorDegrees(side,amount)),x=hx+cx*Math.cos(a)+cz*Math.sin(a),z=hz-cx*Math.sin(a)+cz*Math.cos(a);
                        boolean exterior=part.name().startsWith("door_rear")?z<closed:Math.abs(x)>closed;
                        if(!exterior)throw new AssertionError(id+suffix+" "+part.name()+" opens inside at "+amount);checked++;
                        double old=-a,ox=hx+cx*Math.cos(old)+cz*Math.sin(old),oz=hz-cx*Math.sin(old)+cz*Math.cos(old);if(part.name().startsWith("door_rear")?oz>closed:Math.abs(ox)<closed)oldInward++;
                    }
                }
            }
        }}
        if(checked<100||oldInward<100||sliders!=3)throw new AssertionError("Missing authored door coverage: "+checked+" "+oldInward+" sliders="+sliders);
        System.out.println("PASS actual door vertices: "+checked+" exterior checks across three LODs, "+oldInward+" old inward witnesses, "+sliders+" unchanged slider LODs");
    }
}
