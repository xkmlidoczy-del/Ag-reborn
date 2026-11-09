package cn.academy.port.skill.vectorcombatoracle;
/** Compile-only vector shape for unchanged scalar MathUtils oracle. */
public final class OracleVec {public final double xCoord,yCoord,zCoord;private OracleVec(double x,double y,double z){xCoord=x;yCoord=y;zCoord=z;}public static OracleVec createVectorHelper(double x,double y,double z){return new OracleVec(x,y,z);}}
