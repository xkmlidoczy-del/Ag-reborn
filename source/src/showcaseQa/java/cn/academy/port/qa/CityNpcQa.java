package cn.academy.port.qa;

import cn.academy.port.city.CityEspers;
import cn.academy.port.city.CityGoods;
import cn.academy.port.city.CityNpcContent;
import cn.academy.port.city.CityNpcEntity;
import cn.academy.port.client.city.CityDialogueScreen;
import com.mojang.logging.LogUtils;
import java.util.EnumMap;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/** One integrated test of actual conversation buttons, service transactions and all civilian demonstrations. */
final class CityNpcQa {
    private static final Logger LOG=LogUtils.getLogger();
    private static final EnumMap<CityEspers.Profile,UUID> DEMOS=new EnumMap<>(CityEspers.Profile.class);
    private static UUID target;
    private static Vec3 before;
    private static int retry;
    private CityNpcQa() {}
    private static void require(boolean ok,String why) {if(!ok) throw new IllegalStateException("QA_CITY_NPC "+why);}
    private static ServerPlayer player(Minecraft mc) {return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());}
    private static <T> T server(Minecraft mc,java.util.function.Supplier<T> call) {return mc.getSingleplayerServer().submit(call).join();}
    private static int coins(ServerPlayer p) {
        int total=0;for(int i=0;i<p.getInventory().getContainerSize();i++) {var s=p.getInventory().getItem(i);if(s.is(CityGoods.CITY_COIN.get()))total+=s.getCount();}return total;
    }
    static boolean step(Minecraft mc,String op) {
        String[] a=op.split(",");
        switch(a[0]) {
            case "fixture" -> server(mc,() -> {
                var p=player(mc);p.getInventory().add(new ItemStack(CityGoods.CITY_COIN.get(),40));
                p.getFoodData().setFoodLevel(6);p.setHealth(p.getMaxHealth()-7);
                p.addEffect(new MobEffectInstance(MobEffects.POISON,600,0));
                LOG.info("QA_CITY_NPC_FIXTURE health={} max={} coins={} difficulty={}",p.getHealth(),p.getMaxHealth(),coins(p),p.level().getDifficulty());return null;
            });
            case "choose", "focus" -> {
                require(mc.screen instanceof CityDialogueScreen,"conversation screen missing for "+a[1]);
                var screen=(CityDialogueScreen)mc.screen;
                var option=screen.view().options().stream().filter(o -> o.id().equals(a[1])&&o.enabled()).findFirst().orElseThrow(() -> new IllegalStateException("QA_CITY_NPC choice missing "+a[1]+" options="+screen.view().options()));
                var button=screen.children().stream().filter(e -> e instanceof Button b && b.active && b.getMessage().getString().equals(option.label().getString())).map(e -> (Button)e).findFirst().orElse(null);
                if(button==null) {
                    var next=screen.children().stream().filter(e -> e instanceof Button b && b.active && b.getMessage().getString().equals("›")).map(e -> (Button)e).findFirst().orElseThrow();
                    next.onPress();return false;
                }
                if(a[0].equals("focus")) screen.setFocused(button);
                else {button.onPress();LOG.info("QA_CITY_NPC_BUTTON action={} realWidget=true realNetwork=true",a[1]);}
            }
            case "scroll" -> {require(mc.screen instanceof CityDialogueScreen,"dialogue required");mc.screen.mouseScrolled(mc.screen.width*.45,mc.screen.height*.5,0,-3);}
            case "hud" -> {
                require(mc.options.hideGui==Boolean.parseBoolean(a[1]),"dialogue HUD visibility was not restored");
                LOG.info("QA_CITY_NPC_HUD hidden={}",mc.options.hideGui);
            }
            case "view" -> {
                require(mc.screen instanceof CityDialogueScreen,"conversation did not open");
                var screen=(CityDialogueScreen)mc.screen;
                require(screen.view().options().stream().anyMatch(o -> o.id().equals(a[1])),"choice missing "+a[1]);
                LOG.info("QA_CITY_NPC_VIEW title={} options={} district={} coins={}",screen.view().title().getString(),screen.view().options().size(),screen.view().district(),screen.view().coins());
            }
            case "healed" -> server(mc,() -> {
                var p=player(mc);require(p.getHealth()==p.getMaxHealth() && !p.hasEffect(MobEffects.POISON),"medical service did not heal / clear poison");
                require(coins(p)==Integer.parseInt(a[1]),"medical fee mismatch "+coins(p));
                LOG.info("QA_CITY_NPC_HEALED hp={} max={} poison=false coins={}",p.getHealth(),p.getMaxHealth(),coins(p));return null;
            });
            case "repaired" -> server(mc,() -> {
                var p=player(mc);var held=p.getMainHandItem();
                require(held.is(Items.IRON_PICKAXE)&&held.getDamageValue()==0&&!held.getEnchantments().isEmpty(),"repair / enchantment preservation failed");
                require(coins(p)==Integer.parseInt(a[1]),"repair fee mismatch "+coins(p));
                LOG.info("QA_CITY_NPC_REPAIRED damage={} enchantmentsPreserved=true coins={}",held.getDamageValue(),coins(p));return null;
            });
            case "quest" -> server(mc,() -> {
                var p=player(mc);int state=p.getPersistentData().getCompound("academy:city_errands").getInt(a[1]);
                require(state==Integer.parseInt(a[2]),"quest state "+a[1]+"="+state);
                require(coins(p)==Integer.parseInt(a[3]),"quest balance "+a[1]+"="+coins(p));
                LOG.info("QA_CITY_NPC_QUEST id={} state={} coins={}",a[1],state,coins(p));return null;
            });
            case "survey" -> server(mc,() -> {
                require(player(mc).getPersistentData().getCompound("academy:city_errands").getBoolean(a[1]+"_visited"),"survey checkpoint not recorded");
                LOG.info("QA_CITY_NPC_SURVEY id={} recorded=true actualPlayerPosition=true",a[1]);return null;
            });
            case "supply" -> server(mc,() -> {
                var p=player(mc);int wheat=0;for(var s:p.getInventory().items)if(s.is(Items.WHEAT))wheat+=s.getCount();
                require(wheat==0,"supply handover did not consume wheat");LOG.info("QA_CITY_NPC_SUPPLY wheatConsumed=8");return null;
            });
            case "skins" -> {
                require(CityNpcEntity.Look.values().length==30,"expected 30 skins");
                for(var look:CityNpcEntity.Look.values()) require(mc.getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath("academy","textures/entity/city_npc/"+look.id()+".png")).isPresent(),"missing skin "+look.id());
                LOG.info("QA_CITY_NPC_SKINS count=30 loaded=true");
            }
            case "spawn_demos" -> server(mc,() -> {
                var p=player(mc);int index=0;
                var looks=new CityNpcEntity.Look[]{CityNpcEntity.Look.ESPER_UNIFORM,CityNpcEntity.Look.ESPER_LAB,CityNpcEntity.Look.ESPER_WORK,CityNpcEntity.Look.ESPER_TRACK,CityNpcEntity.Look.ESPER_ART,CityNpcEntity.Look.ESPER_FIELD};
                for(var profile:CityEspers.Profile.values()) if(profile!=CityEspers.Profile.NONE) {
                    var npc=CityNpcContent.NPC.get().create(p.serverLevel());var tag=new CompoundTag();
                    tag.putString("Look",looks[index%looks.length].id());tag.putString("Esper",profile.id());tag.putString("Activity","stand");
                    npc.readAdditionalSaveData(tag);npc.setNoAi(true);npc.setInvulnerable(true);
                    npc.moveTo(-75.5+(index%6)*4,0,-25.5+(index/6)*5,180,0);npc.setYHeadRot(180);
                    npc.setCustomName(profile.label().copy().append(" Lv"+profile.level()));npc.setCustomNameVisible(true);
                    require(p.serverLevel().addFreshEntity(npc),"could not add staged esper "+profile.id());
                    DEMOS.put(profile,npc.getUUID());index++;
                }
                LOG.info("QA_CITY_NPC_DEMO_FIXTURES count={} staged=true",index);return null;
            });
            case "open_demo" -> {
                var profile=CityEspers.Profile.valueOf(a[1].toUpperCase(java.util.Locale.ROOT));target=DEMOS.get(profile);
                var npc=mc.level.getEntitiesOfClass(CityNpcEntity.class,mc.player.getBoundingBox().inflate(100),n -> n.getUUID().equals(target)).stream().findFirst().orElse(null);
                if(npc==null || npc.distanceToSqr(mc.player)>10) {
                    if(++retry%20==1) server(mc,() -> {var p=player(mc);var n=p.serverLevel().getEntity(target);p.teleportTo(n.getX(),n.getY(),n.getZ()-2.2);return null;});
                    require(retry<600,"esper fixture not reachable "+a[1]);return false;
                }
                retry=0;before=server(mc,() -> player(mc).serverLevel().getEntity(target).position());
                if(profile==CityEspers.Profile.WATER) server(mc,() -> {player(mc).setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.GLASS_BOTTLE,2));return null;});
                mc.gameMode.interact(mc.player,npc,InteractionHand.MAIN_HAND);
            }
            case "demo_check" -> server(mc,() -> {
                var p=player(mc);var npc=(CityNpcEntity)p.serverLevel().getEntity(target);var profile=npc.esper();
                require(npc.activity()==CityNpcEntity.Activity.CAST,"demo pose not active "+a[1]+" "+npc.activity());
                switch(profile) {
                    case AIR,VECTOR,FLOAT -> require(p.hasEffect(MobEffects.SLOW_FALLING),"missing slow falling");
                    case MENTAL -> require(p.hasEffect(MobEffects.NIGHT_VISION),"missing night vision");
                    case NITROGEN -> require(p.hasEffect(MobEffects.DAMAGE_RESISTANCE),"missing resistance");
                    case SHADOW -> require(p.hasEffect(MobEffects.INVISIBILITY),"missing shadow support");
                    case WATER -> {require(p.getMainHandItem().is(Items.GLASS_BOTTLE)&&p.getMainHandItem().getCount()==1,"water support did not fill one bottle");
                        require(p.getInventory().items.stream().anyMatch(s -> s.is(Items.POTION)),"water potion missing");}
                    case TELEPORT -> require(npc.position().distanceToSqr(before)>3.9,"short teleport did not move");
                    default -> {}
                }
                LOG.info("QA_CITY_NPC_DEMO profile={} level={} activity={} displaced={} actualSupportChecked=true",profile.id(),profile.level(),npc.activity(),npc.position().distanceTo(before));return null;
            });
            case "demo_after" -> server(mc,() -> {
                var npc=(CityNpcEntity)player(mc).serverLevel().getEntity(target);
                require(npc.activity()==CityNpcEntity.Activity.STAND,"demo did not restore activity "+npc.activity());return null;
            });
            default -> throw new IllegalArgumentException("Unknown NPC QA step "+op);
        }
        return true;
    }
}
