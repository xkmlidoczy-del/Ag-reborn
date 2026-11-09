package cn.academy.port.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** Immediate GUI-local meshes: preserve subpixel source geometry rather than integer-scissoring a bar. */
final class ClassicHudCanvas {
    private static final ResourceLocation WHITE=texture("port/white");
    private final GuiGraphics graphics;
    ClassicHudCanvas(GuiGraphics graphics){this.graphics=graphics;}
    void begin(){graphics.flush();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableDepthTest();RenderSystem.disableCull();RenderSystem.setShaderColor(1,1,1,1);}
    // Vanilla GUI teardown must leave blending off before the menu blur's cached opaque pass.
    void end(){RenderSystem.disableBlend();RenderSystem.setShaderColor(1,1,1,1);RenderSystem.setShader(GameRenderer::getPositionTexColorShader);RenderSystem.enableDepthTest();RenderSystem.enableCull();}
    void rect(ResourceLocation texture,double x,double y,double width,double height,ClassicHudTimeline.Rgba color){rect(texture,x,y,width,height,0,0,1,1,color,null,null);}
    void rect(ResourceLocation texture,double x,double y,double width,double height,double u0,double v0,double u1,double v1,
              ClassicHudTimeline.Rgba color,ShaderInstance shader,ResourceLocation second){
        quad(texture,new double[]{x,y+height,x+width,y+height,x+width,y,x,y},new double[]{u0,v1,u1,v1,u1,v0,u0,v0},color,shader,second);
    }
    void cp(ResourceLocation texture,ResourceLocation icon,double progress,ClassicHudTimeline.Rgba color){
        var q=ClassicHudTimeline.cpQuad(progress);
        double[] xy={q.leftTop(),q.top(),q.leftBottom(),q.bottom(),q.right(),q.bottom(),q.right(),q.top()};
        double[] uv=new double[8];for(int i=0;i<4;i++){uv[i*2]=xy[i*2]/964;uv[i*2+1]=xy[i*2+1]/147;}
        quad(texture,xy,uv,color,ClassicHudShaders.cp,icon);
    }
    void fill(double x,double y,double width,double height,ClassicHudTimeline.Rgba color,boolean mono){
        rect(WHITE,x,y,width,height,0,0,1,1,color,mono?ClassicHudShaders.mono:null,null);
    }
    void glow(double x,double y,double width,double height,double size,ClassicHudTimeline.Rgba color,boolean mono){
        ShaderInstance shader=mono?ClassicHudShaders.mono:null;
        glowPart("left",x-size,y,size,height,color,shader);glowPart("right",x+width,y,size,height,color,shader);
        glowPart("up",x,y-size,width,size,color,shader);glowPart("down",x,y+height,width,size,color,shader);
        glowPart("ru",x+width,y-size,size,size,color,shader);glowPart("rd",x+width,y+height,size,size,color,shader);
        glowPart("lu",x-size,y-size,size,size,color,shader);glowPart("ld",x-size,y+height,size,size,color,shader);
    }
    private void glowPart(String name,double x,double y,double w,double h,ClassicHudTimeline.Rgba color,ShaderInstance shader){rect(texture("guis/glow_"+name),x,y,w,h,0,0,1,1,color,shader,null);}
    void minecraftText(String text,double x,double y,double size,ClassicHudTimeline.Rgba color){
        graphics.pose().pushPose();graphics.pose().translate(x,y,0);graphics.pose().scale((float)(size/12),(float)(size/12),1);
        int argb=((int)(ClassicHudTimeline.clamp(color.a())*255)<<24)|((int)(color.r()*255)<<16)|((int)(color.g()*255)<<8)|(int)(color.b()*255);
        graphics.drawString(net.minecraft.client.Minecraft.getInstance().font,text,0,0,argb,false);graphics.flush();graphics.pose().popPose();
    }
    void quad(ResourceLocation texture,double[] xy,double[] uv,ClassicHudTimeline.Rgba color,ShaderInstance shader,ResourceLocation second){
        if(color.a()<=0)return;
        RenderSystem.setShader(shader==null?GameRenderer::getPositionTexColorShader:()->shader);
        RenderSystem.setShaderTexture(0,texture);
        if(second!=null)RenderSystem.setShaderTexture(1,second);
        BufferBuilder buffer=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
        Matrix4f matrix=graphics.pose().last().pose();
        for(int i=0;i<4;i++)buffer.addVertex(matrix,(float)xy[i*2],(float)xy[i*2+1],0).setUv((float)uv[i*2],(float)uv[i*2+1]).setColor((float)color.r(),(float)color.g(),(float)color.b(),(float)ClassicHudTimeline.clamp(color.a()));
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }
    static ResourceLocation texture(String path){return ResourceLocation.fromNamespaceAndPath("academy","textures/"+path+".png");}
}
