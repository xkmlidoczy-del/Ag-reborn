package cn.academy.port.client.city.o5;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
final class CombatHands {
 static final ResourceLocation TEX=ResourceLocation.fromNamespaceAndPath("academy","textures/combat/hands.png");
 static void box(PoseStack ps,MultiBufferSource buffers,int light,double cx,double cy,double cz,double sx,double sy,double sz,int tile){
  var out=buffers.getBuffer(RenderType.entityCutoutNoCull(TEX));float x=(float)(cx-sx/2),y=(float)(cy-sy/2),z=(float)(cz-sz/2),X=(float)(cx+sx/2),Y=(float)(cy+sy/2),Z=(float)(cz+sz/2);
  float[][][] f={{{x,y,z},{x,Y,z},{X,Y,z},{X,y,z}},{{X,y,Z},{X,Y,Z},{x,Y,Z},{x,y,Z}},
   {{x,y,Z},{x,Y,Z},{x,Y,z},{x,y,z}},{{X,y,z},{X,Y,z},{X,Y,Z},{X,y,Z}},
   {{x,Y,z},{x,Y,Z},{X,Y,Z},{X,Y,z}},{{x,y,Z},{x,y,z},{X,y,z},{X,y,Z}}};
  float[][] normals={{0,0,-1},{0,0,1},{-1,0,0},{1,0,0},{0,1,0},{0,-1,0}};
  float u=(tile%4)*.25f+.012f,v=(tile/4)*.5f+.024f,du=.226f,dv=.452f;float[][] uv={{u,v+dv},{u,v},{u+du,v},{u+du,v+dv}};
  for(int i=0;i<6;i++)for(int j=0;j<4;j++)out.addVertex(ps.last(),f[i][j][0],f[i][j][1],f[i][j][2]).setColor(255,255,255,255).setUv(uv[j][0],uv[j][1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(ps.last(),normals[i][0],normals[i][1],normals[i][2]);
 }
}
