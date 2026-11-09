/* AcademyCraft1.0.7 TerminalUI/SettingsUI/FreqTransmitterUI visual adaptation. GPLv3. */
package cn.academy.port.client;

import cn.academy.port.ClassicHudConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Shares the existing verified system-font and immediate canvas pipeline without changing its ownership. */
public final class ClassicTerminalDrawing {
    private final ClassicHudFont font=new ClassicHudFont();
    private ClassicHudCanvas canvas;
    public void prepare(){font.use(ClassicHudConfig.SPEC.isLoaded()?ClassicHudConfig.FONT.get():"Microsoft YaHei");}
    public void begin(GuiGraphics graphics){prepare();canvas=new ClassicHudCanvas(graphics);canvas.begin();}
    public void end(){canvas.end();canvas=null;}
    public void release(){font.release();}
    public void texture(String path,double x,double y,double w,double h,double alpha){texture(ResourceLocation.fromNamespaceAndPath("academy","textures/"+path+".png"),x,y,w,h,alpha);}
    public void texture(ResourceLocation texture,double x,double y,double w,double h,double alpha){canvas.rect(texture,x,y,w,h,0,0,1,1,new ClassicHudTimeline.Rgba(1,1,1,alpha),ClassicHudShaders.font,null);}
    public void fill(double x,double y,double w,double h,int rgb,double alpha){canvas.rect(ClassicHudCanvas.texture("port/white"),x,y,w,h,0,0,1,1,color(rgb,alpha),ClassicHudShaders.font,null);}
    public void glow(double x,double y,double w,double h,double size,int rgb,double alpha){canvas.glow(x,y,w,h,size,color(rgb,alpha),false);}
    public double width(String text,double size){return font.width(text,size,false);}
    public void text(String text,double x,double y,double size,int rgb,double alpha,int align){double width=align==0?0:width(text,size);font.draw(canvas,text,x-(align==1?width/2:align==2?width:0),y,size,color(rgb,alpha),false,false);}
    private static ClassicHudTimeline.Rgba color(int rgb,double alpha){return new ClassicHudTimeline.Rgba(((rgb>>16)&255)/255d,((rgb>>8)&255)/255d,(rgb&255)/255d,alpha);}
}
