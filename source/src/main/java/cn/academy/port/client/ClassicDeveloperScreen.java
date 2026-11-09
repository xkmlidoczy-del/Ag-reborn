/* Faithful AcademyCraft 1.0.7 DeveloperUI/Common.TreeScreen adapter. GPLv3; see NOTICE. */
package cn.academy.port.client;

import cn.academy.port.AcademyCraft;
import cn.academy.port.ClassicHudConfig;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.ClassicRules;
import cn.academy.port.develop.DeveloperItemEnergy;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.develop.InductionFactors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Real finite developer UI, never a client-side ability or energy authority. */
public class ClassicDeveloperScreen extends Screen {
    private static final Set<String> LEARNABLE=cn.academy.port.SkillAvailability.REGISTERED_LEARNABLE;
    private static final ResourceLocation BACK=tex("guis/developer/skill_back"),OUTLINE=tex("guis/developer/skill_outline"),MASK=tex("guis/developer/skill_radial_mask"),LINE=tex("guis/developer/line");
    private static final ClassicHudTimeline.Rgba WHITE=rgba(1,1,1,1),RED=rgba(1,.333,.333,1),CYAN=rgba(161/255d,225/255d,1,1);
    private final ClassicHudFont classicFont=new ClassicHudFont();
    private final boolean skillTreeApp;
    private final Map<String,Hover> hovers=new HashMap<>();
    private final HighPowerDeveloperLayout.Navigation navigation=new HighPowerDeveloperLayout.Navigation();
    private String machineToken="",structureSignature="",consoleMode="",lastStatus="";
    private DeveloperType developerType=DeveloperType.PORTABLE;
    private double machineEnergy,treeScroll;
    private BlockPos machineOrigin;
    private String machineNodeName="N/A";
    private boolean released;
    private long created=now(),sentAt,consoleRebuildAt=-1;
    private ClassicDeveloperConsole console;
    private Cover cover;
    private ClassicDeveloperLayout.Node hoveredNode;
    private String sentAction="";
    private CompoundTag previousDevelopment;
    private static int highPowerLearningSequence;
    private static final class Hover {boolean over;long changed;Hover(long now){changed=now-100;}}
    private static final class Cover {
        final ClassicDeveloperLayout.Node node;final String kind;long changed;boolean ending,requested,received,complete,buttonDisposed;final boolean learned;String message="";double progress;
        final DeveloperLevelAttempt levelAttempt;
        double descriptionScroll;String descriptionText="";List<String> descriptionLines=List.of();
        final cn.academy.port.highpower.HighPowerLearningProtocol.Pending highPowerLearning=new cn.academy.port.highpower.HighPowerLearningProtocol.Pending();
        final HighPowerDeveloperLayout.DevelopmentFeedback developmentFeedback=new HighPowerDeveloperLayout.DevelopmentFeedback();
        Cover(String kind,ClassicDeveloperLayout.Node node){this.kind=kind;this.node=node;this.levelAttempt=kind.equals("level")?DeveloperLevelAttempt.capture(AcademyClient.state.level):null;this.learned=node!=null&&AcademyClient.state.learned(node.id());changed=now();}
    }
    public ClassicDeveloperScreen(){this(false);}
    /** Classic SkillTreeAppUI initializes Common with developer=null: no machine, learning or level actions. */
    public ClassicDeveloperScreen(boolean appView){super(Component.translatable(appView?"ac.app.skill_tree.name":"screen.academy.developer"));skillTreeApp=appView;}
    public ClassicDeveloperScreen(CompoundTag machine){this();machineToken=machine.getString("session");machineOrigin=BlockPos.of(machine.getLong("origin"));machineNodeName=machine.getString("node_name").isEmpty()?"N/A":machine.getString("node_name");try{developerType=DeveloperType.valueOf(machine.getString("type"));}catch(IllegalArgumentException ignored){}machineEnergy=finiteEnergy(machine.getDouble("energy"));}
    /** Immediate correlated failure leaves the original timeout and successful development snapshots intact. */
    public void highPowerLearningResult(CompoundTag result){
        if(cover==null||!cover.requested||!cover.highPowerLearning.receive(result))return;
        if(cover.highPowerLearning.accepted()){cover.received=true;}else{
            cover.developmentFeedback.cancel();
            navigation.finishLearning();
            cover.requested=false;cover.received=false;cover.complete=false;cover.buttonDisposed=false;cover.progress=0;
            cover.message=cover.highPowerLearning.reason().isEmpty()?translated("screen.academy.developer_server_rejected"):cover.highPowerLearning.reason();
        }
    }
    public boolean machineSession(String token){return !machineToken.isEmpty()&&machineToken.equals(token);}
    public void machineUpdate(CompoundTag data){if(machineSession(data.getString("session"))){machineEnergy=finiteEnergy(data.getDouble("energy"));machineNodeName=data.getString("node_name").isEmpty()?"N/A":data.getString("node_name");}}
    private double finiteEnergy(double value){return Double.isFinite(value)?Math.max(0,Math.min(developerType.energy,value)):0;}
    private double energy(){
        if(!machineToken.isEmpty())return machineEnergy;
        if(minecraft==null||minecraft.player==null||!minecraft.player.getMainHandItem().is(AcademyCraft.DEVELOPER.get()))return 0;
        return new DeveloperItemEnergy(minecraft.player.getMainHandItem(),DeveloperType.PORTABLE).energy();
    }
    private boolean resetMode(){return minecraft!=null&&minecraft.player!=null&&AcademyClient.state.hasCategory()&&minecraft.player.getMainHandItem().is(AcademyCraft.MAGNETIC_COIL.get());}
    private boolean developing(){return AcademyClient.development!=null&&AcademyClient.development.getString("state").equals("DEVELOPING");}
    private String signature(){var state=AcademyClient.state;return state.category+":"+state.level+":"+state.experience.keySet()+":"+resetMode();}
    @Override protected void init(){
        ClassicDeveloperTextures.prepare();
        classicFont.use(ClassicHudConfig.SPEC.isLoaded()?ClassicHudConfig.FONT.get():"Microsoft YaHei");
        if(structureSignature.isEmpty())rebuild();
    }
    private void rebuild(){
        synchronizePage();
        structureSignature=signature();created=now();hovers.clear();
        String next=!AcademyClient.state.hasCategory()?"acquire":resetMode()?"reset":"tree";
        if(!next.equals(consoleMode)){consoleMode=next;console=null;sentAction="";}
        if(!next.equals("tree")&&console==null){
            String startup=next.equals("reset")?local("console.override"):local("console.invalid_cat")+(skillTreeApp?"":local("console.learn_hint"));
            console=new ClassicDeveloperConsole(local("console.init",minecraft.player==null?"N/A":minecraft.player.getName().getString()),local("console.boot_failed"),startup,created);
        }
    }
    @Override public void tick(){
        super.tick();long time=now();if(console!=null)console.tick(time);
        synchronizePage();
        CompoundTag dev=AcademyClient.development;
        if(dev!=null&&!dev.equals(previousDevelopment)){previousDevelopment=dev.copy();acceptDevelopment(dev);}
        if(consoleRebuildAt>=0&&time>=consoleRebuildAt){consoleRebuildAt=-1;console=null;consoleMode="";rebuild();}
        if(!structureSignature.equals(signature())&&cover==null&&consoleRebuildAt<0&&(console==null||!console.busy())){rebuild();}
        if(cover!=null&&cover.ending&&time-cover.changed>=200){boolean refresh=cover.complete;discardCover();if(refresh)rebuild();}
        // An invalid server session/action has no start snapshot. Do not strand the original cover forever.
        if(cover!=null&&cover.requested&&!cover.received&&time-sentAt>3000){cover.requested=false;cover.highPowerLearning.cancel();cover.developmentFeedback.cancel();navigation.finishLearning();cover.message=translated("screen.academy.developer_server_rejected");}
        if(console!=null&&console.busy()&&!sentAction.isEmpty()&&time-sentAt>3000&&!developing()&&lastStatus.equals("waiting")){
            console.output("\n"+translated("screen.academy.developer_server_rejected")+"\n");console.busy(false);sentAction="";
        }
    }
    private void acceptDevelopment(CompoundTag dev){
        String status=dev.getString("state"),action=dev.getString("action");double progress=ClassicDeveloperTimeline.clamp(dev.getDouble("progress"));
        if(cover!=null&&cover.requested){
            boolean accepted=cover.node==null||!cn.academy.port.highpower.HighPowerSkills.contains(cover.node.id())||cover.highPowerLearning.accepted();
            var update=cover.developmentFeedback.receive(status,action,accepted);
            if(update==HighPowerDeveloperLayout.DevelopmentUpdate.PROGRESS){
                cover.received=true;cover.progress=progress;cover.message=local("progress")+String.format(Locale.ROOT," %.0f%%",progress*100);
            }else if(update==HighPowerDeveloperLayout.DevelopmentUpdate.DONE||update==HighPowerDeveloperLayout.DevelopmentUpdate.FAILED||update==HighPowerDeveloperLayout.DevelopmentUpdate.DISPLACED){
                cover.requested=false;navigation.finishLearning();cover.complete=update==HighPowerDeveloperLayout.DevelopmentUpdate.DONE;
                if(cover.complete)cover.progress=1;
                cover.message=cover.complete?local("dev_successful"):local("dev_failed");
            }
        }
        if(console!=null&&!sentAction.isEmpty()){
            if(status.equals("DEVELOPING")&&sentAction.equals(action)){lastStatus="developing";console.replaceProgress(local("console.progress",String.format(Locale.ROOT,"%02d",(int)(progress*100))));}
            else if(lastStatus.equals("developing")&&(status.equals("DONE")||status.equals("FAILED"))){
                boolean reset=sentAction.equals("reset");console.output("\n"+local("console."+(reset?"reset_":"dev_")+(status.equals("DONE")?"succ":"fail")));console.busy(false);sentAction="";consoleRebuildAt=now()+500;
            }
        }
    }
    private static String expectedAction(Cover cover){return cover.kind.equals("skill")?"skill:"+cover.node.id():"level";}
    private void request(String action,String value){
        if(machineToken.isEmpty())AcademyClient.request(action,value);
        else AcademyClient.request(switch(action){case "develop_level"->"machine_level";case "learn"->"machine_learn";case "hp_learn"->"machine_hp_learn";case "develop_reset"->"machine_reset";default->action;},machineToken+(value.isEmpty()?"":":"+value));
    }
    private void discardCover(){if(cover!=null){cover.highPowerLearning.cancel();cover.developmentFeedback.cancel();}cover=null;navigation.clearSelection();}
    private void synchronizePage(){if(navigation.synchronize(AcademyClient.state.category)){discardCover();hovers.clear();treeScroll=0;created=now();}}
    @Override public void removed(){super.removed();discardCover();classicFont.release();if(!machineToken.isEmpty()&&!released){released=true;AcademyClient.request("machine_close",machineToken);}}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){
        // The source TreeScreen is non-pausing and draws a dim game background, without menu blur.
        graphics.fillGradient(0,0,width,height,0xC0101010,0xD0101010);graphics.flush();
        ClassicHudCanvas canvas=new ClassicHudCanvas(graphics);canvas.begin();
        try{
            var placement=ClassicDeveloperTimeline.placement(width,height);graphics.pose().pushPose();
            try{
                graphics.pose().translate(placement.x(),placement.y(),0);graphics.pose().scale((float)placement.scale(),(float)placement.scale(),1);
                drawMain(graphics,canvas,placement.localX(mouseX),placement.localY(mouseY),mouseX,mouseY);
            }finally{graphics.pose().popPose();}
            if(cover!=null)drawCover(graphics,canvas,mouseX,mouseY);
        }finally{canvas.end();}
    }
    private void drawMain(GuiGraphics graphics,ClassicHudCanvas canvas,double mx,double my,int screenMouseX,int screenMouseY){
        canvas.rect(tex("guis/parent/parent_background_developerright"),118,0,278,187,rgba(.5,.65,.75,1));
        canvas.rect(tex("guis/parent/parent_background_developerleft"),4,0,108.5,187,rgba(.5,.65,.75,1));
        hoveredNode=null;
        if(console==null){
            canvas.fill(128,18,257,139,rgba(.025,.045,.065,.97),false);
            double dx=ClassicDeveloperTimeline.parallax(screenMouseX,width),dy=ClassicDeveloperTimeline.parallax(screenMouseY,height);
            double u,v;
            // SkillTree.scala centers UV at .5 and adds 1/1.01 across the original area.
            u=(dx/10*.01-.5)/1.01+.5;v=(dy/10*.01-.5)/1.01+.5;
            canvas.rect(tex("guis/effect/effect_developer_background"),128,18,257,139,u,v,u+1/1.01,v+1/1.01,accent(.16),null,null);
            var placement=ClassicDeveloperTimeline.placement(width,height);
            graphics.enableScissor((int)Math.floor(placement.x()+128*placement.scale()),(int)Math.floor(placement.y()+18*placement.scale()),
                    (int)Math.ceil(placement.x()+385*placement.scale()),(int)Math.ceil(placement.y()+157*placement.scale()));
            try{drawTree(canvas,mx,my,dx,dy);}finally{graphics.disableScissor();}
        }else{
            int line=0;for(String text:console.visibleLines(now()))text(canvas,text,133,23+10*(line++),8,WHITE,false,0);
        }
        canvas.rect(tex("guis/ui/ui_developerright"),118,0,278,187,rgba(.6,.75,.83,.8));
        canvas.rect(tex(skillTreeApp?"guis/ui/ui_developerleft_skilltree":"guis/ui/ui_developerleft"),4,0,108.5,187,rgba(.6,.75,.83,.8));
        drawTreeHeading(canvas);
        drawAbility(canvas,mx,my);if(!skillTreeApp)drawMachine(canvas,mx,my);
        if(console==null){
            for(int lv=1;lv<=5;lv++){
                double center=128+UnifiedSkillTreeLayout.COLUMN_X+(lv-1)*UnifiedSkillTreeLayout.COLUMN_STEP+8;
                text(canvas,"LV "+lv,center,5,7.5,lv>=4?rgba(1,.78,.3,.95):accent(.9),false,1);
                canvas.fill(center-6,14,12,.5,lv>=4?rgba(1,.78,.3,.5):accent(.4),false);
            }
            String caption=hoveredNode==null?translated("ac.ability."+AcademyClient.state.category+".name"):translated(hoveredNode.nameKey());
            fittedText(canvas,caption,130,164,196,8,hoveredNode==null?accent(.6):WHITE,false);
            var nodes=pageNodes();double maximum=UnifiedSkillTreeLayout.maxScroll(nodes);
            if(maximum>0){double thumb=139*139/UnifiedSkillTreeLayout.contentHeight(nodes);canvas.fill(391,18,1.5,139,accent(.12),false);canvas.fill(391,18+(139-thumb)*treeScroll/maximum,1.5,thumb,accent(.8),false);}
            if(hoveredNode!=null)drawHoverCard(canvas,hoveredNode,mx,my);
        }
    }
    private void drawTreeHeading(ClassicHudCanvas canvas){
        // Replace the baked English title/version with a localised, useful heading; keep the source app icon.
        canvas.fill(4,1,108.5,61,rgba(.035,.055,.075,.98),false);
        canvas.rect(tex("guis/apps/skill_tree/icon"),10,13,30,30,accent(.9));
        fittedText(canvas,translated("ac.app.skill_tree.name"),46,16,61,12,WHITE,true);
        long learned=pageNodes().stream().filter(n->AcademyClient.state.learned(n.id())).count();
        text(canvas,"LV "+AcademyClient.state.level+"   "+learned+" / "+pageNodes().size(),46,36,7.5,accent(.8),false,0);
        canvas.fill(10,56,97,.75,accent(.4),false);
    }
    private void drawAbility(ClassicHudCanvas canvas,double mx,double my){
        var state=AcademyClient.state;String category=state.hasCategory()?state.category:"nocategory";
        canvas.rect(tex("guis/icons/icon_"+category),6,67.5,32,32,WHITE);
        fittedText(canvas,state.hasCategory()?translated("ac.ability."+category+".name"):"N/A",37,69.5,70,13,WHITE,false);
        double progress=state.levelProgress(SkillCatalog.levelSkillCount(state));if(!state.hasCategory())progress=0;
        if(state.level<5){
            canvas.fill(37,80.75,70,1.5,accent(.15),false);canvas.fill(37,80.75,70*progress,1.5,accent(.9),false);
            text(canvas,"EXP "+(int)(progress*100)+"%",36,83,8,rgba(.75,.82,.88,1),false,0);
        }
        if(!skillTreeApp&&state.hasCategory()&&state.canLevelUp(SkillCatalog.levelSkillCount(state))){
            boolean over=ClassicDeveloperTimeline.hit(mx,my,66,82,48.363636,15.476364);
            canvas.rect(tex("guis/button/button_learn"),66,82,48.363636,15.476364,rgba(1,1,1,over?1:.7));
        }else text(canvas,levelName(state.level),106.421875,83.5,9,accent(.9),false,2);
    }
    private void drawMachine(ClassicHudCanvas canvas,double mx,double my){
        canvas.rect(tex("guis/parent/parent_background_developermachine"),4,0,108.5,187,WHITE);
        if(!machineToken.isEmpty()){
            text(canvas,local("machine_node"),8.25,104.5,12,WHITE,false,0);
            canvas.rect(tex("guis/element/element_background300x32"),8.25,114.5,100,16,rgba(1,1,1,.45));
            canvas.rect(tex("guis/icons/icon_node"),15.25,116.5,12,12,rgba(1,1,1,.45));text(canvas,machineNodeName,34.25,116.5,12,rgba(1,1,1,.55),false,0);
        }
        text(canvas,local("machine_power"),8.25,130.5,12,WHITE,false,0);text(canvas,local("machine_sync"),8.25,153.5,12,WHITE,false,0);
        canvas.fill(9.75,145,97*(energy()/developerType.energy),8,rgba(252/255d,197/255d,50/255d,1),false);
        canvas.fill(9.75,167,97*developerType.syncRate,8,rgba(50/255d,164/255d,252/255d,1),false);
        if(ClassicDeveloperTimeline.hit(mx,my,8.25,130.5,100,22))text(canvas,String.format(Locale.ROOT,"%.0f / %.0f IF",energy(),developerType.energy),8.25,179,7,WHITE,false,0);
        if(ClassicDeveloperTimeline.hit(mx,my,8.25,153.5,100,23))text(canvas,(int)(developerType.syncRate*100)+"%  "+local("type_"+developerType.name().toLowerCase(Locale.ROOT)),8.25,179,7,WHITE,false,0);
    }
    /** One merged tree per category: original and high-power skills together, no separate page. */
    private List<ClassicDeveloperLayout.Node> pageNodes(){return UnifiedSkillTreeLayout.category(AcademyClient.state.category);}
    private List<ClassicDeveloperLayout.Node> visibleNodes(){var state=AcademyClient.state;return pageNodes().stream().filter(n->n.enabled()&&(state.level>=n.level()||state.learned(n.id())||n.parent().isEmpty()||state.learned(n.parent()))).toList();}
    private void drawTree(ClassicHudCanvas canvas,double mx,double my,double dx,double dy){
        var state=AcademyClient.state;var nodes=visibleNodes();var category=pageNodes();treeScroll=UnifiedSkillTreeLayout.clampScroll(category,treeScroll);long elapsed=now()-created;
        hoveredNode=cover==null?nodes.stream().filter(node->UnifiedSkillTreeLayout.hit(node,mx,my,dx,dy,treeScroll)).findFirst().orElse(null):null;
        var requirements=canonicalPrerequisites(category);
        var focus=SkillTreePresentation.prerequisiteChain(hoveredNode==null?null:hoveredNode.id(),requirements);
        var focusedNodes=new java.util.HashSet<String>();
        for(var dependency:focus){focusedNodes.add(dependency.child());focusedNodes.add(dependency.parent());}
        var quietWires=new java.util.ArrayList<SkillTreePresentation.Wire>();
        for(int index=0;index<nodes.size();index++){
            var node=nodes.get(index);var parent=category.stream().filter(n->n.id().equals(node.parent())).findFirst();if(parent.isEmpty())continue;
            var reveal=ClassicDeveloperTimeline.reveal(elapsed,index);
            for(var edge:UnifiedSkillTreeLayout.dependency(category,node,reveal.line()))quietWires.add(new SkillTreePresentation.Wire(edge.x0(),edge.y0(),edge.x1(),edge.y1()));
        }
        drawWires(canvas,quietWires,dx,dy,.65,accent(hoveredNode==null?.24:.12));
        var focusWires=new java.util.ArrayList<SkillTreePresentation.Wire>();
        for(var dependency:focus){
            var child=nodes.stream().filter(node->node.id().equals(dependency.child())).findFirst();
            if(child.isEmpty()||nodes.stream().noneMatch(node->node.id().equals(dependency.parent())))continue;
            for(var edge:UnifiedSkillTreeLayout.dependency(category,child.get(),dependency.parent(),1))focusWires.add(new SkillTreePresentation.Wire(edge.x0(),edge.y0(),edge.x1(),edge.y1()));
        }
        drawWires(canvas,focusWires,dx,dy,1.05,accent(.92));
        for(int index=0;index<nodes.size();index++){
            var node=nodes.get(index);double x=128+node.x()-dx,y=18+node.y()-dy-treeScroll;boolean learned=state.learned(node.id()),over=cover==null&&UnifiedSkillTreeLayout.hit(node,mx,my,dx,dy,treeScroll);
            Hover hover=hovers.computeIfAbsent(node.id(),id->new Hover(now()));if(now()-hover.changed>=100&&hover.over!=over){hover.over=over;hover.changed=now();}
            double scale=ClassicDeveloperTimeline.hoverScale(hover.over,now()-hover.changed);double alpha=ClassicDeveloperTimeline.parentAlpha(learned,node.parent().isEmpty()||state.learned(node.parent()));var reveal=ClassicDeveloperTimeline.reveal(elapsed,index);
            double bx=x+8-11.5*scale,by=y+8-11.5*scale;
            canvas.rect(BACK,bx,by,23*scale,23*scale,rgba(.24,.33,.43,alpha*reveal.background()));
            boolean focused=over||focusedNodes.contains(node.id());
            canvas.rect(OUTLINE,x+8-15.5*scale,y+8-15.5*scale,31*scale,31*scale,accent(alpha*reveal.background()*(focused?1:learned?.52:.27)));
            // Original advanced skills keep their top-tier standing in the merged tree.
            if(UnifiedSkillTreeLayout.signature(node))canvas.rect(OUTLINE,x+8-15.5*scale,y+8-15.5*scale,31*scale,31*scale,rgba(1,.78,.3,alpha*reveal.background()*.85));
            var shader=ClassicDeveloperShaders.icon;if(shader!=null)shader.safeGetUniform("Monochrome").set(learned?0f:1f);
            canvas.rect(SkillIconAtlas.texture(node.category(),node.id()),x+8-7*scale,y+8-7*scale,14*scale,14*scale,0,0,1,1,rgba(1,1,1,alpha*reveal.icon()),shader!=null?shader:learned?null:ClassicHudShaders.mono,shader!=null?BACK:null);
            if(learned&&showsMastery(node))radial(canvas,OUTLINE,x+8-15.5*scale,y+8-15.5*scale,31*scale,reveal.radial()*state.exp(node.id()),accent(.8));
        }
    }
    private Map<String,List<String>> canonicalPrerequisites(List<ClassicDeveloperLayout.Node> nodes){
        var result=new HashMap<String,List<String>>();
        for(var node:nodes)if(node.enabled())result.put(node.id(),SkillCatalog.find(node.category(),node.id())
                .map(skill->SkillCatalog.requirements(skill).stream().map(SkillCatalog.Requirement::id).toList()).orElse(List.of()));
        return result;
    }
    private void drawWires(ClassicHudCanvas canvas,List<SkillTreePresentation.Wire> wires,double dx,double dy,double thickness,ClassicHudTimeline.Rgba colour){
        for(var wire:SkillTreePresentation.mergeWires(wires)){
            double x=128+wire.x0()-dx,y=18+wire.y0()-dy-treeScroll;
            if(wire.x0()==wire.x1())canvas.fill(x-thickness/2,y,thickness,wire.y1()-wire.y0(),colour,false);
            else canvas.fill(x,y-thickness/2,wire.x1()-wire.x0(),thickness,colour,false);
        }
    }
    private void drawHoverCard(ClassicHudCanvas canvas,ClassicDeveloperLayout.Node node,double mx,double my){
        var summary=DeveloperSkillDetailsLayout.wrap(skillDescription(node),164,value->classicFont.width(value,7.5,false));
        String parents=canonicalPrerequisites(pageNodes()).getOrDefault(node.id(),List.of()).stream()
                .map(id->pageNodes().stream().filter(parent->parent.id().equals(id)).findFirst().map(parent->translated(parent.nameKey())).orElse(id))
                .collect(java.util.stream.Collectors.joining(" / "));
        var parentLines=parents.isEmpty()?List.<String>of():DeveloperSkillDetailsLayout.wrap(local("req")+": "+parents,164,value->classicFont.width(value,7,false));
        int descriptionRows=Math.min(2,summary.size()),parentRows=Math.min(1,parentLines.size());
        var card=SkillTreePresentation.hoverCard(mx,my,180,30+descriptionRows*9+parentRows*9);
        canvas.fill(card.x()+1,card.y()+2,card.width(),card.height(),rgba(0,0,0,.35),false);
        canvas.fill(card.x(),card.y(),card.width(),card.height(),rgba(.035,.065,.095,.98),false);
        canvas.fill(card.x(),card.y(),2,card.height(),accent(.9),false);
        fittedText(canvas,translated(node.nameKey()),card.x()+8,card.y()+6,132,9,WHITE,true);
        text(canvas,"LV "+node.level(),card.x()+card.width()-7,card.y()+7,6.5,UnifiedSkillTreeLayout.signature(node)?rgba(1,.78,.3,1):accent(.9),false,2);
        double y=card.y()+22;
        for(int row=0;row<descriptionRows;row++){
            String line=summary.get(row);if(row==descriptionRows-1&&summary.size()>descriptionRows)line=ellipsized(line,164,7.5);
            text(canvas,line,card.x()+8,y,7.5,rgba(.82,.88,.93,1),false,0);y+=9;
        }
        for(int row=0;row<parentRows;row++){
            String line=parentLines.get(row);if(row==parentRows-1&&parentLines.size()>parentRows)line=ellipsized(line,164,7);
            text(canvas,line,card.x()+8,y+2,7,accent(.85),false,0);y+=9;
        }
    }
    private String ellipsized(String value,double width,double size){
        while(!value.isEmpty()&&classicFont.width(value+"…",size,false)>width)value=value.substring(0,value.offsetByCodePoints(value.length(),-1));
        return value+"…";
    }
    private void radial(ClassicHudCanvas canvas,ResourceLocation texture,double x,double y,double size,double progress,ClassicHudTimeline.Rgba color){
        var shader=ClassicDeveloperShaders.radial;if(shader!=null){shader.safeGetUniform("Progress").set((float)ClassicDeveloperTimeline.clamp(progress));canvas.rect(texture,x,y,size,size,0,0,1,1,color,shader,MASK);}
        else if(progress>=1)canvas.rect(texture,x,y,size,size,color);
    }
    private void drawCover(GuiGraphics graphics,ClassicHudCanvas canvas,int mx,int my){
        Cover current=cover;double alpha=ClassicDeveloperTimeline.cover(now()-current.changed,current.ending);
        canvas.fill(0,0,width,height,rgba(0,0,0,alpha),false);double cx=width/2d,cy=height/2d;
        if(current.kind.equals("wireless")){
            canvas.rect(tex("guis/icons/icon_node"),cx-16,cy-38,32,32,rgba(1,1,1,.45));
            text(canvas,"N/A",cx,cy,12,WHITE,false,1);
            separated(canvas,translated("screen.academy.developer_wireless_missing"),cx,cy+16,200,9,rgba(1,1,1,.7));return;
        }
        if(current.kind.equals("skill")){drawSkillDetails(graphics,canvas,current,mx,my);return;}
        ResourceLocation icon=tex("abilities/condition/any"+current.levelAttempt.targetLevel());
        canvas.rect(BACK,cx-25,cy-25,50,50,WHITE);canvas.rect(icon,cx-13.5,cy-13.5,27,27,WHITE);
        radial(canvas,tex("guis/developer/skill_view_outline"+(current.progress==1?"_glow":"")),cx-25,cy-25,50,current.progress,WHITE);
        if(current.kind.equals("level")){
            text(canvas,local("uplevel",levelName(current.levelAttempt.targetLevel())),cx,cy+23,12,WHITE,false,1);
            text(canvas,local("req")+" "+Math.round(estimated(current)),cx,cy+36,9,WHITE,false,1);
            text(canvas,current.message.isEmpty()?local("level_question"):current.message,cx,cy+46,9,WHITE,false,1);
            if(!current.requested&&!current.complete&&!current.buttonDisposed)drawConfirm(canvas,cx-16,cy+60,mx,my);return;
        }
    }
    private DeveloperSkillDetailsLayout.Placement detailsPlacement(Cover current){
        return DeveloperSkillDetailsLayout.placement(width,height,conditions(current.node).size());
    }
    private List<String> detailsDescription(Cover current){
        String value=skillDescription(current.node);
        if(!value.equals(current.descriptionText)){
            current.descriptionText=value;
            current.descriptionLines=DeveloperSkillDetailsLayout.wrap(value,DeveloperSkillDetailsLayout.DESCRIPTION.width(),line->classicFont.width(line,DeveloperSkillDetailsLayout.TEXT_SIZE,false));
            current.descriptionScroll=0;
        }
        return current.descriptionLines;
    }
    private String skillDescription(ClassicDeveloperLayout.Node node){
        String skill = node.id();
        boolean mentalRemote = cn.academy.port.core.RangedSkillRanges.REMOTE_SCANS.contains(skill)
                && minecraft.player != null
                && (minecraft.player.getMainHandItem().is(cn.academy.port.canon.CanonAuxItems.MENTAL_REMOTE.get())
                || minecraft.player.getOffhandItem().is(cn.academy.port.canon.CanonAuxItems.MENTAL_REMOTE.get()));
        return SkillTuningDetails.append(translated(node.descriptionKey()), skill,
                AcademyClient.state.exp(skill), mentalRemote);
    }
    private boolean showsMastery(ClassicDeveloperLayout.Node node){
        return DeveloperSkillDetailsLayout.showMastery(cn.academy.port.core.ClassicSkillConfiguration.GENERIC_SKILLS.contains(node.id()),
                cn.academy.port.highpower.HighPowerSkills.contains(node.id()),AcademyClient.state.exp(node.id()));
    }
    private boolean canConfirmSkill(Cover current){
        return DeveloperSkillDetailsLayout.canConfirm(skillTreeApp,current.learned,current.requested,current.complete,current.buttonDisposed,LEARNABLE.contains(current.node.id()));
    }
    /** Client readiness only: the server still owns learning and the actual finite IF debit. */
    private boolean canStartSkill(Cover current){
        if(current==null||!current.kind.equals("skill")||!canConfirmSkill(current)||developing())return false;
        double available=energy(),cost=estimated(current);
        if(!Double.isFinite(available)||!Double.isFinite(cost)||cost<0||available<cost)return false;
        var state=AcademyClient.state;var skill=SkillCatalog.find(current.node.category(),current.node.id()).orElse(null);
        return skill!=null&&state.level>=current.node.level()&&developerType.supportsSkill(skill.level())&&SkillCatalog.canLearn(state,skill);
    }
    private void drawSkillDetails(GuiGraphics graphics,ClassicHudCanvas canvas,Cover current,int screenMx,int screenMy){
        var placement=detailsPlacement(current);double mx=placement.localX(screenMx),my=placement.localY(screenMy);
        var node=current.node;boolean learned=current.learned||current.complete;
        String feedback=current.message.isEmpty()?(!LEARNABLE.contains(node.id())?translated("screen.academy.developer_skill_unported"):""):current.message;
        graphics.pose().pushPose();
        try{
            graphics.pose().translate(placement.x(),placement.y(),0);graphics.pose().scale((float)placement.scale(),(float)placement.scale(),1);
            var panel=placement.panel();canvas.fill(0,0,panel.width(),panel.height(),rgba(.035,.06,.09,.98),false);
            canvas.fill(0,0,panel.width(),1,accent(.8),false);canvas.fill(0,panel.height()-1,panel.width(),1,accent(.35),false);
            canvas.fill(0,0,3,panel.height(),accent(.7),false);
            canvas.rect(BACK,14,10,46,46,rgba(.24,.33,.43,1));canvas.rect(SkillIconAtlas.texture(node.category(),node.id()),24,20,26,26,WHITE);
            radial(canvas,tex("guis/developer/skill_view_outline"+(current.progress==1?"_glow":"")),14,10,46,current.progress,accent(.9));
            String name=translated(node.nameKey());double nameSize=Math.min(13,13*328/Math.max(1,classicFont.width(name,13,true)));
            text(canvas,name,74,14,nameSize,UnifiedSkillTreeLayout.signature(node)?rgba(1,.78,.3,1):WHITE,true,0);
            String status="LV "+node.level()+"  ·  "+(learned?(showsMastery(node)?local("skill_exp")+ClassicDeveloperTimeline.experiencePercent(AcademyClient.state.exp(node.id()))+"%":translated("screen.academy.developer.learned")):local("skill_not_learned"));
            text(canvas,status,74,39,9,AcademyClient.state.level<node.level()?RED:accent(.85),false,0);
            var description=DeveloperSkillDetailsLayout.DESCRIPTION;var lines=detailsDescription(current);
            current.descriptionScroll=DeveloperSkillDetailsLayout.clampScroll(lines.size(),current.descriptionScroll);
            canvas.fill(description.x()-3,description.y()-3,description.width()+6,description.height()+6,rgba(0,0,0,.25),false);
            graphics.enableScissor((int)Math.floor(placement.screenX(description.x())),(int)Math.floor(placement.screenY(description.y())),
                    (int)Math.ceil(placement.screenX(description.x()+description.width())),(int)Math.ceil(placement.screenY(description.y()+description.height())));
            try{
                for(int row=0;row<lines.size();row++){
                    double y=description.y()+row*DeveloperSkillDetailsLayout.LINE_HEIGHT-current.descriptionScroll;
                    if(y+DeveloperSkillDetailsLayout.LINE_HEIGHT>description.y()&&y<description.y()+description.height())
                        text(canvas,lines.get(row),description.x(),y,DeveloperSkillDetailsLayout.TEXT_SIZE,rgba(.86,.91,.95,1),false,0);
                }
            }finally{graphics.disableScissor();}
            double maximum=DeveloperSkillDetailsLayout.maxScroll(lines.size());
            if(maximum>0){double thumb=Math.max(10,description.height()*description.height()/(lines.size()*DeveloperSkillDetailsLayout.LINE_HEIGHT));
                canvas.fill(402,description.y(),2,description.height(),rgba(1,1,1,.15),false);
                canvas.fill(402,description.y()+(description.height()-thumb)*current.descriptionScroll/maximum,2,thumb,accent(.8),false);
            }
            canvas.fill(14,DeveloperSkillDetailsLayout.CONDITION_TOP-20,392,.5,accent(.2),false);
            text(canvas,local("req"),14,DeveloperSkillDetailsLayout.CONDITION_TOP-14,9,accent(.9),false,0);
            var requirements=conditions(node);
            for(int index=0;index<requirements.size();index++){
                var condition=requirements.get(index);double y=DeveloperSkillDetailsLayout.CONDITION_TOP+index*DeveloperSkillDetailsLayout.CONDITION_STEP;
                canvas.rect(condition.icon(),14,y,12,12,0,0,1,1,WHITE,condition.accepted()?null:ClassicHudShaders.mono,null);
                String hint=condition.hint();double size=Math.min(8.5,8.5*374/Math.max(1,classicFont.width(hint,8.5,false)));
                text(canvas,hint,32,y+1,size,condition.accepted()?rgba(.65,1,.75,1):RED,false,0);
            }
            if(DeveloperSkillDetailsLayout.showLearningCost(skillTreeApp,learned))text(canvas,local("learn_question",Long.toString(Math.round(estimated(current)))),14,placement.costY(),9,WHITE,false,0);
            var feedbackBox=placement.status();
            graphics.enableScissor((int)Math.floor(placement.screenX(feedbackBox.x())),(int)Math.floor(placement.screenY(feedbackBox.y())),
                    (int)Math.ceil(placement.screenX(feedbackBox.x()+feedbackBox.width())),(int)Math.ceil(placement.screenY(feedbackBox.y()+feedbackBox.height())));
            try{int row=0;for(String line:DeveloperSkillDetailsLayout.wrap(feedback,feedbackBox.width(),value->classicFont.width(value,8,false)))
                text(canvas,line,feedbackBox.x(),feedbackBox.y()+row++*9,8,current.complete?CYAN:rgba(1,.75,.65,1),false,0);
            }finally{graphics.disableScissor();}
            detailButton(canvas,placement.back(),translated("gui.back"),!current.requested,mx,my);
            if(canConfirmSkill(current))detailButton(canvas,placement.confirm(),translated("screen.academy.developer.start_learning"),canStartSkill(current),mx,my);
        }finally{graphics.pose().popPose();}
        if(!feedback.isEmpty()&&placement.status().contains(mx,my))graphics.renderTooltip(font,Component.literal(feedback),screenMx,screenMy);
    }
    private void detailButton(ClassicHudCanvas canvas,DeveloperSkillDetailsLayout.Rect rect,String label,boolean enabled,double mx,double my){
        boolean over=enabled&&rect.contains(mx,my);canvas.fill(rect.x(),rect.y(),rect.width(),rect.height(),rgba(.08,.14,.2,enabled?(over?1:.75):.3),false);
        canvas.fill(rect.x(),rect.y()+rect.height()-1,rect.width(),1,accent(enabled?(over?1:.55):.15),false);
        text(canvas,label,rect.x()+rect.width()/2,rect.y()+4,9,enabled?WHITE:rgba(.5,.5,.5,1),false,1);
    }
    private double confirmY(Cover current){return height/2d+60;}
    private void drawConfirm(ClassicHudCanvas canvas,double x,double y,int mx,int my){boolean over=ClassicDeveloperTimeline.hit(mx,my,x,y,32,16);canvas.rect(tex("guis/developer/button"),x,y,32,16,rgba(1,1,1,over?1:.6));}
    private double estimated(Cover current){
        int stimulations=current.kind.equals("skill")?ClassicRules.learningStimulations(current.node.level()):current.levelAttempt.stimulations();
        // Pack additions quote every paid native TPS+1 tick; classic skill/level estimates stay source-faithful.
        if(current.kind.equals("skill")&&cn.academy.port.highpower.HighPowerSkills.contains(current.node.id()))return developerType.actualConsumption(stimulations);
        return developerType.estimatedConsumption(stimulations);
    }
    private record Condition(ResourceLocation icon,String hint,boolean accepted){}
    private List<Condition> conditions(ClassicDeveloperLayout.Node node){
        var state=AcademyClient.state;var result=new java.util.ArrayList<Condition>();
        for(var condition:node.conditions()){
            if(!condition.get("shouldDisplay").getAsBoolean())continue;
            String kind=condition.get("type").getAsString(),hint;boolean accepted;
            if(kind.equals("developer_type")){
                var minimum=DeveloperType.valueOf(condition.get("minimum").getAsString().toUpperCase(Locale.ROOT));
                hint=translated(condition.get("hintKey").getAsString());accepted=developerType.ordinal()>=minimum.ordinal();
            }else if(kind.equals("skill_dependency")){
                String id=condition.get("id").getAsString();double experience=condition.get("minimum_exp").getAsDouble();
                hint=translated(condition.get("hintNameKey").getAsString())+(experience==0.0?"":condition.get("hintSuffix").getAsString());accepted=state.learned(id)&&(float)state.exp(id)>=(float)experience;
            }else if(kind.equals("any_skill_of_level")){
                int level=condition.get("level").getAsInt();hint=local("anyskill",level);accepted=SkillCatalog.ALL.stream().anyMatch(s->s.category().equals(state.category)&&s.level()==level&&state.learned(s.id()));
            }else continue;
            var icon=kind.equals("skill_dependency")&&SkillIconAtlas.contains(node.category(),condition.get("id").getAsString())
                    ?SkillIconAtlas.texture(node.category(),condition.get("id").getAsString()):resource(condition.get("icon").getAsString());
            result.add(new Condition(icon,hint,accepted));
        }
        return List.copyOf(result);
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button!=0)return super.mouseClicked(mx,my,button);
        if(cover!=null){
            if(cover.ending)return true;
            double cx=width/2d,cy=height/2d;Cover current=cover;
            if(current.kind.equals("skill")){
                var placement=detailsPlacement(current);double x=placement.localX(mx),y=placement.localY(my);
                if(canConfirmSkill(current)&&placement.confirm().contains(x,y)){startCover();return true;}
                if(!current.requested&&(placement.back().contains(x,y)||!placement.panel().contains(x,y))){current.ending=true;current.changed=now();}
                return true;
            }
            boolean learned=current.learned;
            double by=confirmY(current);
            if(!current.kind.equals("wireless")&&!skillTreeApp&&!current.requested&&!current.complete&&!current.buttonDisposed&&!learned&&(current.node==null||LEARNABLE.contains(current.node.id()))&&ClassicDeveloperTimeline.hit(mx,my,cx-16,by,32,16)){startCover();return true;}
            if(!current.requested){current.ending=true;current.changed=now();}return true;
        }
        var placement=ClassicDeveloperTimeline.placement(width,height);double x=placement.localX(mx),y=placement.localY(my);
        if(!machineToken.isEmpty()&&ClassicDeveloperTimeline.hit(x,y,8.25,114.5,100,16)){ClassicWirelessClient.openMachine(machineOrigin,machineToken);return true;}
        var state=AcademyClient.state;
        if(!skillTreeApp&&state.hasCategory()&&state.canLevelUp(SkillCatalog.levelSkillCount(state))&&ClassicDeveloperTimeline.hit(x,y,66,82,48.363636,15.476364)){cover=new Cover("level",null);return true;}
        if(console==null){double dx=ClassicDeveloperTimeline.parallax(mx,width),dy=ClassicDeveloperTimeline.parallax(my,height);
            for(var node:visibleNodes())if(UnifiedSkillTreeLayout.hit(node,x,y,dx,dy,treeScroll)&&navigation.selectIn(node.id(),pageNodes())){cover=new Cover("skill",node);return true;}}
        return true;
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
        if(cover!=null){
            if(!cover.ending&&cover.kind.equals("skill")){
                var placement=detailsPlacement(cover);
                if(DeveloperSkillDetailsLayout.DESCRIPTION.contains(placement.localX(mx),placement.localY(my)))
                    cover.descriptionScroll=DeveloperSkillDetailsLayout.clampScroll(detailsDescription(cover).size(),cover.descriptionScroll-vertical*DeveloperSkillDetailsLayout.LINE_HEIGHT*2);
            }
            return true;
        }
        if(console!=null)return super.mouseScrolled(mx,my,horizontal,vertical);
        var placement=ClassicDeveloperTimeline.placement(width,height);
        if(!UnifiedSkillTreeLayout.inViewport(placement.localX(mx),placement.localY(my)))return super.mouseScrolled(mx,my,horizontal,vertical);
        treeScroll=UnifiedSkillTreeLayout.clampScroll(pageNodes(),treeScroll-vertical*UnifiedSkillTreeLayout.ROW/2);
        hovers.clear();return true;
    }
    private void startCover(){
        if(skillTreeApp)return;
        Cover current=cover;if(current!=null&&current.kind.equals("skill")&&!canStartSkill(current))return;
        var state=AcademyClient.state;current.buttonDisposed=true;
        if(developing()){current.message=local("dev_developing");return;}
        if(current.levelAttempt!=null&&!current.levelAttempt.canStartAt(state.level)){current.message=local("condition_fail");return;}
        if(energy()<estimated(current)){current.message=local("noenergy");return;}
        if(current.node!=null){
            if(!LEARNABLE.contains(current.node.id()))return;
            if(state.level<current.node.level()){current.message=local("level_fail",current.node.level());return;}
            var skill=SkillCatalog.find(current.node.category(),current.node.id()).orElseThrow();
            if(!developerType.supportsSkill(skill.level())||!SkillCatalog.canLearn(state,skill)){current.message=local("condition_fail");return;}
        }
        current.requested=true;current.received=false;current.message=local("dev_developing");if(current.node!=null)navigation.beginLearning();sentAt=now();previousDevelopment=AcademyClient.development==null?null:AcademyClient.development.copy();
        current.developmentFeedback.begin(expectedAction(current));
        if(current.node!=null&&cn.academy.port.highpower.HighPowerSkills.contains(current.node.id())){
            if(highPowerLearningSequence==Integer.MAX_VALUE){current.requested=false;current.developmentFeedback.cancel();navigation.finishLearning();current.buttonDisposed=false;current.message=translated("screen.academy.developer_server_rejected");return;}
            var attempt=new cn.academy.port.highpower.HighPowerLearningProtocol.Request(current.node.id(),++highPowerLearningSequence);
            current.highPowerLearning.begin(attempt);request("hp_learn",attempt.wire());
        }else request(current.node==null?"develop_level":"learn",current.node==null?"":current.node.id());
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==GLFW.GLFW_KEY_ESCAPE){
            // Canonical DeveloperUI intercepts Escape only for its nested wireless link page.
            if(cover!=null&&cover.kind.equals("wireless")){if(!cover.ending){cover.ending=true;cover.changed=now();}return true;}
            onClose();return true;
        }
        if(cover!=null){
            if(!cover.ending&&cover.kind.equals("skill")&&(key==GLFW.GLFW_KEY_PAGE_UP||key==GLFW.GLFW_KEY_PAGE_DOWN||key==GLFW.GLFW_KEY_HOME||key==GLFW.GLFW_KEY_END)){
                int lines=detailsDescription(cover).size();
                double target=key==GLFW.GLFW_KEY_HOME?0:key==GLFW.GLFW_KEY_END?DeveloperSkillDetailsLayout.maxScroll(lines)
                        :cover.descriptionScroll+(key==GLFW.GLFW_KEY_PAGE_DOWN?1:-1)*DeveloperSkillDetailsLayout.DESCRIPTION.height();
                cover.descriptionScroll=DeveloperSkillDetailsLayout.clampScroll(lines,target);
            }
            return true;
        }
        if(console!=null){
            if(key==GLFW.GLFW_KEY_BACKSPACE){console.backspace();return true;}
            if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER){if(console.ready())runCommand(console.submit());return true;}
        }
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean charTyped(char ch,int modifiers){if(console!=null&&cover==null){console.type(ch);return true;}return super.charTyped(ch,modifiers);}
    private void runCommand(String command){
        if(skillTreeApp){console.output(local("console.invalid_command")+"\n");return;}
        String action=consoleMode.equals("reset")?"reset":"learn";
        if(!command.equals(action)){console.output(local("console.invalid_command")+"\n");return;}
        if(developing()){console.output(local("dev_developing")+"\n");return;}
        if(action.equals("reset")){
            if(developerType!=DeveloperType.ADVANCED){console.output(local("console.reset_fail_dev")+"\n");return;}
            boolean factor=minecraft.player!=null&&minecraft.player.getInventory().items.stream().anyMatch(stack->InductionFactors.category(stack).filter(cat->!cat.equals(AcademyClient.state.category)).isPresent());
            if(AcademyClient.state.level<3||!factor||!resetMode()){console.output(local("console.reset_fail_other")+"\n");return;}
        }
        double consumption=developerType.estimatedConsumption(action.equals("reset")?AcademyClient.state.level*10:5*(AcademyClient.state.level+1));
        if(energy()<consumption){console.output(local("noenergy")+"\n");return;}
        console.output(local("console."+(action.equals("reset")?"reset_begin":"dev_begin"))+local("console.progress","00"));
        console.busy(true);sentAt=now();sentAction=action.equals("reset")?"reset":"level";lastStatus="waiting";
        previousDevelopment=AcademyClient.development==null?null:AcademyClient.development.copy();request(action.equals("reset")?"develop_reset":"develop_level","");
    }
    private void separated(ClassicHudCanvas canvas,String text,double x,double y,double wrap,double size,ClassicHudTimeline.Rgba color){
        int row=0;for(String line:ClassicDeveloperText.wrap(text,wrap,value->classicFont.width(value,size,false)))text(canvas,line,x,y+(row++)*size,size,color,false,1);
    }
    private void text(ClassicHudCanvas canvas,String text,double x,double y,double size,ClassicHudTimeline.Rgba color,boolean bold,int alignment){
        double textWidth=alignment==0?0:classicFont.width(text,size,bold);classicFont.draw(canvas,text,x-(alignment==1?textWidth/2:alignment==2?textWidth:0),y,size,color,bold,false);
    }
    private void fittedText(ClassicHudCanvas canvas,String value,double x,double y,double maximumWidth,double size,ClassicHudTimeline.Rgba colour,boolean bold){
        double fitted=Math.min(size,size*maximumWidth/Math.max(1,classicFont.width(value,size,bold)));
        text(canvas,value,x,y,fitted,colour,bold,0);
    }
    private ClassicHudTimeline.Rgba accent(double alpha){var a=SkillTreePresentation.accent(AcademyClient.state.category);return rgba(a.r(),a.g(),a.b(),alpha);}
    private static String local(String key,Object...args){return Component.translatable("ac.skill_tree."+key,args).getString().replace("\\n","\n");}
    private static String translated(String key){return Component.translatable(key).getString();}
    private static String levelName(int level){return Component.translatable("ac.ability.level"+level).getString();}
    private static ResourceLocation tex(String path){return ClassicHudCanvas.texture(path);}
    private static ResourceLocation resource(String path){return ResourceLocation.parse(path);}
    private static ClassicHudTimeline.Rgba rgba(double r,double g,double b,double a){return new ClassicHudTimeline.Rgba(r,g,b,a);}
    private static long now(){return System.nanoTime()/1_000_000;}
}
