package net.minecraft.server.packs.resources;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import net.minecraft.resources.ResourceLocation;

/** Reads authentic resources. Typed lanes alter one real JSON descriptor before ShaderInstance parses it. */
public final class ResourceManager {
    public final Path root;public final String mode;public int opened,descriptorMutations;
    public ResourceManager(Path root,String mode){this.root=root;this.mode=mode;}
    public Resource getResourceOrThrow(ResourceLocation location)throws IOException {
        opened++;
        if(mode.equals("missing"))throw new FileNotFoundException(location.toString());
        byte[] data=Files.readAllBytes(root.resolve("assets").resolve(location.namespace()).resolve(location.path()));
        if(mode.equals("corrupt"))data[0]^=1;
        if(mode.startsWith("typed-")&&location.path().equals("shaders/core/zero_gap_armor_edge.json")) {
            String[] parts=mode.split("-");
            if(parts.length!=3||!Set.of("ModelViewMat","ProjMat","ColorModulator","VisualPhase").contains(parts[1])||!Set.of("type","count").contains(parts[2]))throw new AssertionError("unknown typed lane "+mode);
            Pattern pattern=Pattern.compile("(\\\"name\\\"\\s*:\\s*\\\""+Pattern.quote(parts[1])+"\\\"\\s*,\\s*\\\"type\\\"\\s*:\\s*\\\")([^\\\"]+)(\\\"\\s*,\\s*\\\"count\\\"\\s*:\\s*)(\\d+)(\\s*,\\s*\\\"values\\\"\\s*:\\s*\\[)([^\\]]*)(\\])");
            String json=new String(data,StandardCharsets.UTF_8);Matcher match=pattern.matcher(json);
            if(!match.find())throw new AssertionError("typed descriptor anchor missing "+mode);
            String type=match.group(2),count=match.group(4);
            if(parts[2].equals("type"))type=type.equals("matrix4x4")?"matrix3x3":"int";
            else count=parts[1].equals("VisualPhase")?"2":parts[1].equals("ColorModulator")?"3":"1";
            // A single value is a native-valid broadcast; avoid merely testing a values-array parse error.
            String replacement=match.group(1)+type+match.group(3)+count+match.group(5)+"1"+match.group(7);
            int start=match.start(),end=match.end();if(match.find())throw new AssertionError("duplicate typed descriptor anchor");
            data=(json.substring(0,start)+replacement+json.substring(end)).getBytes(StandardCharsets.UTF_8);descriptorMutations++;
        }
        return new Resource(data);
    }
    public record Resource(byte[] data){public InputStream open(){return new ByteArrayInputStream(data);}}
}
