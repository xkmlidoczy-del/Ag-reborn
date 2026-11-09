package net.minecraft.client.gui;
import org.joml.Matrix4f;import net.minecraft.client.renderer.MultiBufferSource;import proof.Trace;
/** Captures native-font calls and conservative four-quad budget, not font glyph tessellation. */
public class Font {public enum DisplayMode{NORMAL,SEE_THROUGH,POLYGON_OFFSET}public int width(String text){return text.length()*6;}
 public int drawInBatch(String text,float x,float y,int color,boolean shadow,Matrix4f matrix,MultiBufferSource buffers,DisplayMode mode,int background,int light){Trace.add("font:"+text+":"+Trace.f(x)+":"+Trace.f(y)+":"+Integer.toHexString(color)+":"+shadow+":"+matrix+":"+mode+":"+background+":"+light);Trace.labels++;return width(text);}}
