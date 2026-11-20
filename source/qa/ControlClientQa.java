package cn.academy.hotfix.qa;

import cn.aca.omnitrix.*;
import cn.aca.infinity.*;
import cn.aca.infinity.client.ClientCache;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

@Mod(value="ag_render_qa",dist=Dist.CLIENT)
public final class ControlClientQa {
    private static final String WORLD="x-watch-reload";
    private int stage, ticks;
    private float savedHp;
    public ControlClientQa() { NeoForge.EVENT_BUS.addListener(this::tick); }
    private static void require(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
    private static WristNetwork.Snapshot clientWatch() {
        try {
            var self=Class.forName("cn.aca.omnitrix.client.ClientState").getDeclaredMethod("self");
            self.setAccessible(true); var view=self.invoke(null);
            if (view==null) return null;
            var getter=view.getClass().getDeclaredMethod("s"); getter.setAccessible(true);
            return (WristNetwork.Snapshot)getter.invoke(view);
        } catch(ReflectiveOperationException ex){throw new AssertionError(ex);}
    }
    private void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(stage==0 && mc.screen instanceof TitleScreen){
            stage=1;
            var settings=new LevelSettings("X Watch Reload",GameType.SURVIVAL,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel(WORLD,settings,new WorldOptions(61432L,false,false),
                    regs->regs.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
            return;
        }
        if(stage==2 && mc.screen instanceof TitleScreen && mc.getSingleplayerServer()==null){
            stage=3; ticks=0;
            mc.createWorldOpenFlows().openWorld(WORLD,()->{throw new AssertionError("reopen cancelled");});
            return;
        }
        if (stage==3 && mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen screen) {
            for(var child:screen.children()) if(child instanceof net.minecraft.client.gui.components.Button b
                    && b.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tr
                    && tr.getKey().equals("selectWorld.backupJoinSkipButton")) {
                System.out.println("CONTROL_CLIENT confirming reopen of temporary test world");
                b.onPress(); return;
            }
        }
        if(mc.level==null || mc.player==null || mc.getSingleplayerServer()==null) return;
        int t=++ticks; var server=mc.getSingleplayerServer();
        if(stage==1 && t==12) server.executeBlocking(()->{
            var p=server.getPlayerList().getPlayers().getFirst();
            server.overworld().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
            require(WristService.equip(p,new ItemStack(Omnitrix.watch(Kind.CELESTIAL))),"equip X");
            var w=AcaInfinity.wearer(p); w.gauntlet=InfinityGauntletItem.createFullStack();
            w.updateData(GauntletData.FULL.withSelected(Stone.MIND));
            InfinityNetwork.syncWearer(p);
        });
        if(stage==1 && t==42) {
            server.executeBlocking(()->require(WristService.start(server.getPlayerList().getPlayers().getFirst(),Form.GHOSTFREAK,-1),"start real X form"));
            KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_COMMA));
        }
        if(stage==1 && t==118) {
            require(clientWatch()!=null && clientWatch().until()==0 && clientWatch().chosen()==Form.GHOSTFREAK,"active client HUD snapshot");
            require(ClientCache.self()!=null && !ClientCache.self().snapshot().equipped(),"comma removed equipped gauntlet");
            server.executeBlocking(()->{
                var p=server.getPlayerList().getPlayers().getFirst(); var w=Omnitrix.wrist(p);
                require(w.stage()==Wrist.Stage.ACTIVE && p.getMaxHealth()==80,"active server form");
                var sg=p.getInventory().items.stream().filter(i->i.is(AcaInfinity.INFINITY_GAUNTLET.get())).findFirst().orElseThrow();
                require(GauntletData.FULL.withSelected(Stone.MIND).equals(sg.get(AcaInfinity.GAUNTLET_DATA.get())),"intact returned item");
                p.setHealth(57); savedHp=p.getHealth();
                System.out.println("CONTROL_CLIENT before actual disconnect HP="+savedHp+" max="+p.getMaxHealth()+" gauntlet in inventory");
            });
            stage=2;
            mc.level.disconnect();
            mc.disconnect(new TitleScreen());
            mc.setScreen(new TitleScreen());
        }
        if(stage==3 && t==20) {
            var hud=clientWatch();
            require(hud!=null && hud.chosen()==Form.GHOSTFREAK && hud.until()==0,"reopened client sees permanent form");
            server.executeBlocking(()->{
                var p=server.getPlayerList().getPlayers().getFirst(); var w=Omnitrix.wrist(p);
                require(w.kind()==Kind.CELESTIAL && w.stage()==Wrist.Stage.ACTIVE && p.getHealth()>=savedHp && p.getHealth()<=80,"HP and active form after disk reload "+p.getHealth()+" expected "+savedHp);
                require(Abilities.cast(p,w,false,WristService.time(p)),"ability works after disk reload");
                System.out.println("QA_DONE physical comma binding returned six stones, saved and reopened X form with HUD; hp="+p.getHealth()+" max="+p.getMaxHealth());
            });
            stage=4; mc.stop();
        }
    }
}
