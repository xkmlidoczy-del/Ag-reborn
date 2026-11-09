package net.minecraft.client.renderer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.shaders.Uniform;

/** Exact two-shader CPU host. Uniform descriptors derive from actual JSON; driver outcomes are controlled. */
public final class ShaderInstance {
    public static boolean failCompile;
    public static String missingUniform="";
    public static int attempts,created,closed;
    public static ShaderInstance lastConstructed;
    private int id;
    private final Map<String,Uniform> uniforms=new HashMap<>();
    public String source="position-tex-color-shader";
    private static final Pattern UNIFORM=Pattern.compile("\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"\\s*,\\s*\\\"type\\\"\\s*:\\s*\\\"(int|float|matrix2x2|matrix3x3|matrix4x4)\\\"\\s*,\\s*\\\"count\\\"\\s*:\\s*(\\d+)\\s*,\\s*\\\"values\\\"\\s*:\\s*\\[([^\\]]*)\\]");
    public ShaderInstance(){id=0;}
    public ShaderInstance(ResourceManager provider,ResourceLocation name,VertexFormat format)throws IOException {
        if(!name.namespace().equals("academy")||!Set.of("folded_mirror_energy","zero_gap_armor_edge").contains(name.path()))throw new AssertionError("undeclared shader dependency "+name);
        attempts++;
        if(failCompile)throw new IOException("controlled shader compilation failure");
        for(String extension:new String[]{"json","vsh","fsh"})try(var in=provider.getResourceOrThrow(ResourceLocation.fromNamespaceAndPath(name.namespace(),"shaders/core/"+name.path()+"."+extension)).open()) {
            byte[] bytes=in.readAllBytes();if(bytes.length==0)throw new IOException("empty shader resource");
            if(extension.equals("json")) {
                var matcher=UNIFORM.matcher(new String(bytes,StandardCharsets.UTF_8));
                while(matcher.find()) {
                    String uniformName=matcher.group(1);int base=Uniform.getTypeFromString(matcher.group(2)),count=Integer.parseInt(matcher.group(3));
                    String[] values=matcher.group(4).trim().split(",");
                    if(count<1||values.length!=count&&values.length>1)throw new IOException("invalid amount of uniform values");
                    for(String value:values)Float.parseFloat(value.trim());
                    // Matches ShaderInstance.parseUniformNode from the pinned native sources.
                    int type=base+(count>1&&count<=4&&base<8?count-1:0);
                    if(uniforms.put(uniformName,new Uniform(uniformName,type,count))!=null)throw new IOException("duplicate uniform declaration");
                }
                if(uniforms.isEmpty())throw new IOException("no supported JSON uniform declarations");
            }
        }
        id=++created;source="shader="+name;lastConstructed=this;
    }
    public Uniform getUniform(String name) {
        if(!Set.of("MaterialKind","TexturePart","VisualPhase","Contact","ModelViewMat","ProjMat","ColorModulator").contains(name))throw new AssertionError("unknown uniform query "+name);
        if(source.equals("shader=academy:zero_gap_armor_edge")&&name.equals(missingUniform))return null;
        return uniforms.get(name);
    }
    public int getId(){return id;}
    public void close(){closed++;}
}
