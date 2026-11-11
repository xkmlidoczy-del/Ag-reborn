package cn.academy.port.city;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;

/** Immutable per-building plan; columns are pure lookups, independent of chunk order. Foot heights are explicit, not inferred from windows. */
public final class CityInteriorPlan {
    public record Rect(int x0,int z0,int x1,int z1){
        public boolean contains(int x,int z){return x>=x0&&x<=x1&&z>=z0&&z<=z1;}
        public Rect inset(int n){return new Rect(x0+n,z0+n,x1-n,z1-n);}
        public int width(){return x1-x0+1;}public int depth(){return z1-z0+1;}
    }
    public record Floor(int y,Rect rect){}
    public record Room(int floor,int number,Rect rect,String use,int variant){}
    public enum InteractionKind { SEAT,STORAGE,LIGHT,ELEVATOR,READING,SERVICE }
    public record Interaction(String id,BlockPos position,InteractionKind kind){public Interaction{position=position.immutable();}}
    public record Lift(String id,BlockPos controller,int x,int z,int[] floors){
        public Lift{floors=floors.clone();}
        @Override public int[] floors(){return floors.clone();}
        public BlockPos panel(int f){return new BlockPos(x+3,floors[f]+1,z);}
        public Vec3Data centre(int f){return new Vec3Data(x+2,floors[f],z+2);}
        public boolean panelAt(BlockPos p){for(int f=0;f<floors.length;f++)if(panel(f).equals(p))return true;return controller.equals(p);}
    }
    public record Vec3Data(double x,double y,double z){}
    public final String id,theme;public final List<Floor> floors;public final List<Room> rooms;
    public final Rect reservation;public final int coreX,coreZ,stairDepth,corridorX;public final Lift lift;
    public final Direction entrance;private final Map<BlockPos,BlockState> core=new HashMap<>();
    private final Map<BlockPos,String> labels=new HashMap<>();private final int lastHeight;private final boolean hasStairs;private final Map<Integer,List<Room>> byFloor;private final List<CityServicePlan.Site> services;
    private static final BlockState AIR=Blocks.AIR.defaultBlockState();
    public CityInteriorPlan(String id,String theme,List<Floor> floors,Rect reservation,Direction entrance,boolean publicLift){
        this.id=id;this.theme=theme;this.floors=List.copyOf(floors);this.reservation=reservation;this.entrance=entrance;
        if(floors.isEmpty())throw new IllegalArgumentException("No interior floors");
        int x0=Integer.MIN_VALUE,z0=Integer.MIN_VALUE,x1=Integer.MAX_VALUE,z1=Integer.MAX_VALUE,maxRise=4;
        for(int i=0;i<floors.size();i++){Rect r=floors.get(i).rect;x0=Math.max(x0,r.x0);z0=Math.max(z0,r.z0);x1=Math.min(x1,r.x1);z1=Math.min(z1,r.z1);if(i>0)maxRise=Math.max(maxRise,floors.get(i).y-floors.get(i-1).y);}
        hasStairs=floors.size()>1;
        if(hasStairs&&(x1-x0<15||z1-z0<14))throw new IllegalArgumentException("Interior core does not fit "+id+" "+x0+","+z0+".."+x1+","+z1);
        coreX=x0+2;coreZ=z0+3;stairDepth=(maxRise+1)/2+3;corridorX=coreX+9;
        lastHeight=floors.size()>1?floors.get(floors.size()-1).y-floors.get(floors.size()-2).y:6;
        lift=(floors.size()>=4||publicLift&&floors.size()>1)?new Lift(id+":lift",new BlockPos(coreX+5,floors.getFirst().y,coreZ+1),coreX+5,coreZ+1,floors.stream().mapToInt(Floor::y).toArray()):null;
        services=CityServicePlan.SITES.stream().filter(s->reservation.contains(s.home().getX(),s.home().getZ())).toList();
        rooms=List.copyOf(planRooms());byFloor=rooms.stream().collect(java.util.stream.Collectors.groupingBy(Room::floor));if(hasStairs)for(Room r:rooms){int signX=r.rect.x0==corridorX+2?r.rect.x0-1:r.rect.x1+1;labels.put(new BlockPos(signX,floors.get(r.floor).y+1,r.rect.z0+1),String.format("%d%02d  %s",r.floor+1,r.number,r.use));}if(hasStairs)buildStairs();
    }
    private static String[] names(String theme){return switch(theme){
        case "academic"->new String[]{"教室 / CLASSROOM","教师办公室 / STAFF","实验教室 / LAB","卫生间 / WASHROOM"};
        case "residential"->new String[]{"卧室 / BEDROOM","起居室 / LOUNGE","厨房 / KITCHEN","卫浴 / BATHROOM"};
        case "lab"->new String[]{"实验室 / LABORATORY","监测室 / CONTROL","资料室 / ARCHIVE","设备间 / EQUIPMENT"};
        case "library"->new String[]{"阅览室 / READING","资料室 / ARCHIVE","安静自习 / STUDY","借阅与咨询 / LIBRARY"};
        case "clinic"->new String[]{"候诊室 / WAITING","诊室 / CONSULTATION","病床区 / WARD","医护室 / MEDICAL"};
        case "station","airport"->new String[]{"候车休息 / WAITING","服务咨询 / INFORMATION","工作人员 / STAFF","卫生间 / WASHROOM"};
        default->new String[]{"商铺 / SHOP","办公室 / OFFICE","会议室 / MEETING","休息区 / LOUNGE"};
    };}
    private List<Room> planRooms(){
        var list=new ArrayList<Room>();String[] uses=names(theme);
        for(int f=0;f<floors.size();f++){
            Rect r=floors.get(f).rect.inset(2);int n=0;
            for(int z=r.z0+(f==0?2:0);z<=r.z1-4;){int end=Math.min(r.z1,z+7+(int)Math.floorMod(stable(id)+f,3));
                if(sharedCorridor(z,floors.get(f).rect,floors.get(f).y)){z++;continue;}
                for(int zz=z;zz<=end;zz++)if(sharedCorridor(zz,floors.get(f).rect,floors.get(f).y)){end=zz-1;break;}
                if(end-z<4){z=end+1;continue;}
                if(r.x1-corridorX>=5){int v=(int)Math.floorMod(stable(id)+f*17+n*13,3);list.add(new Room(f,++n,new Rect(corridorX+2,z,r.x1,end),uses[Math.floorMod(f+n-1,uses.length)],v));}
                z=end+1;
            }
            int back=coreZ+stairDepth+2;
            for(int z=back;z<=r.z1-4;){int end=r.z1;
                if(sharedCorridor(z,floors.get(f).rect,floors.get(f).y)){z++;continue;}
                for(int zz=z;zz<=end;zz++)if(sharedCorridor(zz,floors.get(f).rect,floors.get(f).y)){end=zz-1;break;}
                if(end-z>=4){int v=(int)Math.floorMod(stable(id)+f*7+n,3);list.add(new Room(f,++n,new Rect(r.x0,z,corridorX-1,end),uses[Math.floorMod(f+n-1,uses.length)],v));}z=end+1;
            }
            if(!hasStairs&&n==0)list.add(new Room(f,1,r,uses[0],(int)Math.floorMod(stable(id)+f,3)));
        }return list;
    }
    public static long stable(String text){long h=0xcbf29ce484222325L;for(int i=0;i<text.length();i++){h^=text.charAt(i);h*=0x100000001b3L;}return h;}
    private void put(int x,int y,int z,BlockState s){core.put(new BlockPos(x,y,z),s);}
    private void buildStairs(){
        // All rises are authored first; headroom is carved afterwards. Every floor has the same two-wide front landing.
        var steps=new ArrayList<BlockPos>();
        for(int f=0;f<floors.size()-1;f++){
            int y=floors.get(f).y,next=floors.get(f+1).y,rise=next-y,up=(rise+1)/2,down=rise-up;
            for(int g=1;g<=up;g++)for(int x=coreX;x<=coreX+1;x++){
                BlockPos p=new BlockPos(x,y+g-1,coreZ+g);core.put(p,Blocks.SMOOTH_QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.SOUTH));steps.add(p);
            }
            for(int x=coreX;x<=coreX+4;x++)for(int z=coreZ+up+1;z<=coreZ+up+2;z++)put(x,y+up-1,z,CityBlocks.block("composite_floor").defaultBlockState());
            for(int g=1;g<=down;g++)for(int x=coreX+3;x<=coreX+4;x++){
                BlockPos p=new BlockPos(x,y+up+g-1,coreZ+up+1-g);core.put(p,Blocks.SMOOTH_QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.NORTH));steps.add(p);
            }
            for(int x=coreX+3;x<=coreX+4;x++)for(int z=coreZ;z<=coreZ+up-down;z++)put(x,next-1,z,CityBlocks.block("composite_floor").defaultBlockState());
        }
        for(BlockPos p:steps)for(int dy=1;dy<=3;dy++)core.putIfAbsent(p.above(dy),AIR);
        for(int f=0;f<floors.size();f++){
            int y=floors.get(f).y;for(int x=coreX;x<=coreX+8;x++)put(x,y-1,coreZ-1,CityBlocks.block("composite_floor").defaultBlockState());
            BlockPos label=new BlockPos(coreX+2,y+1,coreZ);
            labels.put(label,"F"+(f+1)+"  楼梯 / STAIRS");core.put(label,Blocks.BIRCH_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING,Direction.NORTH));
            put(coreX+2,y+1,coreZ+1,CityBlocks.block("brushed_steel").defaultBlockState());
        }
    }
    public int ceiling(){return floors.getLast().y+lastHeight-1;}
    public int floorIndex(int y){int result=-1;for(int i=0;i<floors.size();i++){if(floors.get(i).y<=y+1)result=i;else break;}return result;}
    public boolean contains(int x,int z){for(Floor f:floors)if(f.rect.contains(x,z))return true;return false;}
    private boolean transportCorridor(int z,Rect r){return theme.equals("airport")?(Math.abs(z+1848)<=1||Math.abs(z+1829)<=1):theme.equals("station")&&(Math.abs(z-(r.z0+16))<=1||Math.abs(z-(r.z0+35))<=1);}
    private boolean sharedCorridor(int z,Rect r,int feet){
        if(transportCorridor(z,r))return true;
        boolean grand=id.startsWith("grand_complex:")&&((feet==81&&(id.endsWith("tower_0")||id.endsWith("tower_2")||id.endsWith("tower_4")))||(feet==153&&(id.endsWith("tower_0")||id.endsWith("tower_2"))));
        boolean low=id.startsWith("lowrise:")&&theme.equals("lab")&&floors.size()==3&&(feet==5||feet==10);
        return (grand||low)&&Math.abs(z-(r.z0+r.z1)/2)<=1;
    }
    public BlockState block(int x,int y,int z){
        if(y<floors.getFirst().y-1||y>ceiling())return null;
        if(!floors.getFirst().rect.contains(x,z))return null;
        int fi=floorIndex(y);if(fi<0)return null;Floor f=floors.get(fi);Rect r=f.rect;int h=y-f.y;
        if(!r.contains(x,z))return null;
        // Registered entries connect to the actual street-side doorway on the public ground floor.
        int middle=(r.x0+r.x1)/2;
        if(fi==0&&x>=middle-1&&x<=middle+1&&h>=0&&h<=2&&((entrance==Direction.NORTH&&z<=r.z0+2)||(entrance==Direction.SOUTH&&z>=r.z1-2)))return AIR;
        if(theme.equals("airport")&&fi<3&&transportCorridor(z,r)&&((x>=1589&&x<=1594)||(x>=1649&&x<=1654))){if(h>=0&&h<=2)return AIR;if(h==-1)return CityBlocks.block("composite_floor").defaultBlockState();}
        if(x<r.x0+2||x>r.x1-2||z<r.z0+2||z>r.z1-2)return null;
        BlockPos pos=new BlockPos(x,y,z);
        for(var site:services){if(pos.equals(site.home().below()))return Blocks.CYAN_GLAZED_TERRACOTTA.defaultBlockState();
            if(y>=site.home().getY()&&y<=site.home().getY()+2){int x0=site.home().getX(),x1=site.shelf().getX(),z0=site.home().getZ(),z1=site.shelf().getZ();if(x>=Math.min(x0,x1)-1&&x<=Math.max(x0,x1)+1&&z>=Math.min(z0,z1)-1&&z<=Math.max(z0,z1)+1)return AIR;}}
        BlockState exact=core.get(pos);if(exact!=null)return exact;
        boolean stair=x>=coreX&&x<=coreX+4&&z>=coreZ&&z<coreZ+stairDepth;
        if(y==ceiling())return light(fi,x,z)?CityInteriorContent.state("interior_lamp"):CityBlocks.block("white_panel_wall").defaultBlockState();
        if(stair&&hasStairs){if(h==-1&&(fi==0||z==coreZ))return CityBlocks.block("composite_floor").defaultBlockState();return AIR;}
        if(lift!=null&&x>=lift.x&&x<=lift.x+3&&z>=lift.z&&z<=lift.z+3){
            int a=x-lift.x,b=z-lift.z;boolean edge=a==0||a==3||b==0||b==3;
            if(pos.equals(lift.controller))return CityInteriorContent.state("elevator_controller");
            if(a==3&&b==0&&h==1)return CityInteriorContent.state("elevator_panel");
            if(b==0&&(a==1||a==2)&&h>=0&&h<=2)return CityInteriorContent.state("elevator_gate").setValue(HorizontalDirectionalBlock.FACING,Direction.NORTH).setValue(CityInteriorContent.RIGHT,a==2);
            if(!edge&&fi==0&&h==-1)return CityBlocks.block("composite_floor").defaultBlockState();
            if(edge&&a==0&&b==2&&h==1)return CityInteriorContent.state("wall_light").setValue(HorizontalDirectionalBlock.FACING,Direction.EAST);
            if(edge&&b==3&&h>=0&&h<=2)return CityBlocks.block("curtain_glass_clear").defaultBlockState();
            return edge?CityBlocks.block("brushed_steel").defaultBlockState():AIR;
        }
        if(h==-1)return fi>0&&light(fi-1,x,z)?CityInteriorContent.state("interior_lamp"):CityBlocks.block("composite_floor").defaultBlockState();
        if(y==ceiling())return light(fi,x,z)?CityInteriorContent.state("interior_lamp"):CityBlocks.block("white_panel_wall").defaultBlockState();
        if(label(pos)!=null)return Blocks.BIRCH_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING,x==corridorX?Direction.EAST:Direction.WEST);
        boolean corridor=hasStairs&&(x==corridorX||x==corridorX+1||x<corridorX&&(z==coreZ-2||z==coreZ-1||z==coreZ+stairDepth||z==coreZ+stairDepth+1));
        if(fi==0&&((entrance==Direction.NORTH&&z<=r.z0+3)||(entrance==Direction.SOUTH&&z>=r.z1-3)))corridor=true;
        if(sharedCorridor(z,r,f.y))corridor=true;
        if(corridor)return AIR;
        // A service's existing standing points and its short shelf route remain authoritative.
        for(var site:services){if(y>=site.home().getY()&&y<=site.home().getY()+2){int x0=site.home().getX(),x1=site.shelf().getX(),z0=site.home().getZ(),z1=site.shelf().getZ();if(x>=Math.min(x0,x1)-1&&x<=Math.max(x0,x1)+1&&z>=Math.min(z0,z1)-1&&z<=Math.max(z0,z1)+1)return AIR;}}
        if(fi==0&&f.y==0&&y<5){
            var life=CityLifeRooms.at(x,z);if(life!=null){BlockState old=life.block(y);if(old!=null&&!old.isAir())return modern(old,y);}
            var service=CityServicePlan.at(x,z);if(service!=null){BlockState old=service.block(y);if(old!=null&&!old.isAir())return modern(old,y);}
        }
        for(Room room:byFloor.getOrDefault(fi,List.of()))if(room.rect.contains(x,z))return roomBlock(room,x,h,z);
        return AIR;
    }
    private BlockState roomBlock(Room room,int x,int h,int z){
        Rect r=room.rect;int door=(r.z0+r.z1)/2;boolean side=r.x0==corridorX+2;
        if(!hasStairs){int a=x-r.x0,b=z-r.z0;
            if(h==0&&b==r.depth()-2&&a>0)return CityInteriorContent.state(room.variant==1?"lab_bench":"counter");
            if(h==0&&a==r.width()-2&&b>1&&b<r.depth()-2)return CityInteriorContent.state(room.variant==2?"sofa":"bench");
            if(h==1&&b==r.depth()-1&&a==r.width()/2)return CityInteriorContent.state("wall_monitor");return AIR;
        }
        if(x==(side?r.x0:r.x1)){
            if(z==door||z==door+1){if(h==0||h==1)return Blocks.BIRCH_DOOR.defaultBlockState().setValue(DoorBlock.FACING,side?Direction.EAST:Direction.WEST).setValue(DoorBlock.HALF,h==0?DoubleBlockHalf.LOWER:DoubleBlockHalf.UPPER).setValue(DoorBlock.HINGE,z==door?DoorHingeSide.LEFT:DoorHingeSide.RIGHT);return AIR;}
            if(h==1&&Math.floorMod(z,6)==0)return CityInteriorContent.state("wall_light").setValue(HorizontalDirectionalBlock.FACING,side?Direction.EAST:Direction.WEST);
            return h==2?CityBlocks.block("thin_glass_panel").defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,Direction.EAST):CityBlocks.block("white_panel_wall").defaultBlockState();
        }
        if(z==r.z0){if(h==1&&Math.floorMod(x,6)==0)return CityInteriorContent.state("wall_light").setValue(HorizontalDirectionalBlock.FACING,Direction.SOUTH);return CityBlocks.block(h==2?"thin_glass_panel":"white_panel_wall").defaultBlockState();}

        if(h>1)return AIR;
        int a=x-r.x0,b=z-r.z0,w=r.width(),d=r.depth();
        // Two cells of clear approach on the room side of each double doorway. No furnishing can block egress.
        if(z>=door-1&&z<=door+2&&(side?a<=2:a>=w-3))return AIR;
        boolean wash=room.use.contains("卫生")||room.use.contains("卫浴");
        if(wash){int sinkB=room.variant==0?d-2:room.variant==1?1:Math.max(1,d-3);if(h==0&&a==(side?w-2:1)&&b==d-2)return CityInteriorContent.state("toilet");if(h==0&&b==sinkB&&(a==(side?w-3:2)||a%3==room.variant))return CityInteriorContent.state("sink");return AIR;}
        if(room.use.contains("卧室")||room.use.contains("病床")){
            int bedA=side?w-3+(room.variant==1?1:0):2+(room.variant==1?1:0),footB=room.variant==0?d-3:room.variant==1?2:d-2;
            if(side&&w>=13&&a>=4&&a<w-5&&a%6==4){
                if(h==0&&(b==footB||b==footB-1))return Blocks.LIGHT_BLUE_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.NORTH).setValue(BedBlock.PART,b==footB?BedPart.FOOT:BedPart.HEAD);
                int tableB=footB<d/2?d-2:1;if(h==0&&b==tableB)return CityInteriorContent.state("study_desk");if(h==0&&b==(tableB==1?2:tableB-1))return CityInteriorContent.state("chair");
            }
            if(h==0&&a==bedA&&(b==footB||b==footB-1))return Blocks.LIGHT_BLUE_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.NORTH).setValue(BedBlock.PART,b==footB?BedPart.FOOT:BedPart.HEAD);
            if(h==0&&a==bedA+(side?-1:1)&&b==footB-1)return CityInteriorContent.state("bedside");if(h<=1&&a==(side?w-2:1)&&b==d-2&&a!=bedA)return CityInteriorContent.state("locker");int deskB=footB<d/2?d-2:1;if(h==0&&a==bedA&&b==deskB)return CityInteriorContent.state("study_desk");if(h==0&&a==bedA&&b==(deskB==1?2:deskB-1))return CityInteriorContent.state("chair");return AIR;
        }
        if(room.use.contains("资料")||theme.equals("library")){
            if(h<=1&&a==(side?w-2:1)&&b>0&&b<d-1&&(room.variant!=2||b%3!=1))return Blocks.BOOKSHELF.defaultBlockState();
            if(side&&w>=14&&a>=4&&a<w-5&&a%5==4){
                if(room.use.contains("资料")&&h<=1&&b>=2&&b<d-1)return Blocks.BOOKSHELF.defaultBlockState();
                int row=room.variant==1?1:d-3;if(!room.use.contains("资料")&&h==0&&(b==row||b==row+1))return CityInteriorContent.state(b==row?"study_desk":"chair");
            }
            int deskA=side?w-3:2,deskB=room.variant==0?d-3:room.variant==1?1:Math.max(1,d/2);
            if(h==0&&a==deskA&&b==deskB)return CityInteriorContent.state("study_desk");if(h==0&&a==deskA&&b==deskB+1)return CityInteriorContent.state("chair");return AIR;
        }
        if(theme.equals("lab")||room.use.contains("实验")){
            if(h==0&&a==(side?w-2:1)&&b>1&&b<d-1)return CityInteriorContent.state((b+room.variant)%3==0?"equipment_rack":"lab_bench");
            if(side&&w>=14&&a>=4&&a<w-4&&a%5==4&&(b==2||b==d-3)){
                if(h==0)return CityInteriorContent.state("lab_bench");if(h==1&&b==d-3&&a%2==0)return CityInteriorContent.state("wall_monitor");
            }
            if(h==1&&a==(side?w-2:1)&&b==d-2)return CityInteriorContent.state("wall_monitor");if(h==0&&a==(side?w-3:2)&&b==d-3)return CityInteriorContent.state("office_desk");return AIR;
        }
        if(theme.equals("academic")&&room.use.contains("教室")){
            if(h==1&&b==1&&a>=2&&a<=w-3)return CityInteriorContent.state("wall_monitor").setValue(HorizontalDirectionalBlock.FACING,Direction.SOUTH);
            if(h==0&&a==(side?w-2:2)&&(b==2||b==3))return CityInteriorContent.state(b==2?"study_desk":"chair");
            if(h==0&&a>=2&&a<w-1&&a%3==(room.variant%2==0?2:0)&&b>=2&&b<d-1){if(b%4==2)return CityInteriorContent.state("study_desk");if(b%4==3)return CityInteriorContent.state("chair");}return AIR;
        }
        if(room.use.contains("办公室")||room.use.contains("工作人员")||room.use.contains("医护")||room.use.contains("咨询")){
            int deskB=room.variant==0?d-3:room.variant==1?1:Math.max(1,d/2);if(h==0&&a>=2&&a<w-1&&(a%3==room.variant||a==(side?w-2:2))){if(b==deskB)return CityInteriorContent.state("office_desk");if(b==deskB+1)return CityInteriorContent.state("chair");}
            if(h==0&&a==w-2&&b==2)return CityInteriorContent.state("cabinet");return AIR;
        }
        if(room.use.contains("会议")){
            int row=room.variant==0?d/2:room.variant==1?Math.max(2,d/2-1):Math.min(d-3,d/2+1);if(h==0&&a>=2&&a<w-1&&b==row)return CityInteriorContent.state("study_desk");
            if(h==0&&a>=2&&a<w-1&&a%2==0&&(b==row-1||b==row+1))return CityInteriorContent.state("chair").setValue(HorizontalDirectionalBlock.FACING,b<row?Direction.SOUTH:Direction.NORTH);return AIR;
        }
        if(room.use.contains("起居")||room.use.contains("休息")){
            int row=room.variant==1?1:d-2;if(h==0&&a>=2&&a<w-1&&b==row)return CityInteriorContent.state(room.variant==2?"bench":"sofa").setValue(HorizontalDirectionalBlock.FACING,row==1?Direction.SOUTH:Direction.NORTH);
            if(h==0&&a==w/2&&b==(row==1?3:d-4))return CityInteriorContent.state("counter");if(h==0&&a==w-2&&b==2)return Blocks.POTTED_FERN.defaultBlockState();return AIR;
        }
        if(room.use.contains("商铺")){
            if(h==0&&side&&w>=14&&a>=4&&a<w-4&&a%6==4&&(b==2||b==d-3))return CityInteriorContent.state(room.variant==2?"equipment_rack":"cabinet");
            if(h==0&&b==d-2&&a>=2&&a<w-1)return CityInteriorContent.state(a%3==0?"cabinet":"counter");
            if(h==0&&a==w-2&&b>=2&&b<d-2)return CityInteriorContent.state(room.variant==0?"equipment_rack":"cabinet");
            if(h==1&&a==w-2&&b==d-3)return CityInteriorContent.state("wall_monitor");return AIR;
        }
        if(room.use.contains("候车")||room.use.contains("候诊")){
            if(h==0&&b>=1&&b<d-1&&b%3==room.variant&&a>=2&&a<w-1&&a%8<6)return CityInteriorContent.state("bench");
            if(h==0&&a==w-2&&b==d-2)return CityInteriorContent.state("cabinet");return AIR;
        }
        if(room.use.contains("厨房")){if(h==0&&b==(room.variant==1?1:d-2)&&a>0)return CityInteriorContent.state(a==(side?w-2:1)||(a+room.variant)%3==0?"sink":"cabinet");return AIR;}
        if(h==0&&a==w-2&&b==d-2)return CityInteriorContent.state("cabinet");
        if(h==0&&a>=2&&a<w-1&&b==d-3)return CityInteriorContent.state(room.variant==0?"office_desk":room.variant==1?"sofa":"bench");
        if(h==0&&a==2&&b==d-2&&room.variant==0)return CityInteriorContent.state("chair");return AIR;
    }
    private BlockState modern(BlockState old,int y){
        if(old.is(Blocks.SPRUCE_STAIRS)||old.is(Blocks.OAK_STAIRS))return CityInteriorContent.state("chair");
        if(old.is(Blocks.BARREL))return CityInteriorContent.state(theme.equals("clinic")?"bedside":"cabinet");
        if(old.is(Blocks.SPRUCE_FENCE))return CityInteriorContent.state("study_desk");
        if(old.is(Blocks.SPRUCE_TRAPDOOR))return AIR;
        if(old.is(Blocks.SPRUCE_SLAB)||old.is(Blocks.SMOOTH_QUARTZ_SLAB))return CityInteriorContent.state("counter");
        // New rooms already have ceiling-aligned panels. Do not retain R5's mid-air cube lights.
        if(old.is(CityBlocks.block("ceiling_light")))return AIR;return old;
    }
    private boolean light(int f,int x,int z){if(x==corridorX&&Math.floorMod(z,5)==0)return true;for(Room r:byFloor.getOrDefault(f,List.of()))if(r.rect.contains(x,z)&&x>r.rect.x0&&x<r.rect.x1&&z>r.rect.z0&&z<r.rect.z1&&(Math.floorMod(x-r.rect.x0,6)==2&&Math.floorMod(z-r.rect.z0,5)==2||x==(r.rect.x0+r.rect.x1)/2&&z==(r.rect.z0+r.rect.z1)/2))return true;return false;}
    public String label(BlockPos p){return labels.get(p);}
    public Interaction interactionAt(BlockPos at){
        if(!contains(at.getX(),at.getZ()))return null;
        for(var site:services)if(site.home().equals(at))return new Interaction(id+":service:"+site.role().name(),at,InteractionKind.SERVICE);
        return interaction(id,at,block(at.getX(),at.getY(),at.getZ()));
    }
    static Interaction interaction(String owner,BlockPos at,BlockState state){
        if(state==null)return null;InteractionKind kind=null;
        if(state.getBlock()instanceof CityInteriorContent.Storage)kind=InteractionKind.STORAGE;
        else if(state.getBlock()instanceof CityInteriorContent.Gate||state.getBlock()instanceof CityInteriorContent.Controller)kind=InteractionKind.ELEVATOR;
        else if(state.getBlock()instanceof CityInteriorContent.Furnishing furnishing)kind=switch(furnishing.id){case "chair","bench","sofa"->InteractionKind.SEAT;case "interior_lamp","wall_light"->InteractionKind.LIGHT;case "elevator_panel"->InteractionKind.ELEVATOR;default->null;};
        else if(state.is(Blocks.LECTERN))kind=InteractionKind.READING;
        return kind==null?null:new Interaction(owner+":point:"+at.asLong(),at,kind);
    }
}
