package cn.academy.port.client.city.o5;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

/** Black ink cores with sharply bounded crimson sheaths, in local glove space. */
final class BlackFlashBolts {
 static void ribbon(VertexConsumer out,PoseStack ps,Vec3 a,Vec3 b,Vec3 side,double width,int r,int g,int bl,float alpha){
  Vec3 s=side.scale(width);Vec3[] p={a.subtract(s),a.add(s),b.add(s),b.subtract(s)};
  for(int i=0;i<4;i++)out.addVertex(ps.last(),(float)p[i].x,(float)p[i].y,(float)p[i].z).setColor(r,g,bl,(int)(255*Math.max(0,Math.min(1,alpha)))).setUv(i==1||i==2?1:0,i>=2?1:0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(ps.last(),0,0,1);
 }
 static void stroke(VertexConsumer out,PoseStack ps,Vec3[] points,float width,float alpha){
  for(int k=0;k<points.length-1;k++){
   Vec3 a=points[k],b=points[k+1],dir=b.subtract(a).normalize(),side=dir.cross(new Vec3(0,0,1));if(side.lengthSqr()<.01)side=dir.cross(new Vec3(0,1,0));side=side.normalize();
   float w=width*(1-.65f*k/points.length);
   ribbon(out,ps,a,b,side,w*1.85,128,0,15,alpha*.10f);
   ribbon(out,ps,a,b,side,w*1.23,230,9,39,alpha);
   ribbon(out,ps,a,b,side,w*1.03,1,1,3,Math.min(1,alpha*1.4f));
  }
 }
 static void draw(VertexConsumer out,PoseStack ps,double time,float power,float contact,boolean left){
  int epoch=(int)Math.floor(time/2);double salt=left?.8:3.4;
  for(int arc=0;arc<5;arc++){
   Vec3[] p=new Vec3[8];double phase=arc*Math.PI*2/5+time*.052;
   for(int j=0;j<p.length;j++){
    double a=phase+j*.26,rad=.114+.009*Math.sin(j*2.3+epoch+arc);
    p[j]=new Vec3(Math.cos(a)*rad,-.026+Math.sin(a)*rad*.91,-.19+j*.035+.008*Math.sin(j*3.7+epoch));
   }
   stroke(out,ps,p,.0048f+.0036f*power,.22f+.56f*power);
  }
  if(power>.16f)for(int branch=0;branch<5;branch++){
   Vec3[] p=new Vec3[6];double angle=branch*Math.PI*2/5+salt;Vec3 start=new Vec3(Math.cos(angle)*.075,-.01+Math.sin(angle)*.065,-.17);
   for(int j=0;j<p.length;j++){
    double t=j/5.,zig=Math.sin(j*2.4+epoch*1.9+salt+branch)*.024*Math.sin(t*Math.PI);
    p[j]=start.add(Math.cos(angle)*(.07+contact*.16)*t+zig,Math.sin(angle)*(.07+contact*.14)*t-zig,-(.08+contact*.18)*t);
   }
   stroke(out,ps,p,.0070f+contact*.007f,.72f*power);
   if(branch%2==0){Vec3 root=p[2],tip=root.add(.04*Math.cos(angle+.8),.04*Math.sin(angle+.8),-.05);stroke(out,ps,new Vec3[]{root,root.lerp(tip,.55).add(.009,-.01,0),tip},.0035f,.65f*power);}
  }
 }
}
