package cn.academy.port.solar;

import cn.academy.port.client.ClassicDeveloperObj;
import cn.academy.port.client.ClassicDeveloperTransform;
import cn.academy.port.client.ClassicSolarModelTransform;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.imageio.ImageIO;

public final class ClassicSolarDataRegressionTest {
    private static int assertions;
    private static void check(boolean condition,String label){assertions++;if(!condition)throw new AssertionError(label);}
    private static void close(double a,double b,String label){check(Math.abs(a-b)<1e-6,label);}
    private static byte[] resource(String path)throws Exception{try(var stream=ClassicSolarDataRegressionTest.class.getResourceAsStream('/'+path)){if(stream==null)throw new AssertionError("Missing resource "+path);return stream.readAllBytes();}}
    public static void main(String[] args)throws Exception{
        var manifest=JsonParser.parseString(new String(resource("cn/academy/port/solar/assets.json"),StandardCharsets.UTF_8)).getAsJsonArray();
        for(var entry:manifest){var value=entry.getAsJsonObject();String path=value.get("path").getAsString();byte[] bytes=resource(path);check(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(value.get("sha256").getAsString()),"canonical original asset identity "+path);if(path.endsWith(".png"))check(ImageIO.read(new ByteArrayInputStream(bytes))!=null,"valid original PNG "+path);}
        var mesh=ClassicDeveloperObj.parse(new InputStreamReader(new ByteArrayInputStream(resource("assets/academy/models/solar.obj")),StandardCharsets.UTF_8));
        check(!mesh.triangles().isEmpty(),"authentic complete OBJ faces");
        for(var facing:ClassicDeveloperTransform.Facing.values()){
            var bounds=ClassicSolarModelTransform.worldBounds(mesh.bounds(),facing);
            for(var triangle:mesh.triangles())for(var vertex:new ClassicDeveloperObj.Vertex[]{triangle.a(),triangle.b(),triangle.c()}){
                var point=ClassicSolarModelTransform.worldPoint(vertex.x(),vertex.y(),vertex.z(),facing);
                check(point.x()>=bounds.minX()-1e-7&&point.x()<=bounds.maxX()+1e-7&&point.y()>=bounds.minY()-1e-7&&point.y()<=bounds.maxY()+1e-7&&point.z()>=bounds.minZ()-1e-7&&point.z()<=bounds.maxZ()+1e-7,"full original mesh in cardinal frustum");
                double angle=Math.toRadians(facing.blockRotation()+90);
                close(point.x(),.5+Math.cos(angle)*vertex.x()*.014+Math.sin(angle)*vertex.z()*.014,"exact source X transform");
                close(point.z(),.5-Math.sin(angle)*vertex.x()*.014+Math.cos(angle)*vertex.z()*.014,"exact source Z transform");
            }
        }
        var recipe=JsonParser.parseString(new String(resource("data/academy/recipe/classic/solar_gen_09.json"),StandardCharsets.UTF_8)).getAsJsonObject();
        check(recipe.get("type").getAsString().equals("minecraft:crafting_shaped"),"native source shaped recipe");
        check(recipe.getAsJsonArray("pattern").toString().equals("[\"GGG\",\" W \",\"CFC\"]"),"exact source grid");
        String[][] expected={{"G","minecraft:glass_pane"},{"W","academy:wafer"},{"C","academy:energy_convert_component"},{"F","academy:machine_frame"}};
        for(var entry:expected)check(recipe.getAsJsonObject("key").getAsJsonObject(entry[0]).get("item").getAsString().equals(entry[1]),"exact source ingredient "+entry[0]);
        check(recipe.getAsJsonObject("result").get("id").getAsString().equals("academy:solar_gen")&&recipe.getAsJsonObject("result").get("count").getAsInt()==1,"source generator yield1");
        String sourceRecipe=new String(resource("cn/academy/port/solar/default-solar.recipe"),StandardCharsets.UTF_8);
        check(sourceRecipe.contains("[glass_pane, glass_pane, glass_pane]")&&sourceRecipe.contains("[nil,        wafer,      nil]")&&sourceRecipe.contains("[conv_comp,  frame,      conv_comp]"),"retained original source recipe excerpt");
        var particle=JsonParser.parseString(new String(resource("assets/academy/models/block/solar_gen.json"),StandardCharsets.UTF_8)).getAsJsonObject();check(particle.getAsJsonArray("elements").isEmpty(),"native chunk model empty for sole OBJ renderer");
        var icon=JsonParser.parseString(new String(resource("assets/academy/models/item/solar_gen.json"),StandardCharsets.UTF_8)).getAsJsonObject();check(icon.getAsJsonObject("textures").get("layer0").getAsString().equals("academy:blocks/solar_gen"),"original inventory icon");
        System.out.println("ClassicSolarDataRegressionTest: "+assertions+" assertions passed; "+mesh.positionCount()+" source vertices / "+mesh.triangles().size()+" triangles, original8 assets, recipe and four rotations");
    }
}
