/* ArcFactory list/buffer generation and ribbon vertices adapted from AcademyCraft1.0.7.
 * Copyright (c) Lambda Innovation,2013-2016. GPLv3; see NOTICE. */
package cn.academy.port.client;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
/** Modern CPU geometry for original local display-list/dynamic-ribbon business code. */
final class ClassicArcGeometry {
 record Point(Vec3 position,double width){}
 record Segment(Point start,Point end,double alpha){}
 record Quad(Vec3 p1,Vec3 p2,Vec3 p4,Vec3 p3,double alpha){}
 private static final Random ROTATION_RANDOM=new Random();
 private static final int MAX_PATHS=4096,MAX_SEGMENTS=65536;
 private static final class PatternPaths extends ArrayList<List<Segment>>{
  final List<Quad> displayList;
  PatternPaths(List<List<Segment>> paths,Random rotation){super(paths);displayList=ribbons(this,23333333,new Vec3(0,0,1),rotation);}
 }
 private ClassicArcGeometry(){}
 static List<List<Segment>> generate(Random shape,double length,int passes,double width,double offset,double branch,double shrink){return generate(shape,ROTATION_RANDOM,length,passes,width,offset,branch,shrink);}
 static List<List<Segment>> generate(Random shape,Random rotation,double length,int passes,double width,double offset,double branch,double shrink){
  if(!Double.isFinite(length)||!Double.isFinite(width)||!Double.isFinite(offset)||!Double.isFinite(branch)||!Double.isFinite(shrink)||length<0||width<0||offset<0||passes<0||passes>8||branch<0||branch>=1||shrink<0)throw new IllegalArgumentException("Invalid bounded arc geometry");
  var lists=new ArrayList<List<Segment>>();var buffers=new ArrayList<List<Segment>>();var initial=new ArrayList<Segment>();
  initial.add(new Segment(new Point(Vec3.ZERO,width),new Point(new Vec3(length,0,0),width),1));lists.add(initial);buffers.add(new ArrayList<>());boolean flip=false;
  for(int pass=0;pass<passes;pass++){
   // Keep source dynamic list size, new-path emptiness and alternating pass direction.
   for(int index=0;index<lists.size();index++)split(flip?buffers.get(index):lists.get(index),flip?lists.get(index):buffers.get(index),offset,shape,rotation,branch,shrink,lists,buffers);
   flip=!flip;offset/=2;
  }
  // Original Arc constructor builds its complete GL list, including ribbon RNG use.
  return new PatternPaths(flip?buffers:lists,rotation);
 }
 private static void split(List<Segment> source,List<Segment> output,double offset,Random shape,Random rotation,double branch,double shrink,List<List<Segment>> lists,List<List<Segment>> buffers){
  output.clear();
  for(Segment segment:source){
   Vec3 a=segment.start.position,b=segment.end.position;Vec3 midpoint=new Vec3(a.x+.5*(b.x-a.x),a.y+.5*(b.y-a.y),a.z+.5*(b.z-a.z));
   float theta=(float)(shape.nextFloat()*Math.PI*2);double off=shape.nextFloat()*offset;midpoint=midpoint.add(0,off*Mth.sin(theta),off*Mth.cos(theta));
   Point average=new Point(midpoint,(segment.start.width+segment.end.width)/2);output.add(new Segment(segment.start,average,segment.alpha));output.add(new Segment(average,segment.end,segment.alpha));
   if(output.size()>MAX_SEGMENTS)throw new IllegalStateException("Arc segment safety bound");
   if(shape.nextDouble()<branch){
    Vec3 direction=randomRotate(rotation,10,midpoint.subtract(a).scale(.7));double nextWidth=average.width*shrink;var child=new ArrayList<Segment>();
    child.add(new Segment(new Point(midpoint,nextWidth),new Point(midpoint.add(direction),nextWidth),segment.alpha*.9));
    if(lists.size()>=MAX_PATHS)throw new IllegalStateException("Arc path safety bound");buffers.add(child);lists.add(new ArrayList<>());
   }
  }
 }
 static List<Quad> baked(List<List<Segment>> paths){if(!(paths instanceof PatternPaths pattern))throw new IllegalArgumentException("Missing generated arc display list");return pattern.displayList;}
 static List<Quad> ribbons(List<List<Segment>> paths,double length){return ribbons(paths,length,new Vec3(0,0,1),ROTATION_RANDOM);}
 static List<Quad> ribbons(List<List<Segment>> paths,double length,Vec3 normal,Random rotation){
  var result=new ArrayList<Quad>();
  for(List<Segment> path:paths){Vec3 last=null;for(Segment segment:path){
   if(segment.start.position.x>length)break;
   Vec3 up=randomRotate(rotation,15,segment.end.position.subtract(segment.start.position).cross(normal)).normalize();if(last==null)last=up;
   result.add(new Quad(segment.start.position.add(last.scale(segment.start.width)),segment.start.position.add(last.scale(-segment.start.width)),segment.end.position.add(up.scale(-segment.end.width)),segment.end.position.add(up.scale(segment.end.width)),segment.alpha));last=up;
  }}return result;
 }
 private static Vec3 randomRotate(Random random,float range,Vec3 vector){float angle=(float)(range(random,-range,range)/180*Math.PI);return vector.xRot(range(random,-angle,angle)).yRot(range(random,-angle,angle)).zRot(range(random,-angle,angle));}
 private static float range(Random random,float from,float to){return from+random.nextFloat()*(to-from);}
}
