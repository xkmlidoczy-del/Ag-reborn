package cn.academy.port.client.terminal.media;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

/** Source-local subpixel quads with modern buffer/state ownership. */
final class MediaCanvas {
    record Color(double r,double g,double b,double a){}
    static final Color WHITE=new Color(1,1,1,1),REFERENCE=new Color(225/255d,195/255d,133/255d,1);
    final GuiGraphics graphics;
    MediaCanvas(GuiGraphics graphics){this.graphics=graphics;}
    void begin(){graphics.flush();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableDepthTest();RenderSystem.disableCull();RenderSystem.setShaderColor(1,1,1,1);}
    void end(){graphics.flush();RenderSystem.disableBlend();RenderSystem.enableDepthTest();RenderSystem.enableCull();RenderSystem.setShaderColor(1,1,1,1);RenderSystem.setShader(GameRenderer::getPositionTexColorShader);}
    void rect(ResourceLocation texture,double x,double y,double width,double height,Color color){rect(texture,x,y,width,height,0,0,1,1,color,null,null);}
    void rect(ResourceLocation texture,double x,double y,double width,double height,double u0,double v0,double u1,double v1,Color color,ShaderInstance shader,ResourceLocation unused){
        if(color.a()<=0||width<=0||height<=0)return;
        RenderSystem.setShader(shader==null?GameRenderer::getPositionTexColorShader:()->shader);RenderSystem.setShaderTexture(0,texture);
        var buffer=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);var pose=graphics.pose().last().pose();
        double[] xs={x,x+width,x+width,x},ys={y+height,y+height,y,y},us={u0,u1,u1,u0},vs={v1,v1,v0,v0};
        for(int i=0;i<4;i++)buffer.addVertex(pose,(float)xs[i],(float)ys[i],0).setUv((float)us[i],(float)vs[i]).setColor((float)color.r(),(float)color.g(),(float)color.b(),(float)clamp(color.a()));
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }
    void fill(double x,double y,double width,double height,Color color){rect(texture("port/white"),x,y,width,height,color);}
    void minecraftText(String text,double x,double y,double size,Color color){
        graphics.pose().pushPose();graphics.pose().translate(x,y,0);graphics.pose().scale((float)(size/12),(float)(size/12),1);
        int argb=((int)(clamp(color.a())*255)<<24)|((int)(color.r()*255)<<16)|((int)(color.g()*255)<<8)|(int)(color.b()*255);
        graphics.drawString(Minecraft.getInstance().font,text,0,0,argb,false);graphics.flush();graphics.pose().popPose();begin();
    }
    static double clamp(double value){return Math.max(0,Math.min(1,value));}
    static ResourceLocation texture(String name){return ResourceLocation.fromNamespaceAndPath("academy","textures/"+name+".png");}
}
