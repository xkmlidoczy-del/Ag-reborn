package oracle;
import cn.academy.terminal.*; import cn.academy.terminal.item.*; import cn.academy.terminal.app.settings.AppSettings;
import cn.academy.ability.app.AppSkillTree; import cn.academy.energy.client.app.AppFreqTransmitter;
import cn.academy.misc.tutorial.ModuleTutorial; import cn.lambdalib.util.datapart.*; import cn.lambdalib.s11n.network.NetworkMessage;
import net.minecraft.entity.player.EntityPlayer; import net.minecraft.item.ItemStack; import net.minecraft.world.World;
import net.minecraft.nbt.NBTTagCompound; import net.minecraftforge.common.MinecraftForge;
import java.util.*; import java.lang.reflect.*;
public final class TerminalSourceOracle {
 static int assertions,permutations,scenarios; static final List<String> ORDER=List.of("settings","tutorial","skill_tree","freq_transmitter","media_player");
 static void eq(Object a,Object b){assertions++;if(!Objects.equals(a,b))throw new AssertionError(a+" != "+b);}
 static void yes(boolean b){eq(b,true);}
 static List<App> list()throws Exception{Field f=AppRegistry.class.getDeclaredField("appList");f.setAccessible(true);return (List<App>)f.get(null);}
 static void invoke(Class<?> c,String n)throws Exception{Method m=c.getDeclaredMethod(n);m.setAccessible(true);m.invoke(null);}
 static Set<String> names(TerminalData d){Set<String>s=new TreeSet<>();for(App a:d.getInstalledApps())s.add(a.getName());return s;}
 static void resetSignals(){NetworkMessage.calls.clear();MinecraftForge.EVENT_BUS.events.clear();}
 static String observation(TerminalData d,ItemStack s,EntityPlayer p){return d.isTerminalInstalled()+"|"+String.join(",",names(d))+"|"+s.stackSize+"|"+d.syncs+"|"+MinecraftForge.EVENT_BUS.events.size()+"|"+String.join(",",NetworkMessage.calls)+"|"+p.chat.stream().map(c->c.key).reduce((a,b)->a+","+b).orElse("");}
 static void row(String key,TerminalData d,ItemStack s,EntityPlayer p){System.out.println("ROW|"+key+"|"+observation(d,s,p));scenarios++;}
 static App app(String name){return AppRegistry.enumeration().stream().filter(a->a.getName().equals(name)).findFirst().orElseThrow();}
 public static void main(String[] args)throws Exception {
  list().clear();invoke(ModuleTutorial.class,"initConditions");App tutorial=app("tutorial");
  App media=new App("media_player"){public AppEnvironment createEnvironment(){throw new AssertionError("Scala UI execution excluded");}};
  App[] apps={AppSettings.instance,tutorial,AppSkillTree.instance,AppFreqTransmitter.instance,media};
  permute(apps,0);System.out.println("SUMMARY|original|"+permutations+"|"+scenarios+"|"+assertions);
 }
 static void permute(App[] apps,int pos)throws Exception{if(pos==apps.length){run(apps);return;}for(int i=pos;i<apps.length;i++){Collections.swap(Arrays.asList(apps),pos,i);permute(apps,pos+1);Collections.swap(Arrays.asList(apps),pos,i);}}
 static void run(App[] apps)throws Exception {
  permutations++;list().clear();for(App a:apps)AppRegistry.register(a);
  eq(AppRegistry.size(),5);for(int i=0;i<5;i++){eq(apps[i].getID(),i);eq(AppRegistry.get(i),apps[i]);}
  try{AppRegistry.enumeration().clear();throw new AssertionError("mutable enumeration");}catch(UnsupportedOperationException expected){}
  invoke(ItemApp.class,"init");for(App a:apps)eq(ItemApp.getItemForApp(a)!=null,!a.isPreInstalled());
  String prefix=String.join(",",Arrays.stream(apps).map(App::getName).toList());
  EntityPlayer baseline=new EntityPlayer();TerminalData base=TerminalData.get(baseline);eq(names(base),new TreeSet<>(List.of("settings","tutorial")));eq(base.isTerminalInstalled(),false);
  for(boolean remote:new boolean[]{false,true})for(boolean creative:new boolean[]{false,true}){
   EntityPlayer p=new EntityPlayer();p.capabilities.isCreativeMode=creative;TerminalData d=TerminalData.get(p);var installer=new ItemTerminalInstaller();var s=new ItemStack(3);var w=new World(remote);resetSignals();
   installer.onItemRightClick(s,w,p);row(prefix+"/terminal/"+remote+"/"+creative+"/first",d,s,p);eq(s.stackSize,remote||creative?3:2);eq(d.isTerminalInstalled(),!remote);
   installer.onItemRightClick(s,w,p);row(prefix+"/terminal/"+remote+"/"+creative+"/repeat",d,s,p);eq(d.syncs,remote?0:1);eq(MinecraftForge.EVENT_BUS.events.size(),remote?0:1);eq(NetworkMessage.calls.size(),remote?0:2);
   for(String name:List.of("skill_tree","freq_transmitter","media_player")){
    var item=ItemApp.getItemForApp(app(name));var token=new ItemStack(4);item.onItemRightClick(token,w,p);row(prefix+"/app/"+name+"/"+remote+"/"+creative+"/first",d,token,p);eq(token.stackSize,remote||creative?4:3);
    item.onItemRightClick(token,w,p);row(prefix+"/app/"+name+"/"+remote+"/"+creative+"/repeat",d,token,p);eq(token.stackSize,remote||creative?4:3);
   }
   eq(d.syncs,remote?0:4);eq(MinecraftForge.EVENT_BUS.events.size(),remote?0:4);eq(NetworkMessage.calls.size(),remote?0:5);
  }
  for(String name:List.of("skill_tree","freq_transmitter","media_player")){
   EntityPlayer p=new EntityPlayer();TerminalData d=TerminalData.get(p);var token=new ItemStack(2);resetSignals();ItemApp.getItemForApp(app(name)).onItemRightClick(token,new World(false),p);row(prefix+"/absent/"+name,d,token,p);eq(token.stackSize,2);eq(d.syncs,0);eq(names(d),new TreeSet<>(List.of("settings","tutorial")));
  }
  // Actual original NBTS11n + SerializationHelper: every semantic subset, both terminal flags.
  for(int mask=0;mask<8;mask++)for(boolean terminal:new boolean[]{false,true}){
   EntityPlayer p=new EntityPlayer();TerminalData d=TerminalData.get(p);if(terminal)d.install();for(int i=0;i<3;i++)if((mask&(1<<i))!=0)d.installApp(app(ORDER.get(i+2)));
   var tag=new NBTTagCompound();d.toNBT(tag);TerminalData restored=new TerminalData();restored.fromNBT(tag);eq(names(restored),names(d));eq(restored.isTerminalInstalled(),terminal);
   eq(tag.getTag("installedList") instanceof net.minecraft.nbt.NBTTagByteArray,true);eq(tag.getTag("isInstalled") instanceof net.minecraft.nbt.NBTTagByte,true);
   // Decode after rebuilding numeric registry in the same order, then project semantic app names.
   list().clear();for(App a:apps)AppRegistry.register(a);TerminalData fresh=new TerminalData();fresh.fromNBT(tag);eq(names(fresh),names(d));
  }
  EntityPlayer p=new EntityPlayer();TerminalData d=TerminalData.get(p);resetSignals();d.installApp(app("skill_tree"));eq(d.isTerminalInstalled(),false);eq(d.isInstalled(app("skill_tree")),true);eq(d.syncs,1);d.installApp(app("skill_tree"));eq(d.syncs,1);
  d.side=cpw.mods.fml.relauncher.Side.CLIENT;try{d.install();throw new AssertionError("client install accepted");}catch(IllegalStateException expected){}try{d.installApp(app("media_player"));throw new AssertionError("client app accepted");}catch(IllegalStateException expected){}
 }
}