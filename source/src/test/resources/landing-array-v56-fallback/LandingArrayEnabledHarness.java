package cn.academy.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import proof.Trace;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import static cn.academy.port.client.LandingArrayFallbackHarness.*;

/** Bounded current-source smoke witnesses, not a frozen enabled beauty or GPU oracle. */
public final class LandingArrayEnabledHarness {
    static List<String> snapshot(String name,int expected)throws Exception {
        snap(name,false);
        require(Trace.vertices==expected,"OPTIONAL_VERTEX_COUNT:"+name+":"+Trace.vertices);
        require(Trace.rows.stream().anyMatch(r->r.startsWith("buffer:academy_rail_array_soft")),"OPTIONAL_SOFT_MATERIAL_MISSING:"+name);
        require(Trace.rows.stream().anyMatch(r->r.startsWith("buffer:academy_landing_array_textures/port/white.png")),"OPTIONAL_CORE_MATERIAL_MISSING:"+name);
        return List.copyOf(Trace.rows);
    }
    public static void main(String[] args)throws Exception {
        lane="ready-enabled";prepareMaterial(lane,Path.of(args[1]));Path out=Path.of(args[0]);Files.createDirectories(out);
        final int perCue=MultidimensionalLandingCueGeometry.MAX_VERTICES;
        require(perCue==144,"EXPECTED_BOUNDED_GEOMETRY");
        String sourceHash,contactHash;
        try(var c=Files.newBufferedWriter(out.resolve("cases.tsv"));var gz=new GZIPOutputStream(Files.newOutputStream(out.resolve("capture.txt.gz")));var full=new BufferedWriter(new OutputStreamWriter(gz,StandardCharsets.UTF_8))){
            cases=c;capture=full;
            reset(0,true);prepare(3);var source=snapshot("enabled/source-publication",perCue);sourceHash=sha(String.join("\n",source));
            receive(packet(4,6,0,false,false,""));require(cues().size()==1&&!(boolean)field(cues().getFirst(),"contact"),"REJECTED_IMPACT_INVENTED_CONTACT");
            require(snapshot("enabled/rejected-impact-retains-source",perCue).equals(source),"REJECTED_IMPACT_CHANGED_CUE");
            reset(0,true);prepare(4);var contact=snapshot("enabled/accepted-contact",perCue);contactHash=sha(String.join("\n",contact));
            require(!source.equals(contact),"SOURCE_AND_CONTACT_NOT_DISTINCT");
            reset(0,true);prepare(2);for(int shot=0;shot<6;shot++)receive(packet(3,6,shot,true,false,""));require(cues().size()==6,"SIX_SOURCES_REQUIRED");
            snapshot("enabled/six-source-cues",6*perCue);require(Trace.vertices<=MultidimensionalLandingCueGeometry.MAX_SCENE_VERTICES,"SCENE_VERTEX_CAP_EXCEEDED");
            for(int shot=0;shot<6;shot++)receive(packet(4,6,shot,true,false,""));for(Object cue:cues())require((boolean)field(cue,"contact"),"SIX_CONTACTS_REQUIRED");
            snapshot("enabled/six-contact-cues",6*perCue);require(Trace.vertices<=MultidimensionalLandingCueGeometry.MAX_SCENE_VERTICES,"SCENE_VERTEX_CAP_EXCEEDED");
        }
        reset(0,true);prepare(3);Object cue=cues().getFirst();
        Method helper=MultidimensionalLandingArrayEffects.class.getDeclaredMethod("enhancedCue",Matrix4f.class,cue.getClass(),Vec3.class,Vec3.class,float.class,int.class);helper.setAccessible(true);
        Trace.reset();int refused=(int)helper.invoke(null,new Matrix4f(),cue,camera,forward,.375f,perCue-1);
        require(refused==0&&Trace.vertices==0&&Trace.rows.isEmpty(),"BUDGET_REFUSAL_PARTIALLY_SUBMITTED");
        Files.writeString(out.resolve("budget-refusal.txt"),"Injected remaining vertices="+(perCue-1)+"; return="+refused+"; vertices="+Trace.vertices+"; buffer/state calls="+Trace.rows.size()+"\n");
        Trace.reset();int admitted=(int)helper.invoke(null,new Matrix4f(),cue,camera,forward,.375f,perCue);
        require(admitted==perCue&&Trace.vertices==perCue,"EXACT_BUDGET_NOT_ADMITTED");
        Files.writeString(out.resolve("budget-exact.txt"),"Injected remaining vertices="+perCue+"; return="+admitted+"\n"+String.join("\n",Trace.rows)+"\n");
        System.out.println("{\"result\":\"PASS_CURRENT_ENABLED_SMOKE\",\"cases\":"+count+",\"vertices\":"+totalVertices+",\"per_cue_vertices\":"+perCue+",\"six_cue_vertices\":"+(6*perCue)+",\"scene_cap\":"+MultidimensionalLandingCueGeometry.MAX_SCENE_VERTICES+",\"source_sha256\":\""+sourceHash+"\",\"contact_sha256\":\""+contactHash+"\",\"budget_refusal\":\"zero return and no vertex, buffer or state calls\",\"exact_budget\":\"144 admitted\"}");
    }
}
