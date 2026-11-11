package cn.academy.port.city;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** Adapters for authored towers, procedural towers, low-rise lots and public district landmarks. */
@EventBusSubscriber(modid="academy")
public final class CityInteriors {
    private static final Gson GSON=new Gson();
    private static final JsonObject CATALOG=readCatalog();
    public record ServicePoint(String role,BlockPos home,BlockPos shelf,String shop){}
    public record Surface(int y,CityInteriorPlan.Rect bounds,String shape,BlockPos entrance){}
    public static List<ServicePoint> services(){return CityServicePlan.SITES.stream().map(s->new ServicePoint(s.role().name(),s.home(),s.shelf(),s.shop())).toList();}
    public static void roomLighting(ServerLevel w,BlockPos switchAt,boolean lit){
        if(!(w.getChunkSource().getGenerator()instanceof CityGreyboxGenerator generator))return;
        var group=generator.interiorAt(switchAt.getX(),switchAt.getZ());if(group==null)return;
        for(var plan:group.parts()){
            int floor=plan.floorIndex(switchAt.getY());if(floor<0||floor>=plan.floors.size())continue;
            for(var room:plan.rooms)if(room.floor()==floor&&room.rect().contains(switchAt.getX(),switchAt.getZ())){
                int y=floor+1<plan.floors.size()?plan.floors.get(floor+1).y()-1:plan.ceiling();var r=room.rect();
                for(int x=r.x0();x<=r.x1();x++)for(int z=r.z0();z<=r.z1();z++){
                    if(w.getChunkSource().getChunkNow(x>>4,z>>4)==null)continue;var p=new BlockPos(x,y,z);var s=w.getBlockState(p);if(s.is(CityInteriorContent.block("interior_lamp")))w.setBlock(p,s.setValue(CityInteriorContent.LIT,lit),3);
                }return;
            }
        }
    }
    private static JsonObject readCatalog(){try(var stream=CityInteriors.class.getResourceAsStream("/data/academy/city/interior_shells_v1.json")){
        if(stream==null)throw new IllegalStateException("Missing city interior shell catalog");return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
    }catch(IOException e){throw new IllegalStateException(e);}}
    public record Group(String id,CityInteriorPlan.Rect reservation,List<CityInteriorPlan> parts,Map<BlockPos,BlockState> accents,Map<BlockPos,String> notices,int top){
        public Group(String id,CityInteriorPlan.Rect reservation,List<CityInteriorPlan> parts){this(id,reservation,parts,Map.of(),Map.of());}
        public Group(String id,CityInteriorPlan.Rect reservation,List<CityInteriorPlan> parts,Map<BlockPos,BlockState> accents,Map<BlockPos,String> notices){this(id,reservation,parts,accents,notices,0);}
        public Group{parts=List.copyOf(parts);accents=Map.copyOf(accents);notices=Map.copyOf(notices);top=Math.max(parts.stream().mapToInt(CityInteriorPlan::ceiling).max().orElse(0),accents.keySet().stream().mapToInt(BlockPos::getY).max().orElse(0));}
        public BlockState block(int x,int y,int z){
            if(!accents.isEmpty()){BlockState specific=accents.get(new BlockPos(x,y,z));if(specific!=null)return specific;}
            for(var p:parts){BlockState b=p.block(x,y,z);if(b!=null)return b;}
            if(id.startsWith("lowrise:")&&y>=0){
                for(var p:parts){var r=p.floors.getFirst().rect();if(!r.contains(x,z)||y>p.ceiling())continue;
                    if(y==p.ceiling())return CityBlocks.block("reinforced_concrete").defaultBlockState();
                    boolean edge=x==r.x0()||x==r.x1()||z==r.z0()||z==r.z1();
                    if(edge){if(z==r.z0()&&Math.abs(x-(r.x0()+r.x1())/2)<=1&&y<3)return Blocks.AIR.defaultBlockState();
                        return CityBlocks.block(y%5==0?"blue_panel_wall":y%5==2||y%5==3?"curtain_glass_clear":"white_panel_wall").defaultBlockState();}
                    return Blocks.AIR.defaultBlockState();
                }
                int a=x-reservation.x0(),b=z-reservation.z0();if(a>=3&&a<=45&&b>=6&&b<=31&&y<=top())return Blocks.AIR.defaultBlockState();
            }
            return null;
        }
        public CityInteriorPlan plan(int x,int z){for(var p:parts)if(p.contains(x,z))return p;return null;}
        public CityInteriorPlan.Interaction interactionAt(BlockPos at){
            var explicit=CityInteriorPlan.interaction(id,at,accents.get(at));if(explicit!=null)return explicit;
            for(var p:parts){var point=p.interactionAt(at);if(point!=null)return point;}return null;
        }
        public List<Surface> surfaces(){
            if(!parts.isEmpty())return parts.stream().flatMap(p->p.floors.stream().map(f->new Surface(f.y(),f.rect(),"rectangle",new BlockPos(p.corridorX,f.y(),p.coreZ-1)))).toList();
            if(id.startsWith("public_detail:3:"))return List.of(new Surface(1,reservation.inset(6),"circle",new BlockPos(reservation.x0()+23,1,reservation.z0()+43)));
            if(id.startsWith("public_detail:25:"))return List.of(new Surface(93,reservation.inset(1),"rectangle",new BlockPos(-1753,93,-819)));
            if(id.startsWith("public_detail:36:"))return List.of(new Surface(0,reservation.inset(1),"rectangle",new BlockPos(1479,0,-1755)));
            return List.of();
        }
    }
    public static final class Planner {
        private final long seed;private volatile CityInteriorVersions.Policy policy=CityInteriorVersions.Policy.unavailable();
        private final Map<String,Optional<Group>> cache=Collections.synchronizedMap(new LinkedHashMap<>(64,.75F,true){@Override protected boolean removeEldestEntry(Map.Entry<String,Optional<Group>> e){return size()>96;}});
        public Planner(long seed){this.seed=seed;}
        public void policy(CityInteriorVersions.Policy p){policy=p;cache.clear();}
        public Group at(int x,int z){
            Group small=smallAt(x,z);if(small!=null)return small;
            var site=CityTemplateSites.at(seed,x,z);var land=CityDistrictLandmarks.at(seed,x,z);
            int ox,oz;String key;
            if(site!=null){ox=site.originX();oz=site.originZ();key="lot:"+ox+":"+oz;}
            else if(land!=null&&eligibleLandmark(land.type())){ox=x-land.a();oz=z-land.b();key="land:"+land.type()+":"+ox+":"+oz;}
            else {
                var sample=CityPlan.sample(seed,x,z);if(sample.kind()!=CityPlan.Kind.BUILDING||sample.bw()==0)return smallAt(x,z);
                ox=x-sample.a();oz=z-sample.b();key="proc:"+ox+":"+oz;
            }
            Optional<Group> value=cache.get(key);if(value==null){value=Optional.ofNullable(build(x,z,site,land));cache.put(key,value);}return value.orElse(null);
        }
        private Group smallAt(int x,int z){
            var list=new ArrayList<int[]>();Collections.addAll(list,new int[]{-242,-57,-231,-49},new int[]{-82,428,-71,437},new int[]{-92,566,-78,583},new int[]{-52,566,-38,583});
            for(int station=1;station<=3;station++){int z0=cn.academy.port.train.NorthSouthLine.STATIONS.get(station).z();list.add(new int[]{106,z0+76,128,z0+94});}
            int[][] boxes=list.toArray(int[][]::new);
            String[] themes={"clinic","station","commercial","commercial","station","station","station"};
            for(int i=0;i<boxes.length;i++){int[] b=boxes[i];if(x<b[0]||x>b[2]||z<b[1]||z>b[3])continue;String id="public_small:"+i;Optional<Group> v=cache.get(id);if(v==null){var r=new CityInteriorPlan.Rect(b[0],b[1],b[2],b[3]);v=Optional.ofNullable(allowed(r)?group(id,themes[i],r,List.of(new CityInteriorPlan.Floor(0,r)),Direction.SOUTH,false):null);cache.put(id,v);}return v.orElse(null);}return null;
        }
        private boolean allowed(CityInteriorPlan.Rect r){return policy.allows(r.x0(),r.z0(),r.x1(),r.z1());}
        private Group build(int x,int z,CityTemplateSites.Site site,CityDistrictLandmarks.Column land){
            if(site!=null){var reservation=new CityInteriorPlan.Rect(site.originX(),site.originZ(),site.originX()+site.reservedWidth()-1,site.originZ()+site.reservedDepth()-1);if(!allowed(reservation))return null;
                var low=CityDistrictBlocks.at(seed,x,z,site);if(low!=null)return lowRise(site,reservation,low);
                return authored(site.id(),site.originX(),site.originZ(),site.reservedWidth(),site.reservedDepth());
            }
            if(land!=null&&eligibleLandmark(land.type()))return landmark(land);
            var s=CityPlan.sample(seed,x,z);if(s.kind()!=CityPlan.Kind.BUILDING||s.bw()==0)return null;
            int a=x-s.a(),b=z-s.b();var r=new CityInteriorPlan.Rect(a,b,a+s.bw()-1,b+s.bd()-1);
            if(!allowed(r))return null;var floors=new ArrayList<CityInteriorPlan.Floor>();for(int y=0;y<s.height();y+=4)floors.add(new CityInteriorPlan.Floor(y,r));
            return group("procedural:"+a+":"+b,theme(CityDistrictZoning.at(s.district(),x,z)),r,floors,Direction.NORTH,false);
        }
        private Group lowRise(CityTemplateSites.Site site,CityInteriorPlan.Rect reservation,CityDistrictBlocks.Column low){
            int ox=site.originX(),oz=site.originZ();var parts=new ArrayList<CityInteriorPlan>();var use=low.use();
            int[][] boxes=switch(use){
                case SCIENCE->new int[][]{{4,8,20,28,15},{27,8,43,28,15}};
                case RESIDENTIAL->new int[][]{{3,9,15,30,12},{18,9,30,30,12},{33,9,45,30,12}};
                case ACADEMIC->new int[][]{{4,8,43,29,15}};
                case CIVIC->new int[][]{{4,9,43,30,16}};
                case COMMERCIAL,LEISURE->new int[][]{{4,8,43,27,10}};
                case INDUSTRIAL->new int[][]{{4,8,43,29,10}};
                case SPORTS->new int[][]{{4,6,43,24,10}};
                default->new int[][]{};
            };
            for(int i=0;i<boxes.length;i++){
                int[] b=boxes[i];
                // Narrow small houses retain a low-rise exterior but use a common usable apartment footprint.
                if(b[2]-b[0]<16){if(i>0)continue;b=new int[]{4,9,43,30,12};}
                var r=new CityInteriorPlan.Rect(ox+b[0],oz+b[1],ox+b[2],oz+b[3]);var floors=new ArrayList<CityInteriorPlan.Floor>();for(int y=0;y+4<=b[4];y+=5)floors.add(new CityInteriorPlan.Floor(y,r));
                parts.add(new CityInteriorPlan("lowrise:"+ox+":"+oz+":"+i,theme(use),floors,reservation,Direction.NORTH,true));
            }
            if(parts.isEmpty())return null;
            var accents=new HashMap<BlockPos,BlockState>();
            if(use==CityDistrictZoning.Use.SCIENCE&&parts.size()==2)
                for(int y:new int[]{5,10})bridge(accents,parts.get(0),parts.get(1),y,false);
            return new Group("lowrise:"+ox+":"+oz,reservation,parts,accents,Map.of());
        }
        private Group landmark(CityDistrictLandmarks.Column l){
            int ox=l.x()-l.a(),oz=l.z()-l.b(),type=l.type();
            if(type==3||type==25||type==36)return landmarkDetails(l,policy);
            if(type==4||type==11){var reserved=new CityInteriorPlan.Rect(ox,oz,ox+47,oz+47);if(!allowed(reserved))return null;var shops=new ArrayList<CityInteriorPlan>();for(int i=0;i<4;i++){var r=new CityInteriorPlan.Rect(ox+4+i*10,oz+6,ox+14+i*10,oz+21);shops.add(new CityInteriorPlan("market:"+type+":"+ox+":"+oz+":"+i,"commercial",List.of(new CityInteriorPlan.Floor(0,r)),reserved,Direction.SOUTH,false));}return new Group("market:"+type+":"+ox+":"+oz,reserved,shops);}
            int[] bounds;String theme;int[] ys;Direction entry=Direction.SOUTH;
            switch(type){
                case 1->{bounds=new int[]{6,7,41,37};theme="commercial";ys=new int[]{0,7,13,19};}
                case 5,7->{bounds=new int[]{4,4,43,27};theme="academic";ys=new int[]{0,7,13};}
                case 32->{bounds=new int[]{4,4,43,27};theme="lab";ys=new int[]{0,6,11,16};}
                case 40->{bounds=new int[]{4,8,43,33};theme="library";ys=new int[]{0,7,13};entry=Direction.NORTH;}
                case 17->{bounds=new int[]{4,18,43,41};theme="lab";ys=new int[]{0,7};}
                case 23->{bounds=new int[]{0,0,59,59};theme="airport";ys=new int[]{0,7,13,19};}
                case 33,34->{bounds=new int[]{0,0,47,51};theme="airport";ys=new int[]{0,7,13};}
                default->{bounds=new int[]{6,7,41,30};theme="commercial";ys=new int[]{0,6};}
            }
            var r=new CityInteriorPlan.Rect(ox+bounds[0],oz+bounds[1],ox+bounds[2],oz+bounds[3]);
            // Airport wings form one connected facility. A legacy chunk in any wing keeps all wings legacy.
            var reserved=(type==23||type==33||type==34)?new CityInteriorPlan.Rect(1544,-1868,1699,-1808):new CityInteriorPlan.Rect(ox,oz,ox+47,oz+47);
            if(!allowed(reserved))return null;var floors=new ArrayList<CityInteriorPlan.Floor>();for(int y:ys)floors.add(new CityInteriorPlan.Floor(y,r));
            var group=group("landmark:"+type+":"+ox+":"+oz,theme,reserved,floors,entry,true);
            if(type==32){var accents=new HashMap<BlockPos,BlockState>();
                accents.put(new BlockPos(-225,6,-105),CityInteriorContent.state("equipment_rack"));
                accents.put(new BlockPos(-225,7,-105),CityBlocks.block("brushed_steel").defaultBlockState());
                accents.put(new BlockPos(-221,0,-95),Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING,Direction.SOUTH).setValue(LecternBlock.HAS_BOOK,true));
                return new Group(group.id,reserved,group.parts,accents,Map.of());}
            if(type==40){
                // The original transfer lectern occupied the new shaft's corner. Keep its existing
                // free reference service in the reading room, without refilling it on chunk reload.
                var at=new BlockPos(-216,0,-43);return new Group(group.id,reserved,group.parts,Map.of(at,Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING,Direction.NORTH).setValue(LecternBlock.HAS_BOOK,true)),Map.of());
            }
            return group;
        }
        public Collection<Group> cached(){synchronized(cache){return cache.values().stream().flatMap(Optional::stream).toList();}}
    }
    public static final class Preview {
        public final Group group;private final CityTemplateSites.Site site;private final Map<Long,CityTemplateLibrary.Column> columns=new HashMap<>();
        public Preview(String id){group=previewTemplate(id);site=new CityTemplateSites.Site(id,0,0,id.equals("grand_complex")?157:48,id.equals("grand_complex")?167:48);}
        public BlockState block(int x,int y,int z){BlockState b=group.block(x,y,z);if(b!=null)return b;long k=((long)x<<32)^(z&0xffffffffL);return columns.computeIfAbsent(k,p->CityTemplateLibrary.at(x,z,site,true)).block(y);}
    }
    public static Set<String> templateIds(){return Set.copyOf(CATALOG.keySet());}
    public static Group previewTemplate(String id){var data=CATALOG.getAsJsonObject(id);if(data==null)throw new IllegalArgumentException(id);return authored(id,0,0,id.equals("grand_complex")?157:48,id.equals("grand_complex")?167:48);}
    private static Group authored(String id,int ox,int oz,int width,int depth){
        var reservation=new CityInteriorPlan.Rect(ox,oz,ox+width-1,oz+depth-1);
                JsonObject data=CATALOG.getAsJsonObject(id);if(data==null)return null;
                JsonArray size=data.getAsJsonArray("size");int dx=ox+(width-size.get(0).getAsInt())/2,dz=oz+(depth-size.get(2).getAsInt())/2;
                var parts=new ArrayList<CityInteriorPlan>();for(JsonElement raw:data.getAsJsonArray("parts")){
                    JsonObject part=raw.getAsJsonObject();var floors=new ArrayList<CityInteriorPlan.Floor>();
                    for(JsonElement f:part.getAsJsonArray("floors")){var t=f.getAsJsonObject();var a=t.getAsJsonArray("rect");floors.add(new CityInteriorPlan.Floor(t.get("y").getAsInt(),new CityInteriorPlan.Rect(dx+a.get(0).getAsInt(),dz+a.get(1).getAsInt(),dx+a.get(2).getAsInt(),dz+a.get(3).getAsInt())));}
                    parts.add(new CityInteriorPlan(id+":"+ox+":"+oz+":"+part.get("name").getAsString(),part.get("theme").getAsString(),floors,reservation,Direction.NORTH,true));
                }
                if(!id.equals("grand_complex"))return new Group(id+":"+ox+":"+oz,reservation,parts);
                var accents=new HashMap<BlockPos,BlockState>();var notices=new HashMap<BlockPos,String>();
                var centre=parts.getFirst().floors.getFirst().rect();int cx=(centre.x0()+centre.x1())/2,cz=(centre.z0()+centre.z1())/2;
                // Keep the shared podium's generous, authored public circulation, rather than imposing
                // another overlapping stair core. Furnish only hall cells outside every tower footprint.
                for(int floor:new int[]{1,5})for(int x=cx-57;x<=cx+57;x++)for(int z=cz-57;z<=cz+57;z++){
                    boolean tower=false;for(var p:parts)if(p.floors.getFirst().rect().contains(x,z)){tower=true;break;}if(tower)continue;
                    int a=x-cx,b=z-cz;
                    if(Math.floorMod(a,8)==0&&Math.floorMod(b,8)==0)accents.put(new BlockPos(x,floor+3,z),CityInteriorContent.state("interior_lamp"));
                    if(Math.abs(a)>=19&&Math.abs(a)<=25&&Math.abs(b)==24)
                        accents.put(new BlockPos(x,floor,z),CityInteriorContent.state("bench").setValue(HorizontalDirectionalBlock.FACING,b<0?Direction.SOUTH:Direction.NORTH));
                    if(Math.abs(a)==26&&Math.abs(b)==24)accents.put(new BlockPos(x,floor,z),Blocks.POTTED_FERN.defaultBlockState());
                    if(Math.abs(a)>=19&&Math.abs(a)<=25&&Math.abs(b)==29)
                        accents.put(new BlockPos(x,floor,z),CityInteriorContent.state(Math.floorMod(a,4)==0?"cabinet":"counter"));
                }
                notice(accents,notices,new BlockPos(cx-22,3,cz-20),1,"综合体公共大厅 / CONCOURSE • F1–F2");
                notice(accents,notices,new BlockPos(cx+22,7,cz+20),5,"塔楼电梯与空中连廊 / TOWERS & SKYBRIDGES");
                for(int i=1;i<=4;i++)bridge(accents,parts.getFirst(),parts.get(i),81,i==1||i==3);
                bridge(accents,parts.getFirst(),parts.get(2),153,false);
                return new Group(id+":"+ox+":"+oz,reservation,parts,accents,notices);
    }
    /** Three clear cells across and above the deck; portal cuts also reach both indoor corridors. */
    private static void bridge(Map<BlockPos,BlockState> blocks,CityInteriorPlan a,CityInteriorPlan b,int feet,boolean alongZ){
        var ra=a.floors.stream().filter(f->f.y()==feet).findFirst().orElseThrow().rect();
        var rb=b.floors.stream().filter(f->f.y()==feet).findFirst().orElseThrow().rect();
        int ax=(ra.x0()+ra.x1())/2,az=(ra.z0()+ra.z1())/2,bx=(rb.x0()+rb.x1())/2,bz=(rb.z0()+rb.z1())/2;
        // Setback towers share a core nearer their centre than their lower footprint suggests.
        // Route north/south bridges through the real corridor, outside the moving shaft.
        int start=alongZ?Math.min(az,bz):Math.min(ax,bx),end=alongZ?Math.max(az,bz):Math.max(ax,bx),cross=alongZ?a.corridorX+1:az;
        for(int t=start;t<=end;t++)for(int w=-2;w<=2;w++){
            int x=alongZ?cross+w:t,z=alongZ?t:cross+w;boolean inside=ra.inset(1).contains(x,z)||rb.inset(1).contains(x,z);
            if(Math.abs(w)==2&&inside)continue;
            blocks.put(new BlockPos(x,feet-1,z),CityBlocks.block("composite_floor").defaultBlockState());
            for(int h=0;h<3;h++)blocks.put(new BlockPos(x,feet+h,z),Math.abs(w)==2?CityBlocks.block("curtain_glass_clear").defaultBlockState():Blocks.AIR.defaultBlockState());
            if(!inside)blocks.put(new BlockPos(x,feet+3,z),t%6==0?CityInteriorContent.state("interior_lamp"):CityBlocks.block("brushed_steel").defaultBlockState());
        }
    }
    private static Group landmarkDetails(CityDistrictLandmarks.Column l,CityInteriorVersions.Policy policy){
        int ox=l.x()-l.a(),oz=l.z()-l.b(),type=l.type();
        var bounds=type==25?new CityInteriorPlan.Rect(-1760,-834,-1745,-819):type==36?new CityInteriorPlan.Rect(1390,-1780,1479,-1729):new CityInteriorPlan.Rect(ox,oz,ox+47,oz+47);
        if(!policy.allows(bounds.x0(),bounds.z0(),bounds.x1(),bounds.z1()))return null;
        var blocks=new HashMap<BlockPos,BlockState>();var notices=new HashMap<BlockPos,String>();
        if(type==3){
            for(int i=0;i<8;i++){double a=Math.PI*i/4;int x=ox+24+(int)Math.round(Math.cos(a)*9),z=oz+24+(int)Math.round(Math.sin(a)*9);blocks.put(new BlockPos(x,1,z),CityInteriorContent.state(i%2==0?"equipment_rack":"lab_bench"));blocks.put(new BlockPos(x,2,z),CityInteriorContent.state("wall_monitor"));}
            for(int x=ox+22;x<=ox+25;x++)for(int z=oz+39;z<=oz+43;z++){
                blocks.put(new BlockPos(x,0,z),CityBlocks.block("composite_floor").defaultBlockState());
                for(int y=1;y<=4;y++)blocks.put(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
            }
            for(int x=ox+22;x<=ox+25;x++)blocks.put(new BlockPos(x,0,oz+43),Blocks.SMOOTH_QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.NORTH));
            for(int x:new int[]{ox+14,ox+33})for(int z:new int[]{oz+17,oz+30})blocks.put(new BlockPos(x,1,z),CityInteriorContent.state("bench"));
            notice(blocks,notices,new BlockPos(ox+20,3,oz+43),1,"都市科学展览馆 / SCIENCE EXHIBITION");
        }else if(type==25){
            for(int x=-1757;x<=-1753;x++){blocks.put(new BlockPos(x,93,-831),CityInteriorContent.state("office_desk"));blocks.put(new BlockPos(x,94,-831),CityInteriorContent.state("wall_monitor"));}
            blocks.put(new BlockPos(-1757,93,-823),CityInteriorContent.state("equipment_rack"));blocks.put(new BlockPos(-1748,93,-823),CityInteriorContent.state("cabinet"));
            notice(blocks,notices,new BlockPos(-1758,94,-820),93,"天文台公共观测室 / OBSERVATORY");
        }else{
            for(int x=1394;x<=1424;x+=5){blocks.put(new BlockPos(x,0,-1777),CityInteriorContent.state("lab_bench"));blocks.put(new BlockPos(x,1,-1777),CityInteriorContent.state("equipment_rack"));blocks.put(new BlockPos(x,0,-1732),CityInteriorContent.state("locker"));}
            for(int z=-1768;z<=-1740;z+=7)blocks.put(new BlockPos(1393,0,z),CityInteriorContent.state("bench"));
            notice(blocks,notices,new BlockPos(1465,2,-1740),0,"机库公共检修区 / HANGAR SERVICES");
        }
        return new Group("public_detail:"+type+":"+ox+":"+oz,bounds,List.of(),blocks,notices);
    }
    private static void notice(Map<BlockPos,BlockState> blocks,Map<BlockPos,String> labels,BlockPos p,int floor,String text){
        for(int y=floor;y<=p.getY();y++)blocks.put(new BlockPos(p.getX(),y,p.getZ()-1),CityBlocks.block("white_panel_wall").defaultBlockState());
        blocks.put(p,Blocks.BIRCH_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING,Direction.SOUTH));labels.put(p,text);
    }
    private static Group group(String id,String theme,CityInteriorPlan.Rect reserve,List<CityInteriorPlan.Floor> floors,Direction facing,boolean lift){return new Group(id,reserve,List.of(new CityInteriorPlan(id,theme,floors,reserve,facing,lift)));}
    private static boolean eligibleLandmark(int type){return type==1||type==3||type==4||type==11||type==25||type==36||type==5||type==7||type==17||type==23||type==32||type==33||type==34||type==40;}
    private static String theme(CityDistrictZoning.Use u){return switch(u){case ACADEMIC,SPORTS->"academic";case SCIENCE,INDUSTRIAL->"lab";case RESIDENTIAL,GREEN->"residential";default->"commercial";};}
    public static void initialize(CityInteriors.Group group,BlockPos pos,BlockEntity tile){
        if(group==null)return;
        if(tile instanceof LecternBlockEntity lectern&&group.id.startsWith("landmark:40:")&&pos.equals(new BlockPos(-216,0,-43)))lectern.setBook(CityGoods.referenceBook("transfer_leaflet"));
        if(tile instanceof LecternBlockEntity lectern&&group.id.startsWith("landmark:32:")&&pos.equals(new BlockPos(-221,0,-95)))lectern.setBook(CityGoods.referenceBook("ability_development_guide"));
        if(tile instanceof SignBlockEntity&&group.notices.containsKey(pos))tile.getPersistentData().putString("academy:interior_sign",group.notices.get(pos));
        for(var plan:group.parts){
            if(tile instanceof CityElevatorController ctrl&&plan.lift!=null&&plan.lift.controller().equals(pos))ctrl.configure(plan.lift);
            if(tile instanceof SignBlockEntity){String text=plan.label(pos);if(text!=null)tile.getPersistentData().putString("academy:interior_sign",text);}
        }
    }
    private static final Map<ServerLevel,java.util.concurrent.ConcurrentLinkedQueue<BlockPos>> SIGNS=new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<ServerLevel,java.util.concurrent.ConcurrentLinkedQueue<Group>> BUILT=new java.util.concurrent.ConcurrentHashMap<>();
    public static void generated(ServerLevel w,Group group){BUILT.computeIfAbsent(w,k->new java.util.concurrent.ConcurrentLinkedQueue<>()).add(group);}
    @SubscribeEvent public static void chunkLoaded(ChunkEvent.Load event){
        if(!(event.getLevel()instanceof ServerLevel w)||!(event.getChunk()instanceof net.minecraft.world.level.chunk.LevelChunk chunk))return;
        if(w.getChunkSource().getGenerator()instanceof CityGreyboxGenerator generator){var pos=chunk.getPos();for(var group:generator.cachedInteriors()){var r=group.reservation();if(r.x0()<=pos.getMaxBlockX()&&r.x1()>=pos.getMinBlockX()&&r.z0()<=pos.getMaxBlockZ()&&r.z1()>=pos.getMinBlockZ())BUILT.computeIfAbsent(w,k->new java.util.concurrent.ConcurrentLinkedQueue<>()).add(group);}}
        for(BlockEntity tile:chunk.getBlockEntities().values())if(tile instanceof SignBlockEntity sign&&sign.getPersistentData().contains("academy:interior_sign"))
            SIGNS.computeIfAbsent(w,k->new java.util.concurrent.ConcurrentLinkedQueue<>()).add(sign.getBlockPos());
    }
    @SubscribeEvent public static void signTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event){
        if(!(event.getLevel()instanceof ServerLevel w))return;
        var built=BUILT.get(w);if(built!=null)for(int n=0;n<32;n++){var group=built.poll();if(group==null)break;CityInteriorVersions.record(w,group);}
        var queue=SIGNS.get(w);if(queue==null)return;
        // Sign setters notify comparator neighbors. Never invoke them in a ChunkStatus full callback,
        // or where their two-block notification reach would synchronously load a neighbor chunk.
        int budget=32;for(int n=0;n<budget;n++){
            BlockPos pos=queue.poll();if(pos==null)break;var own=w.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);if(own==null)continue;boolean ready=true;
            for(int x=Math.floorDiv(pos.getX()-2,16);x<=Math.floorDiv(pos.getX()+2,16);x++)for(int z=Math.floorDiv(pos.getZ()-2,16);z<=Math.floorDiv(pos.getZ()+2,16);z++)
                if(w.getChunkSource().getChunkNow(x,z)==null)ready=false;
            if(!ready){queue.add(pos);continue;}
            var chunk=w.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
            if(!(chunk.getBlockEntity(pos)instanceof SignBlockEntity sign)||!sign.getPersistentData().contains("academy:interior_sign"))continue;
            String label=sign.getPersistentData().getString("academy:interior_sign");var text=new SignText();String[] p=label.split(" / ",2);
            text=text.setMessage(0,net.minecraft.network.chat.Component.literal(p[0])).setMessage(1,net.minecraft.network.chat.Component.literal(p.length>1?p[1]:"ACADEMY CITY"));
            sign.setText(text,true);sign.setText(text,false);sign.setWaxed(true);sign.getPersistentData().remove("academy:interior_sign");sign.setChanged();
        }
    }
    @SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload e){if(e.getLevel()instanceof ServerLevel w){SIGNS.remove(w);BUILT.remove(w);}}
}
