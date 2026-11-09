/* AcademyCraft1.0.7 ClientResources.preloadMipmapTexture adaptation. GPLv3; see NOTICE. */
package cn.academy.port.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import java.io.IOException;

/** Source trilinear source-node textures, owned/reloaded by the normal Minecraft texture manager. */
final class ClassicDeveloperTextures {
    static void prepare(){
        var manager=Minecraft.getInstance().getTextureManager();
        for(String name:new String[]{"skill_back","skill_radial_mask","skill_outline","line","skill_view_outline","skill_view_outline_glow"}){
            var location=ClassicHudCanvas.texture("guis/developer/"+name);
            if(!(manager.getTexture(location) instanceof MipmapTexture))manager.register(location,new MipmapTexture(location));
        }
    }
    private static final class MipmapTexture extends SimpleTexture {
        private int width,height;
        MipmapTexture(ResourceLocation location){super(location);}
        @Override protected TextureImage getTextureImage(ResourceManager resources){
            TextureImage image=super.getTextureImage(resources);
            try{width=image.getImage().getWidth();height=image.getImage().getHeight();}catch(IOException ignored){}
            return image;
        }
        @Override public void load(ResourceManager resources)throws IOException{
            super.load(resources);
            Runnable setup=()->{
                int previous=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);RenderSystem.bindTexture(getId());
                int maxLevel=31-Integer.numberOfLeadingZeros(Math.max(1,Math.max(width,height)));
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_MAX_LEVEL,maxLevel);
                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);setFilter(true,true);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
                RenderSystem.bindTexture(previous);
            };
            if(RenderSystem.isOnRenderThreadOrInit())setup.run();else RenderSystem.recordRenderCall(setup::run);
        }
    }
    private ClassicDeveloperTextures(){}
}
