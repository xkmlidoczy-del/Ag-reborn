package cn.academy.port.city;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import cn.academy.port.city.CityDialogueNetwork.Option;
import cn.academy.port.city.CityServicePlan.Role;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-owned dialogue, work orders and professional services. */
@EventBusSubscriber(modid="academy")
public final class CityDialogue {
    private enum Page { MAIN, TASKS, JOURNAL, DETAIL, GUIDE, ESPER, SPAR }
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static long serial;
    private static final class Session {
        final UUID npc; final long token;
        Page page=Page.MAIN; String quest=""; Component notice;
        Session(CityNpcEntity npc) {this.npc=npc.getUUID();this.token=++serial;}
    }
    private CityDialogue() {}
    private static Component tr(String key,Object... args) {return Component.translatable("academy.city."+key,args);}
    private static boolean medic(CityNpcEntity npc) {return npc.look()==CityNpcEntity.Look.MEDIC || npc.service()==Role.CAMPUS_MEDIC || npc.service()==Role.FIELD_MEDIC;}
    private static boolean engineer(CityNpcEntity npc) {return npc.look()==CityNpcEntity.Look.ENGINEER || npc.look()==CityNpcEntity.Look.TECHNICIAN || npc.service()==Role.ENGINEER || npc.service()==Role.RESERVOIR;}
    private static boolean shop(CityNpcEntity npc) {return npc.service()!=Role.NONE && !CityServicePlan.site(npc.service()).shop().isEmpty();}
    private static boolean guide(CityNpcEntity npc) {return npc.service()==Role.MENTOR || npc.service()==Role.LIBRARIAN || npc.look()==CityNpcEntity.Look.TEACHER;}
    public static void open(ServerPlayer player,CityNpcEntity npc) {
        if(npc.distanceToSqr(player)>64 || npc.occupiedByOther(player)) {player.sendSystemMessage(tr("message.busy"));return;}
        close(player,true,false);
        npc.beginConversation(player);
        var session=new Session(npc);SESSIONS.put(player.getUUID(),session);send(player,npc,session);
    }
    public static void choose(ServerPlayer player,CityDialogueNetwork.Choice packet) {
        var session=SESSIONS.get(player.getUUID());
        if(session==null || session.token!=packet.token() || !session.npc.equals(packet.npc())) return;
        var entity=player.serverLevel().getEntity(session.npc);
        if(!(entity instanceof CityNpcEntity npc) || !npc.isAlive() || npc.distanceToSqr(player)>64
                || !npc.conversing() || npc.occupiedByOther(player)) {close(player,true,true);return;}
        if(packet.action().equals("close")) {close(player,true,false);return;}
        npc.extendConversation();
        if(packet.action().equals("keep")) return;
        var allowed=options(player,npc,session);
        if(allowed.stream().noneMatch(o -> o.enabled() && o.id().equals(packet.action()))) return;
        String action=packet.action();session.notice=null;
        switch(action) {
            case "main" -> session.page=Page.MAIN;
            case "talk" -> session.notice=CityNpcDialogue.line(player,npc,player.tickCount/20);
            case "local" -> {
                int d=CityPlan.district(npc.blockPosition().getX(),npc.blockPosition().getZ());
                session.notice=tr("district."+d).copy().append("\n\n").append(tr("dialogue.local_help"));
            }
            case "tasks" -> session.page=Page.TASKS;
            case "commute" -> session.notice=CityCommuters.description(npc);
            case "journal" -> session.page=Page.JOURNAL;
            case "guide" -> session.page=Page.GUIDE;
            case "guide_awaken" -> session.notice=tr("dialogue.guide_awaken");
            case "guide_learn" -> session.notice=tr("dialogue.guide_learn");
            case "guide_power" -> session.notice=tr("dialogue.guide_power");
            case "esper" -> session.page=Page.ESPER;
            case "spar" -> session.page=Page.SPAR;
            case "spar_start" -> {
                close(player,true,true);
                if(!npc.combat().startDuel(player))player.sendSystemMessage(tr("combat.not_ready"));return;
            }
            case "demo" -> {
                player.sendSystemMessage(CityEspers.demonstrate(player,npc));
                close(player,false,true);return;
            }
            case "heal" -> session.notice=heal(player,npc);
            case "repair" -> session.notice=repair(player,npc);
            case "trade" -> {
                close(player,true,true);CityServices.openShop(player,npc);return;
            }
            default -> {
                var split=action.split("\\.",2);
                if(split.length!=2) return;
                var q=CityErrands.ALL.stream().filter(e -> e.id().equals(split[1])).findFirst().orElse(null);
                if(q==null) return;
                switch(split[0]) {
                    case "detail" -> {session.quest=q.id();session.page=Page.DETAIL;}
                    case "accept" -> {session.notice=CityErrands.accept(player,q);session.page=Page.MAIN;}
                    case "finish" -> {session.notice=CityErrands.complete(player,q);session.page=Page.MAIN;}
                    default -> {return;}
                }
            }
        }
        send(player,npc,session);
    }
    private static void add(List<Option> out,String id,String label,Object... args) {out.add(new Option(id,tr(label,args),true));}
    private static List<Option> options(ServerPlayer p,CityNpcEntity npc,Session s) {
        var out=new ArrayList<Option>();
        switch(s.page) {
            case MAIN -> {
                if(shop(npc)) add(out,"trade",npc.service()==Role.EXCHANGE?"dialogue.exchange":"dialogue.trade");
                if(medic(npc)) add(out,"heal","dialogue.heal",4);
                if(engineer(npc)) {int price=repairPrice(p.getMainHandItem());add(out,"repair",price==0?"dialogue.inspect_item":"dialogue.repair",price);}
                if(guide(npc)) add(out,"guide","dialogue.guide");
                if(CityErrands.ALL.stream().anyMatch(q -> q.from()==npc.service()||q.to()==npc.service())) add(out,"tasks","dialogue.tasks");
                if(npc.esper()!=CityEspers.Profile.NONE) add(out,"esper","dialogue.esper");
                if(!npc.commuterId().isEmpty())add(out,"commute","commute.ask");
                add(out,"talk","dialogue.talk");add(out,"local","dialogue.local");add(out,"journal","dialogue.journal");
            }
            case TASKS -> {
                for(var q:CityErrands.ALL) {
                    int state=CityErrands.state(p,q);
                    if(q.from()==npc.service() && state==0) add(out,"detail."+q.id(),"quest.offer",q.name(),q.reward());
                    else if(q.to()==npc.service() && state==1) add(out,"detail."+q.id(),CityErrands.ready(p,q)?"quest.turn_in":"quest.check",q.name());
                    else if(q.from()==npc.service()&&q.repeatable()&&state==2)add(out,"detail."+q.id(),"quest.repeat_wait",q.name(),(CityErrands.remaining(p,q)+19)/20);
                }
            }
            case JOURNAL -> {
                for(var q:CityErrands.ALL) if(CityErrands.state(p,q)==1) add(out,"detail."+q.id(),"quest.check",q.name());
            }
            case DETAIL -> {
                var q=CityErrands.ALL.stream().filter(e -> e.id().equals(s.quest)).findFirst().orElseThrow();
                int state=CityErrands.state(p,q);
                if(state==0 && q.from()==npc.service()) add(out,"accept."+q.id(),"quest.accept",q.reward());
                if(state==1 && q.to()==npc.service()) out.add(new Option("finish."+q.id(),tr("quest.finish",q.reward()),CityErrands.ready(p,q)));
                add(out,"tasks","dialogue.tasks");add(out,"journal","dialogue.journal");
            }
            case GUIDE -> {
                add(out,"guide_awaken","dialogue.awaken");add(out,"guide_learn","dialogue.learn");add(out,"guide_power","dialogue.power");
            }
            case ESPER -> {
                out.add(new Option("demo",tr("dialogue.demo"),CityEspers.canDemonstrate(npc)));
                out.add(new Option("spar",tr("combat.spar"),!p.isCreative()&&!p.isSpectator()&&npc.getHealth()>=npc.getMaxHealth()*.5f));
            }
            case SPAR -> add(out,"spar_start","combat.spar_confirm");
        }
        if(s.page!=Page.MAIN) add(out,"main","dialogue.back");
        return List.copyOf(out);
    }
    private static Component body(ServerPlayer p,CityNpcEntity npc,Session s) {
        if(s.notice!=null) return s.notice;
        return switch(s.page) {
            case MAIN -> {
                var b=CityNpcDialogue.line(p,npc,0).copy().append("\n\n");
                if(medic(npc)) b.append(tr("dialogue.heal_info")).append("\n");
                if(engineer(npc)) b.append(tr("dialogue.repair_info")).append("\n");
                if(npc.esper()!=CityEspers.Profile.NONE) b.append(tr("esper.profile",npc.esper().label(),npc.esperLevel())).append("\n");
                yield b.append(tr("dialogue.choose"));
            }
            case TASKS -> {
                var b=tr("quest.board").copy();int available=0;
                for(var q:CityErrands.ALL) if(q.from()==npc.service()&&(CityErrands.state(p,q)==0||q.repeatable()) || q.to()==npc.service()&&CityErrands.state(p,q)==1) available++;
                yield b.append("\n\n").append(tr(available==0?"quest.none":"quest.board_help"));
            }
            case JOURNAL -> {
                var b=tr("quest.journal").copy();int active=0,done=0,repeated=0;
                for(var q:CityErrands.ALL) {
                    int state=CityErrands.state(p,q);if(state==2&&!q.repeatable()) done++;
                    if(q.repeatable())repeated+=CityErrands.data(p).getInt(q.id()+"_count");
                    if(state==1) {active++;b.append("\n\n").append(q.name()).append("\n").append(CityErrands.destination(q));
                        if(q.survey()!=null) b.append("\n").append(tr(CityErrands.data(p).getBoolean(q.id()+"_visited")?"quest.survey_done":"quest.survey_wait"));}
                }
                if(active==0) b.append("\n\n").append(tr("quest.no_active"));
                yield b.append("\n\n").append(tr("quest.summary",active,done,CityErrands.ALL.stream().filter(q->!q.repeatable()).count())).append("\n").append(tr("quest.repeat_summary",repeated));
            }
            case DETAIL -> {
                var q=CityErrands.ALL.stream().filter(e -> e.id().equals(s.quest)).findFirst().orElseThrow();
                var b=q.name().copy().append("\n\n").append(CityErrands.description(q)).append("\n\n").append(tr("quest.state."+CityErrands.state(p,q)));
                if(q.rideFrom()>=0&&CityErrands.state(p,q)==1)b.append("\n").append(tr(CityErrands.data(p).getBoolean(q.id()+"_rode")?"quest.ride_done":"quest.ride_wait"));
                if(q.repeatable()&&CityErrands.state(p,q)==2)b.append("\n").append(tr("quest.repeat_cooldown",(CityErrands.remaining(p,q)+19)/20));
                yield b;
            }
            case GUIDE -> tr("dialogue.guide_intro");
            case ESPER -> combatProfile(npc);
            case SPAR -> tr("combat.spar_rules");
        };
    }
    private static Component combatProfile(CityNpcEntity npc) {
        var brain=npc.combat();var s=brain.ability();
        var b=tr("esper.profile",npc.esper().label(),npc.esperLevel()).copy().append("\n\n").append(npc.esper().description()).append("\n\n")
                .append(tr("combat.stats",cn.academy.port.highpower.CityNpcPower.health(npc).toBigInteger(),cn.academy.port.highpower.CityNpcPower.maxHealth(npc).toBigInteger(),Math.round(s.cp),Math.round(s.maxCp()),Math.round(s.overload),Math.round(s.maxOverload())))
                .append("\n").append(tr("combat.player_power",cn.academy.port.highpower.HighPowerCombat.shieldText(npc),cn.academy.port.highpower.HighPowerCombat.maxShieldText(npc),String.format(java.util.Locale.ROOT,"%.2f",brain.additionalCp()),String.format(java.util.Locale.ROOT,"%.2f",brain.additionalMaximum())))
                .append("\n").append(tr(s.overloadFine?"combat.ready":"combat.overloaded")).append("\n\n");
        for(var d:brain.runtime().available()) {
            var c=brain.runtime().cost(d);
            b.append(Component.translatable("ac.ability."+d.category()+"."+d.id()+".name"))
                    .append("\n").append(tr("combat.skill_stats",d.level(),String.format(java.util.Locale.ROOT,"%.2f",c.cp()),Math.round(c.overload()),String.format(java.util.Locale.ROOT,"%.1f",c.damage()),c.cooldown()))
                    .append("\n").append(tr(cn.academy.port.canon.CanonSkills.knownSkill(d.id())?"combat.additional_pool":"combat.classic_pool"))
                    .append("\n");
        }
        return b.append("\n").append(tr("esper.rules"));
    }
    static void interrupt(CityNpcEntity npc) {
        if(!(npc.level() instanceof net.minecraft.server.level.ServerLevel level))return;
        for(var entry:List.copyOf(SESSIONS.entrySet()))if(entry.getValue().npc.equals(npc.getUUID())) {
            var player=level.getServer().getPlayerList().getPlayer(entry.getKey());if(player!=null)close(player,true,true);
        }
        if(npc.getTradingPlayer()!=null)npc.getTradingPlayer().closeContainer();
    }
    private static void send(ServerPlayer p,CityNpcEntity npc,Session s) {
        var title=npc.getCustomName()!=null?npc.getDisplayName():tr("resident."+npc.look().id());
        PacketDistributor.sendToPlayer(p,new CityDialogueNetwork.View(npc.getId(),npc.getUUID(),s.token,title,body(p,npc,s),
                CityErrands.coins(p),CityPlan.district(npc.blockPosition().getX(),npc.blockPosition().getZ()),options(p,npc,s)));
    }
    private static Component heal(ServerPlayer p,CityNpcEntity npc) {
        if(p.getHealth()>=p.getMaxHealth() && !p.hasEffect(MobEffects.POISON) && !p.hasEffect(MobEffects.WEAKNESS)) return tr("service.healthy");
        if(!CityErrands.pay(p,4)) return tr("service.no_coins",4);
        p.heal(p.getMaxHealth());p.removeEffect(MobEffects.POISON);p.removeEffect(MobEffects.WEAKNESS);
        npc.perform(CityNpcEntity.Activity.TREAT,CityNpcEntity.Expression.FOCUS);
        p.serverLevel().sendParticles(ParticleTypes.HEART,p.getX(),p.getY()+1.3,p.getZ(),8,.35,.4,.35,.02);
        return tr("service.healed",4);
    }
    private static int repairPrice(ItemStack held) {
        if(!held.isDamageableItem() || held.getDamageValue()==0) return 0;
        return 2+(int)Math.ceil(6.0*held.getDamageValue()/held.getMaxDamage());
    }
    private static Component repair(ServerPlayer p,CityNpcEntity npc) {
        var held=p.getMainHandItem();int cost=repairPrice(held);
        if(cost==0) return tr("service.no_repair");
        if(!CityErrands.pay(p,cost)) return tr("service.no_coins",cost);
        held.setDamageValue(0);p.getInventory().setChanged();p.containerMenu.broadcastChanges();
        npc.perform(CityNpcEntity.Activity.REPAIR,CityNpcEntity.Expression.FOCUS);
        p.serverLevel().sendParticles(ParticleTypes.WAX_OFF,npc.getX(),npc.getY()+1,npc.getZ(),10,.3,.3,.3,0);
        return tr("service.repaired",held.getHoverName(),cost);
    }
    private static void close(ServerPlayer p,boolean release,boolean client) {
        var old=SESSIONS.remove(p.getUUID());if(old==null) return;
        if(release && p.serverLevel().getEntity(old.npc) instanceof CityNpcEntity npc) npc.endConversation();
        if(client) PacketDistributor.sendToPlayer(p,new CityDialogueNetwork.View(-1,old.npc,old.token,Component.empty(),Component.empty(),0,0,List.of()));
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if(!(event.getEntity() instanceof ServerPlayer player)) return;
        CityErrands.tick(player);
        if(player.tickCount%20!=0) return;
        var s=SESSIONS.get(player.getUUID());if(s==null) return;
        if(!(player.serverLevel().getEntity(s.npc) instanceof CityNpcEntity npc) || !npc.isAlive()
                || npc.distanceToSqr(player)>64 || !npc.conversing()) close(player,true,true);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if(event.getEntity() instanceof ServerPlayer p) close(p,true,false);
    }
}
