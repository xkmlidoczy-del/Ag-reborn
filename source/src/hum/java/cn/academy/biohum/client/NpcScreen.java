package cn.academy.biohum.client;
import cn.academy.biohum.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
public final class NpcScreen extends Screen {
 public final HumNpc npc;
 private int offset;
 public NpcScreen(HumNpc a){super(Component.literal(a.profile().name()));npc=a;}
 private int left(){return width/2-150;}
 private int top(){return Math.max(20, height/2-103);}
 private int visibleRows(){return Math.max(1,(height-top()-85)/25);}
 @Override protected void init(){
  int left=left(),top=top();
  String[] ids={"talk","follow","stay","defend","release"},names={"交谈","跟随","停留","防卫同行","解除跟随"};
  for(int i=0;i<ids.length;i++){
   String id=ids[i];
   addRenderableWidget(Button.builder(Component.literal(names[i]),b->send(id))
       .bounds(left+(i%3)*102,top+(i/3)*26,98,22).build());
  }
  var move=NpcMove.of(npc);
  if(move!=null)addRenderableWidget(Button.builder(Component.literal(move.interaction()),b->send("character"))
      .bounds(left+204,top+26,98,22).build());
  var idsInPhase=npc.skills();
  offset=Math.clamp(offset,0,Math.max(0,idsInPhase.size()-visibleRows()));
  for(int i=offset;i<Math.min(idsInPhase.size(),offset+visibleRows());i++){
   var skill=Skills.get(idsInPhase.get(i));
   addRenderableWidget(Button.builder(Component.literal((skill.decision()?"★  ":"")+skill.label()),b->send("cast/"+skill.id()))
       .bounds(left,top+59+(i-offset)*25,302,21).build());
  }
  if(idsInPhase.size()>visibleRows()){
   int bottom=top+59+visibleRows()*25;
   var up=addRenderableWidget(Button.builder(Component.literal("上一页"),b->scroll(-visibleRows())).bounds(left,bottom,70,18).build());
   var down=addRenderableWidget(Button.builder(Component.literal("下一页"),b->scroll(visibleRows())).bounds(left+232,bottom,70,18).build());
   up.active=offset>0;down.active=offset+visibleRows()<idsInPhase.size();
  }
 }
 private boolean scroll(int amount){
  int next=Math.clamp(offset+amount,0,Math.max(0,npc.skills().size()-visibleRows()));
  if(next==offset)return false;
  offset=next;rebuildWidgets();return true;
 }
 @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
  return vertical!=0&&scroll(vertical>0?-1:1)||super.mouseScrolled(x,y,horizontal,vertical);
 }
 public void send(String action){if(!Skills.actionId(npc.profile(),action).isEmpty()){BioClient.prepare(npc,action);onClose();return;}PacketDistributor.sendToServer(new Payloads.Action(npc.getId(),action));if(!action.equals("talk"))onClose();}
 @Override public boolean isPauseScreen(){return false;}
 @Override public void tick(){if(!npc.isAlive()||minecraft.player==null||minecraft.player.distanceToSqr(npc)>49)onClose();}
 @Override public void render(GuiGraphics g,int mx,int my,float dt){
  renderBackground(g,mx,my,dt);
  int x=width/2,y=top(),footer=y+59+visibleRows()*25;
  g.fill(x-162,y-35,x+162,Math.min(height,footer+35),0xe8202532);
  g.drawCenteredString(font,npc.profile().name(),x,y-29,0xffe9d1a5);
  g.drawCenteredString(font,npc.phase().label()+"  ·  生命 "+Math.round(npc.getHealth())+" / "+Math.round(npc.getMaxHealth()),x,y-15,0xffc6d7df);
  g.drawCenteredString(font,"选招后按 "+BioClient.CAST_KEY.getTranslatedKeyMessage().getString()+" 施放  ·  "+npc.skills().size()+" 招",x,footer+22,0xffa3b7bd);
  super.render(g,mx,my,dt);
 }
}
