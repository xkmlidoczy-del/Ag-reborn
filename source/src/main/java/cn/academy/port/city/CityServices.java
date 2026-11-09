package cn.academy.port.city;

import cn.academy.port.train.TrainManager;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Service actors are local ambient people; task progress belongs to each player. */
@EventBusSubscriber(modid = "academy")
public final class CityServices {
    private static final String ERRANDS = "academy:city_errands";
    private static final Map<ServerLevel, Map<CityServicePlan.Role, Actor>> LEVELS = new IdentityHashMap<>();
    private record Actor(CityNpcEntity clerk, CityNpcEntity customer) {}
    private CityServices() {}

    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(TrainManager.CITY)
                || !(level.getChunkSource().getGenerator() instanceof CityGreyboxGenerator)
                || level.getGameTime() % 40 != 0) return;
        var actors = LEVELS.computeIfAbsent(level, ignored -> new EnumMap<>(CityServicePlan.Role.class));
        for (var site : CityServicePlan.SITES) {
            boolean loaded = level.getChunkSource().getChunkNow(site.home().getX() >> 4, site.home().getZ() >> 4) != null;
            boolean nearby = loaded && level.players().stream().anyMatch(player -> player.distanceToSqr(Vec3.atCenterOf(site.home())) < 96 * 96);
            boolean enabled = nearby && CityServicePlan.open(site.role(), level.getDayTime())
                    && CityServicePlan.marker(level.getBlockState(site.home().below()));
            Actor actor = actors.get(site.role());
            if (!enabled) {
                if (actor != null) { discard(actor); actors.remove(site.role()); }
                continue;
            }
            if (actor == null || actor.clerk().isRemoved()) {
                if (actor != null) discard(actor);
                CityNpcEntity clerk = create(level, site.home(), site);
                if (clerk == null) { actors.remove(site.role()); continue; }
                CityNpcEntity customer = site.waiting() == null ? null : createCustomer(level, site.waiting());
                actor = new Actor(clerk, customer); actors.put(site.role(), actor);
            }
            if (site.waiting() != null && (actor.customer() == null || actor.customer().isRemoved())) {
                CityNpcEntity customer = createCustomer(level, site.waiting());
                if (customer != null) { actor = new Actor(actor.clerk(), customer); actors.put(site.role(), actor); }
            }
            CityNpcEntity clerk = actor.clerk();
            if (actor.customer() != null && !actor.customer().conversing() && level.getGameTime() % 240 == 0) {
                var action = switch ((int)Math.floorMod(level.getGameTime() / 240, 3)) {
                    case 0 -> CityNpcEntity.Activity.PHONE;
                    case 1 -> CityNpcEntity.Activity.DRINK;
                    default -> CityNpcEntity.Activity.STAND;
                };
                actor.customer().perform(action, action.expression);
            }
            if (clerk.getTradingPlayer() == null && !clerk.conversing() && level.getGameTime() % 160 == 0) {
                boolean toShelf = Math.floorMod(level.getGameTime() / 160 + site.role().ordinal(), 2) == 0;
                BlockPos target = toShelf ? site.shelf() : site.home();
                clerk.getNavigation().moveTo(target.getX()+.5, target.getY(), target.getZ()+.5, .72);
                var action = !toShelf ? CityNpcEntity.Activity.STAND : switch (site.role()) {
                    case MENTOR, EXCHANGE -> CityNpcEntity.Activity.CHECK;
                    case LIBRARIAN -> CityNpcEntity.Activity.READ;
                    default -> CityNpcEntity.Activity.TEND;
                };
                clerk.perform(action, action.expression);
            }
        }
    }
    private static CityNpcEntity create(ServerLevel level, BlockPos pos, CityServicePlan.Site site) {
        if (!level.isEmptyBlock(pos) || !level.isEmptyBlock(pos.above())) return null;
        var npc = CityNpcContent.NPC.get().create(level);
        if (npc == null) return null;
        npc.moveTo(pos.getX()+.5, pos.getY(), pos.getZ()+.5, 180, 0); npc.prepareService(site);
        return level.noCollision(npc) && level.addFreshEntity(npc) ? npc : null;
    }
    private static CityNpcEntity createCustomer(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.isEmptyBlock(pos) || !level.isEmptyBlock(pos.above())) return null;
        var npc = CityNpcContent.NPC.get().create(level);
        if (npc == null) return null;
        npc.moveTo(pos.getX()+.5, pos.getY(), pos.getZ()+.5, 180, 0);
        var look = switch (CityPlan.district(pos.getX(),pos.getZ())) {
            case 7 -> CityNpcEntity.Look.STUDENT_CASUAL;
            case 23 -> CityNpcEntity.Look.TRAVELER;
            default -> CityNpcEntity.Look.CASUAL_VISITOR;
        };
        npc.prepareAmbient(look, CityNpcEntity.Activity.PHONE);
        return level.noCollision(npc) && level.addFreshEntity(npc) ? npc : null;
    }
    private static void discard(Actor actor) {
        if (actor.clerk().getTradingPlayer() != null) actor.clerk().getTradingPlayer().closeContainer();
        actor.clerk().discard(); if (actor.customer()!=null) actor.customer().discard();
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) LEVELS.remove(level);
    }

    static void openShop(ServerPlayer player, CityNpcEntity npc) {
        var role=npc.service();
        if(!CityServicePlan.open(role,player.serverLevel().getDayTime())) {message(player,"closed");return;}
        if(npc.getTradingPlayer()!=null && npc.getTradingPlayer()!=player) {message(player,"busy");return;}
        var site=CityServicePlan.site(role);
        if(site.shop().isEmpty()) return;
        npc.trade(player,role==CityServicePlan.Role.EXCHANGE?CityCatalog.exchange():CityCatalog.offers(site.shop()));
    }
    private static void message(ServerPlayer player, String id) { player.sendSystemMessage(Component.translatable("academy.city.message." + id)); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer original && event.getEntity() instanceof ServerPlayer replacement
                && original.getPersistentData().contains(ERRANDS))
            replacement.getPersistentData().put(ERRANDS, original.getPersistentData().get(ERRANDS).copy());
    }
}
