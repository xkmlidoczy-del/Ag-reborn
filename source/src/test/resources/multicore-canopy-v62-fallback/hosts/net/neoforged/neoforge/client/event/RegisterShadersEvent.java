package net.neoforged.neoforge.client.event;
import java.util.function.Consumer;import net.minecraft.client.renderer.ShaderInstance;import net.minecraft.server.packs.resources.ResourceProvider;
public class RegisterShadersEvent{public ResourceProvider getResourceProvider(){return null;}public void registerShader(ShaderInstance shader,Consumer<ShaderInstance> consumer){consumer.accept(shader);}}
