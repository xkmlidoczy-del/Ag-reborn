package oracle;
import java.util.*;
public class Capture {public static List<double[]> vertices=new ArrayList<>();public static List<String> calls=new ArrayList<>(),messages=new ArrayList<>();public static String texture;public static double rectWidth,rectHeight;static double[] matrix=identity();static Deque<double[]> matrices=new ArrayDeque<>();
static double[] identity(){double[] r=new double[16];for(int i=0;i<4;i++)r[i*4+i]=1;return r;}
public static void push(){matrices.push(matrix.clone());}public static void pop(){matrix=matrices.pop();}
static void multiply(double[] b){double[] r=new double[16];for(int row=0;row<4;row++)for(int col=0;col<4;col++)for(int k=0;k<4;k++)r[row*4+col]+=matrix[row*4+k]*b[k*4+col];matrix=r;}
public static void translate(double x,double y,double z){double[] m=identity();m[3]=x;m[7]=y;m[11]=z;multiply(m);}
public static void rotate(double angle,double x,double y,double z){double c=Math.cos(Math.toRadians(angle)),s=Math.sin(Math.toRadians(angle));double[] m=identity();if(x==1){m[5]=c;m[6]=-s;m[9]=s;m[10]=c;}else if(y==1){m[0]=c;m[2]=s;m[8]=-s;m[10]=c;}else throw new AssertionError("uncovered axis");multiply(m);}
public static void vertex(double x,double y,double z,double u,double v){vertices.add(new double[]{matrix[0]*x+matrix[1]*y+matrix[2]*z+matrix[3],matrix[4]*x+matrix[5]*y+matrix[6]*z+matrix[7],matrix[8]*x+matrix[9]*y+matrix[10]*z+matrix[11],u,v});}}

