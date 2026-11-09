package net.minecraft.world.phys;
public final class Vec3 {public final double x,y,z;public static final Vec3 ZERO=new Vec3(0,0,0);public Vec3(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
 public Vec3 add(Vec3 p){return new Vec3(x+p.x,y+p.y,z+p.z);} public Vec3 add(double a,double b,double c){return new Vec3(x+a,y+b,z+c);}
 public Vec3 subtract(Vec3 p){return new Vec3(x-p.x,y-p.y,z-p.z);}public Vec3 scale(double n){return new Vec3(x*n,y*n,z*n);}
 public Vec3 lerp(Vec3 p,double n){return new Vec3(x+(p.x-x)*n,y+(p.y-y)*n,z+(p.z-z)*n);}public double distanceToSqr(Vec3 p){double a=x-p.x,b=y-p.y,c=z-p.z;return a*a+b*b+c*c;}public double distanceTo(Vec3 p){return Math.sqrt(distanceToSqr(p));}
 public Vec3 cross(Vec3 p){return new Vec3(y*p.z-z*p.y,z*p.x-x*p.z,x*p.y-y*p.x);}public double lengthSqr(){return x*x+y*y+z*z;}public double dot(Vec3 p){return x*p.x+y*p.y+z*p.z;}public double length(){return Math.sqrt(x*x+y*y+z*z);}public Vec3 normalize(){return length()<1e-4?ZERO:scale(1/length());}
 public boolean equals(Object o){return o instanceof Vec3 p&&Double.compare(x,p.x)==0&&Double.compare(y,p.y)==0&&Double.compare(z,p.z)==0;}public int hashCode(){return java.util.Objects.hash(x,y,z);}
 public String toString(){return x+","+y+","+z;}}
