/* AcademyCraft1.0.7 WindGenerator/TileWindGenBase/TileWindGenMain, GPLv3; see NOTICE. */
package cn.academy.port.wind;

import java.util.ArrayList;
import java.util.List;

/** Literal original root rules. Obstructions, weather, time and dimension do not gate generation. */
public final class ClassicWindRules {
    public static final int MIN_PILLARS=8,MAX_PILLARS=40;
    public static final double CAPACITY=20000,BANDWIDTH=300,MAX_GENERATION=15;
    public enum Kind { BASE,PILLAR,MAIN }
    public enum Facing { NORTH,EAST,SOUTH,WEST }
    public enum Completeness { BASE_ONLY,NO_TOP,COMPLETE,COMPLETE_NOT_WORKING }
    public enum CellKind { OTHER,PILLAR,MAIN_ORIGIN,MAIN_PART,BASE }
    public record Cell(int x,int y,int z) {}
    public record Tower(Completeness completeness,int mainOffset) {}
    private ClassicWindRules(){}
    public static Facing fromYaw(float yaw){return Facing.values()[(int)Math.floor(yaw*4.0F/360.0F+.5D)&3];}
    public static Cell rotate(Cell cell,Facing facing){return switch(facing){case NORTH->cell;case EAST->new Cell(-cell.z,cell.y,cell.x);case SOUTH->new Cell(-cell.x,cell.y,-cell.z);case WEST->new Cell(cell.z,cell.y,-cell.x);};}
    public static int parts(Kind kind){return kind==Kind.BASE?2:kind==Kind.MAIN?3:1;}
    public static Cell offset(Kind kind,int part,Facing facing){if(part<0||part>=parts(kind))throw new IllegalArgumentException("Invalid wind part");return rotate(kind==Kind.BASE?new Cell(0,part,0):kind==Kind.MAIN?new Cell(0,0,part==1?-1:part==2?1:0):new Cell(0,0,0),facing);}
    public static List<Cell> obstaclePlane(Facing facing){var result=new ArrayList<Cell>(168);for(int i=-6;i<=6;i++)for(int j=-6;j<=6;j++)if(i!=0||j!=0)result.add(rotate(new Cell(i,j,-1),facing));return List.copyOf(result);}
    /** Input begins two cells above the base origin; the upper base cell is skipped. */
    public static Tower scanBase(List<CellKind> upward){int pillars=0;for(int i=0;i<upward.size();i++){var cell=upward.get(i);if(cell==CellKind.PILLAR){if(++pillars>MAX_PILLARS)return new Tower(Completeness.NO_TOP,0);}else if(cell==CellKind.MAIN_ORIGIN)return new Tower(pillars>=MIN_PILLARS?Completeness.COMPLETE:Completeness.NO_TOP,pillars>=MIN_PILLARS?i+2:0);else if(cell==CellKind.MAIN_PART)return new Tower(Completeness.NO_TOP,0);else return new Tower(pillars<MIN_PILLARS?Completeness.BASE_ONLY:Completeness.NO_TOP,0);}return new Tower(pillars<MIN_PILLARS?Completeness.BASE_ONLY:Completeness.NO_TOP,0);}
    /** Original main scans down from mainY-1, accepts the upper base block, and counts8–40 pillars. */
    public static boolean scanMain(List<CellKind> downward){int pillars=0;for(var cell:downward){if(cell==CellKind.PILLAR){if(++pillars>MAX_PILLARS)return false;}else return cell==CellKind.BASE&&pillars>=MIN_PILLARS;}return false;}
    public static boolean generates(Completeness completeness,boolean mainComplete,boolean fan){return completeness==Completeness.COMPLETE&&mainComplete&&fan;}
    public static double generation(int mainY,boolean working){return working?(0.5+0.5*Math.max(0,Math.min(1,(mainY-70.0)/90.0)))*MAX_GENERATION:0;}
    public static Completeness presentation(Completeness completeness,boolean working){return completeness==Completeness.COMPLETE&&!working?Completeness.COMPLETE_NOT_WORKING:completeness;}
    public static double finiteEnergy(double amount){return Double.isFinite(amount)?Math.max(0,Math.min(CAPACITY,amount)):0;}
    public static int word(double amount,int index){return (int)(Double.doubleToLongBits(finiteEnergy(amount))>>>(index*16))&65535;}
    public static double fromWords(int a,int b,int c,int d){return finiteEnergy(Double.longBitsToDouble((a&65535L)|((b&65535L)<<16)|((c&65535L)<<32)|((d&65535L)<<48)));}
}
