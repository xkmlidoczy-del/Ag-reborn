package net.minecraft.client.renderer;
import com.mojang.blaze3d.vertex.*;import java.util.*;import proof.Trace;
public class MultiBufferSource {public static BufferSource immediate(ByteBufferBuilder b){return new BufferSource();}
 public static class BufferSource extends MultiBufferSource {private final LinkedHashSet<RenderType> types=new LinkedHashSet<>();public VertexConsumer getBuffer(RenderType t){types.add(t);Trace.add("buffer:"+t);return new VertexConsumer();}
 public void endBatch(){Trace.beforeAllFlush();Trace.add("flush-all:"+types.size());for(RenderType t:List.copyOf(types))endBatch(t);types.clear();}
 public void endBatch(RenderType t){Trace.beforeTypedFlush();if(types.remove(t)){Trace.add("setup:"+t);t.setup.run();Trace.add("draw:"+t);Trace.batches++;t.clear.run();Trace.add("clear:"+t);}}}
}
