package cn.academy.port.client.city.o5;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;

/** Afterimages share the visible articulated rig and the current body motion. */
public final class ArmorGhost {
 static final ResourceLocation TEX=FullBodyRig.BASE;
 public static void draw(PoseStack ps,MultiBufferSource buffers,int move,double age,int dodge,double dodgeAge,float alpha){
  var frame=FullBodyMotion.sample(move,dodge>=0?-1:age,dodge,dodgeAge);
  FullBodyRig.draw(ps,buffers,frame,0,0,0xF000F0,0,false,false,((int)(alpha*255)<<24)|0x645947,true);
 }
}
