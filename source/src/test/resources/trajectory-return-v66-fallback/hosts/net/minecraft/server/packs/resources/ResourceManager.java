package net.minecraft.server.packs.resources;
import java.io.*;import java.nio.file.*;import net.minecraft.resources.ResourceLocation;
/** Opens the actual original glow_line texture, with deterministic resource failure injection. */
public final class ResourceManager implements ResourceProvider {
 public final Path root;public final String mode;public int opened;
 public ResourceManager(Path root,String mode){this.root=root;this.mode=mode;}
 public Resource getResourceOrThrow(ResourceLocation location)throws IOException {
  if(!location.namespace().equals("academy")||!location.path().equals("textures/effects/glow_line.png"))throw new AssertionError("undeclared resource "+location);
  opened++;if(mode.equals("missing"))throw new FileNotFoundException(location.toString());
  byte[] data=Files.readAllBytes(root.resolve("assets").resolve(location.namespace()).resolve(location.path()));
  if(mode.equals("corrupt"))data[0]^=1;
  if(mode.equals("oversized"))data=new byte[65537];return new Resource(data);
 }
 public record Resource(byte[] data){public InputStream open(){return new ByteArrayInputStream(data);}}
}
