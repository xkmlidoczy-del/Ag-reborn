/* AcademyCraft1.0.7 original Magnetic Hook OBJ resources. GPLv3; see NOTICE. */
package cn.academy.port.client;

import com.mojang.logging.LogUtils;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;

public final class ClassicHookModels implements ResourceManagerReloadListener {
    public static final ClassicHookModels INSTANCE=new ClassicHookModels();
    public static final ResourceLocation CLOSED=id("models/maghook.obj"),OPEN=id("models/maghook_open.obj"),TEXTURE=id("textures/models/maghook.png");
    public record Models(ClassicDeveloperObj.Mesh closed,ClassicDeveloperObj.Mesh open){}
    private volatile Models models;
    public Models models(){return models;}
    @Override public void onResourceManagerReload(ResourceManager resources){
        Models next=null;
        try{next=new Models(load(resources,CLOSED),load(resources,OPEN));}
        catch(Exception error){LogUtils.getLogger().error("Cannot load original Magnetic Hook OBJ pair",error);}
        models=next;
    }
    private static ClassicDeveloperObj.Mesh load(ResourceManager resources,ResourceLocation id)throws java.io.IOException{
        try(var in=resources.getResourceOrThrow(id).open();var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){return ClassicDeveloperObj.parse(reader);}
    }
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("academy",path);}
    private ClassicHookModels(){}
}
