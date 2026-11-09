package cn.academy.port.client.city.o5;
import cn.academy.port.city.defense.O5Moves;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/** Original heavy-combat motion: load, drive, contact, follow-through, recovery. */
public final class FullBodyMotion {
 public static final class Frame {
  public Vector3f root=new Vector3f(0,-.055f,.015f),pelvis=new Vector3f(),spine=new Vector3f(-.035f,0,0),chest=new Vector3f(-.055f,0,0);
  public Vector3f lh=new Vector3f(.25f,1.43f,-.40f),rh=new Vector3f(-.25f,1.36f,-.35f),lf=new Vector3f(.14f,.0625f,-.13f),rf=new Vector3f(-.14f,.0625f,.10f);
  public float leftFootPitch,rightFootPitch,leftCurl=1,rightCurl=1,rightIndex=1,leftIndex=1,wristRoll,twist;public boolean leftPlanted=true,rightPlanted=true;
  public Vector3f leftPole=new Vector3f(1,-.55f,.15f),rightPole=new Vector3f(-1,-.55f,.15f);
  public float energyDrive,energyTurn=1;public int owner=-1,move=-1;
  public Vector3f viewLocal=new Vector3f(0,1.7f,-5);
 }
 static float smooth(float t){t=Mth.clamp(t,0,1);return t*t*(3-2*t);}
 static float punch(float t,float peak){return t<peak?smooth(t/peak):1-smooth((t-peak)/(1-peak));}
 static void toward(Vector3f a,float x,float y,float z,float k){a.lerp(new Vector3f(x,y,z),k);}
 static void step(Frame f,boolean left,float t,float amount){
  var foot=left?f.lf:f.rf;float travel=t<.30f?smooth(t/.30f):t<.66f?1:1-smooth((t-.66f)/.34f);
  float lift=t<.30f?(float)Math.sin(Math.PI*t/.30f):t>.66f?(float)Math.sin(Math.PI*(t-.66f)/.34f):0;
  foot.z-=amount*travel;foot.y+=Math.max(0,lift)*.055f;
  if(left){f.leftPlanted=lift<.02f;f.leftFootPitch=lift*.15f;}else{f.rightPlanted=lift<.02f;f.rightFootPitch=lift*.15f;}
 }
 public static Frame sample(int move,double elapsed,int dodge,double dodgeAge){
  Frame f=new Frame();f.move=move;var m=O5Moves.of(move);float t=(float)(elapsed/m.ticks),peak=(float)m.hit/m.ticks;
  if(t>=0&&t<1){float k=punch(t,peak),wind=t<peak?(float)Math.sin(Math.PI*t/peak):0;
   f.energyDrive=k;f.energyTurn=t<peak?1:-1;
   f.root.y-=.025f*wind;f.root.z+=.025f*wind;f.spine.y+=.10f*wind;f.chest.y+=.12f*wind;
   switch(move){
    case 0->{step(f,true,t,.10f);f.root.x+=.025f*k;f.root.z-=.045f*k;f.pelvis.y=.12f*k;f.chest.y=.22f*k;f.chest.x-=.06f*k;toward(f.lh,.17f,1.42f,-.64f,k);f.rh.y+=.04f*wind;}
    case 1->{step(f,false,t,.08f);f.root.x-=.035f*k;f.root.z-=.05f*k;f.pelvis.y=-.22f*k;f.spine.y=-.13f*k;f.chest.y=-.30f*k;f.chest.x-=.07f*k;toward(f.rh,-.06f,1.42f,-.59f,k);f.leftPole.z=-.12f;f.wristRoll=-.35f*k;}
    case 2->{step(f,true,t,.12f);f.root.y-=.025f*wind;f.root.z-=.07f*k;f.chest.x-=.15f*k;toward(f.lh,.18f,1.32f,-.62f,k);toward(f.rh,-.18f,1.32f,-.62f,k);f.leftCurl=f.rightCurl=1-k;}
    case 3->{f.root.z+=.07f*k;f.root.y-=.035f*k;f.pelvis.y=.15f*k;f.chest.x+=.10f*k;f.chest.y=.18f*k;
     f.lh.z-=.17f*wind;f.rh.z-=.17f*wind;toward(f.lh,.20f,1.22f,-.25f,k);toward(f.rh,-.20f,1.18f,-.22f,k);f.leftCurl=f.rightCurl=.2f+.8f*k;}
    case 4->{f.root.x-=.025f*k;f.pelvis.y=-.08f*k;f.chest.y=-.15f*k;toward(f.rh,-.10f,1.46f,-.53f,k);f.rightIndex=1-k;f.rightCurl=.65f;f.wristRoll=-.3f*k;}
    case 5->{step(f,false,t,.065f);f.root.x-=.045f*k;f.pelvis.y=-.32f*k;f.chest.y=-.44f*k;f.chest.z=.07f*k;
     toward(f.rh,.10f,1.29f,-.25f,k);f.rightPole.set(-1,.45f,-.9f);f.wristRoll=-.9f*k;}
    case 6->{f.root.x-=.065f*k;f.root.y-=.025f*k;f.chest.x-=.09f*k;f.pelvis.y=.08f*k;toward(f.lf,.12f,.40f,-.37f,k);f.leftPlanted=k<.015f;f.leftFootPitch=-.35f*k;f.lh.y-=.08f*k;f.rh.y-=.08f*k;}
    case 7->{f.root.x+=.065f*k;f.root.y-=.035f*k;f.pelvis.y=.24f*k;f.chest.y=-.20f*k;f.chest.z=-.16f*k;
     toward(f.rf,-.47f,.47f,-.37f,k);f.rightPlanted=k<.015f;f.rightFootPitch=.32f*k;f.lh.x+=.04f*k;f.rh.x-=.04f*k;}
    case 8->{f.root.x-=.05f*k;f.root.z+=.025f*k;f.chest.x+=.20f*k;f.pelvis.x+=.10f*k;toward(f.lf,.13f,.45f,-.60f,k);f.leftPlanted=k<.015f;f.leftFootPitch=.48f*k;f.rh.y+=.04f*k;}
    default->{step(f,true,t,.14f);f.root.y-=.055f*wind;f.root.z-=.085f*k;f.chest.x-=.18f*k;f.spine.x-=.06f*k;
     toward(f.lh,.16f,1.31f,-.64f,k);toward(f.rh,-.16f,1.31f,-.64f,k);f.leftCurl=f.rightCurl=1-k;}
   }
  }
  if(dodge>=0&&dodgeAge>=0&&dodgeAge<10){float d=(float)Math.sin(Math.PI*dodgeAge/10),sign=dodge%2==0?1:-1;
   f.root.y-=.055f*d;f.root.x+=sign*.045f*d;f.pelvis.z=-sign*.08f*d;f.chest.z=-sign*.18f*d;
   if(dodge==2||dodge==5){f.root.z+=.065f*d;f.root.y-=.035f*d;f.chest.x+=.22f*d;}
   if(dodge==3||dodge==4){f.pelvis.y=sign*.18f*d;f.chest.y-=sign*.28f*d;f.root.y-=.03f*d;}
   if(dodge==5){f.root.y-=.025f*d;f.lf.x+=.025f*d;f.rf.x-=.025f*d;}
  }
  return f;
 }
}
