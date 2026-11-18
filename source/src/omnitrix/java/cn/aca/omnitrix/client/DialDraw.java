package cn.aca.omnitrix.client;

import cn.aca.omnitrix.Kind;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;

final class DialDraw {
    private DialDraw() {}

    static int glow(Kind kind) {
        return switch (kind) {
            case CLASSIC -> 0xffb0ef76;
            case CELESTIAL -> 0xffbcdfff;
            case CARNITRIX -> 0xffefb688;
        };
    }

    static int alpha(int color, float opacity) {
        return ((int)((color>>>24)*Math.clamp(opacity,0,1)) << 24) | (color & 0xffffff);
    }

    static void arc(GuiGraphics g, float x,float y,float r,float outer,float from,float to,int color) {
        int count=Math.max(3,(int)(Math.abs(to-from)*outer/5));
        var sink=g.bufferSource().getBuffer(RenderType.gui());
        for(int i=0;i<count;i++){
            float angle=from+(to-from)*i/count, next=from+(to-from)*(i+1)/count;
            float a=(float)Math.cos(angle), b=(float)Math.sin(angle), c=(float)Math.cos(next), d=(float)Math.sin(next);
            vertex(g,sink,x+r*a,y+r*b,color);
            vertex(g,sink,x+r*c,y+r*d,color);
            vertex(g,sink,x+outer*c,y+outer*d,color);
            vertex(g,sink,x+outer*a,y+outer*b,color);
        }
    }

    static void edge(GuiGraphics g,float x,float y,float endX,float endY,float width,int color){
        float dx=endX-x,dy=endY-y;
        float length=(float)Math.hypot(dx,dy);
        if(length<.01f)return;
        float h=dy*width/(2*length),v=-dx*width/(2*length);
        var sink=g.bufferSource().getBuffer(RenderType.gui());
        vertex(g,sink,x+h,y+v,color);
        vertex(g,sink,x-h,y-v,color);
        vertex(g,sink,endX-h,endY-v,color);
        vertex(g,sink,endX+h,endY+v,color);
    }

    private static void vertex(GuiGraphics g,VertexConsumer sink,float x,float y,int color){
        sink.addVertex(g.pose().last().pose(),x,y,0).setColor(color);
    }

    static void tick(GuiGraphics g,float x,float y,float inner,float outer,float angle,int color){
        float a=(float)Math.cos(angle),b=(float)Math.sin(angle);
        edge(g,x+a*inner,y+b*inner,x+a*outer,y+b*outer,1.25f,color);
    }
}
