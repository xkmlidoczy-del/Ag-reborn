/* AcademyCraft1.0.7 FreqTransmitterUI.java, sender-authorized modern graph bridge. GPLv3. */
package cn.academy.port.client.terminal;

import cn.academy.port.client.ClassicTerminalDrawing;
import cn.academy.port.terminal.TerminalNetwork;
import cn.academy.port.wireless.ClassicWirelessMatrixBlockEntity;
import cn.academy.port.wireless.ClassicWirelessNodeBlockEntity;
import cn.academy.port.machine.MachineDeveloperBlockEntity;
import cn.academy.port.machine.ImagFluxReceiver;
import cn.academy.port.solar.ImagFluxGenerator;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

/** Source START → query/password → repeated matrix/node links. Only an authoritative response changes links. */
final class FrequencyTransmitter {
    private enum Mode{START,PASSWORD_MATRIX,PASSWORD_NODE,LINK_MATRIX,LINK_NODE,WAIT,RETURN,QUIT}
    private final ClassicTerminalDrawing drawing=new ClassicTerminalDrawing();
    private Mode mode=Mode.START,returnMode;
    private long entered=TerminalClient.gameTime(),linkCreated;
    private BlockPos selected,pendingTarget,pendingSelected;
    private String name="",password="",message="s0_0",pending="";
    private boolean transmitting,closed;
    private final TerminalClientLifecycle.Lease lease=new TerminalClientLifecycle.Lease(Minecraft.getInstance().level,Minecraft.getInstance().player);
    private final TerminalClientLifecycle.Pending replies=new TerminalClientLifecycle.Pending();
    private PasswordScreen passwordScreen;
    boolean tick(long now){
        if(closed)return true;long elapsed=now-entered;
        if(mode==Mode.QUIT)return FrequencyTimeline.quitReady(elapsed);
        if(mode==Mode.RETURN&&FrequencyTimeline.returnReady(elapsed)){mode=returnMode;entered=linkCreated;message=mode==Mode.LINK_MATRIX?"s2_0":"s3_0";}
        if(FrequencyTimeline.timeout(now-entered,transmitting)){notifyQuit("st");}
        return false;
    }
    void close(){Minecraft mc=Minecraft.getInstance();var disposal=lease.release(mc.level,mc.player,mc.getConnection()!=null);if(!disposal.release())return;closed=true;closePassword();drawing.release();password="";pending="";replies.clear();if(disposal.notifyServer())TerminalNetwork.requestClose();}
    private void state(Mode next,String text){closePassword();mode=next;message=text;entered=TerminalClient.gameTime();transmitting=false;}
    private void closePassword(){if(passwordScreen!=null){if(Minecraft.getInstance().screen==passwordScreen)Minecraft.getInstance().setScreen(null);passwordScreen=null;}}
    private void notifyQuit(String key){pending="";replies.clear();state(Mode.QUIT,key);}
    private void request(String action,BlockPos target,BlockPos source){pending=action;pendingTarget=target;pendingSelected=source;replies.begin(action,target.asLong(),source.asLong());transmitting=true;TerminalNetwork.requestFrequency(target,source,password,action);}
    void click(){
        if(mode!=Mode.START&&mode!=Mode.LINK_MATRIX&&mode!=Mode.LINK_NODE)return;
        if(transmitting)return;
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.level==null){notifyQuit("e4");return;}
        HitResult hit=mc.player.pick(4,1,false);if(!(hit instanceof BlockHitResult block)||hit.getType()!=HitResult.Type.BLOCK){if(mode==Mode.START)closed=true;else notifyQuit("e4");return;}
        BlockPos target=block.getBlockPos();var tile=mc.level.getBlockEntity(target);
        if(mode==Mode.START){
            if(tile instanceof ClassicWirelessNodeBlockEntity node){selected=target;name=node.getNodeName();state(Mode.PASSWORD_NODE,"");passwordScreen=new PasswordScreen();mc.setScreen(passwordScreen);}
            else if(tile instanceof ClassicWirelessMatrixBlockEntity matrix){if(matrix.origin()==null){notifyQuit("e0");return;}selected=matrix.originPos();/* Source query changes the START timeout without resetting its creation time. */request("query_matrix",target,selected);}
            else notifyQuit("e4");
        }else if(mode==Mode.LINK_MATRIX){
            if(!(tile instanceof ClassicWirelessNodeBlockEntity)){notifyQuit("e4");return;}
            state(Mode.WAIT,"e5");request("link_matrix",target,selected);
        }else{
            if(!(tile instanceof ImagFluxReceiver)&&!(tile instanceof ImagFluxGenerator)&&!(tile instanceof MachineDeveloperBlockEntity)){notifyQuit("e4");return;}
            state(Mode.WAIT,"e5");request("link_node",target,selected);
        }
    }
    private void authorize(){Mode previous=mode;closePassword();state(Mode.WAIT,"s1_1");request(previous==Mode.PASSWORD_MATRIX?"authorize_matrix":"authorize_node",selected,selected);}
    void receive(String action,BlockPos target,BlockPos source,boolean accepted,String responseName){
        if(closed||!replies.accept(action,target.asLong(),source.asLong()))return;
        pending="";transmitting=false;
        if(action.equals("query_matrix")){
            if(!accepted){notifyQuit("e0");return;}name=responseName;state(Mode.PASSWORD_MATRIX,"");passwordScreen=new PasswordScreen();Minecraft.getInstance().setScreen(passwordScreen);
        }else if(action.equals("authorize_matrix")||action.equals("authorize_node")){
            if(!accepted){notifyQuit("e1");return;}
            returnMode=action.equals("authorize_matrix")?Mode.LINK_MATRIX:Mode.LINK_NODE;state(returnMode,returnMode==Mode.LINK_MATRIX?"s2_0":"s3_0");linkCreated=entered;
        }else{
            if(!accepted){notifyQuit(action.equals("link_matrix")?"e2":"e3");return;}
            state(Mode.RETURN,"e6");
        }
    }
    void render(GuiGraphics graphics){
        Minecraft mc=Minecraft.getInstance();double w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();drawing.begin(graphics);
        try{
            String title=local("name");box(15,15,30+drawing.width(title,10),18);drawing.texture("guis/apps/freq_transmitter/icon",17,15,18,18,1);drawing.text(title,39,19,10,0xffffff,1,0);
            if(mode==Mode.PASSWORD_MATRIX||mode==Mode.PASSWORD_NODE){double x=w/2+10,y=h/2-10;box(x,y,140,40);drawing.text((mode==Mode.PASSWORD_MATRIX?"SSID: ":"NAME: ")+name,x+10,y+5,10,0xbfbfbf,1,0);drawing.text("PASS: "+"*".repeat(password.length()),x+10,y+15,10,0xffffff,1,0);drawing.text(local(mode==Mode.PASSWORD_MATRIX?"s1_0":"s1_1"),x+10,y+25,10,0x30ffff,1,0);}
            else textBox(local(message),w/2+10,h/2+10);
        }finally{drawing.end();}
    }
    private static String local(String key){return Component.translatable("ac.app.freq_transmitter."+key).getString();}
    private void box(double x,double y,double w,double h){drawing.fill(x,y,w,h,0x272727,0x77/255d);drawing.glow(x,y,w,h,1,0xffffff,0xaa/255d);}
    private void textBox(String text,double x,double y){var lines=wrap(text);double width=0;for(String line:lines)width=Math.max(width,drawing.width(line,10));box(x,y,35+width,10+lines.size()*10);for(int i=0;i<lines.size();i++)drawing.text(lines.get(i),x+5,y+5+i*10,10,0xffffff,1,0);}
    private java.util.List<String> wrap(String text){var lines=new java.util.ArrayList<String>();StringBuilder line=new StringBuilder();for(int cp:text.codePoints().toArray()){String value=new String(Character.toChars(cp));if(cp=='\n'||drawing.width(line+value,10)>120){lines.add(line.toString());line.setLength(0);}if(cp!='\n')line.append(value);}if(!line.isEmpty())lines.add(line.toString());return lines;}
    private final class PasswordScreen extends Screen {
        PasswordScreen(){super(Component.translatable("ac.app.freq_transmitter.name"));}
        @Override public void render(GuiGraphics graphics,int x,int y,float partial){}
        @Override public boolean isPauseScreen(){return false;}
        @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER){authorize();return true;}if(key==GLFW.GLFW_KEY_BACKSPACE&&!password.isEmpty())password=password.substring(0,password.length()-1);return true;}
        @Override public boolean charTyped(char ch,int modifiers){/* Modern bounded wire protects the server; source accepts arbitrary allowed characters. */if(FrequencyTimeline.allowedCharacter(ch)&&password.length()<128)password+=ch;return true;}
        @Override public boolean mouseClicked(double x,double y,int button){return true;}
        @Override public boolean shouldCloseOnEsc(){return false;}
    }
}
