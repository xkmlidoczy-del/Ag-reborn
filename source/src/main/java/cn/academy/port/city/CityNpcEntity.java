package cn.academy.port.city;

import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;

/** City residents share the same renderer. Hand-placed samples retain their state in the world. */
public final class CityNpcEntity extends PathfinderMob implements Merchant {
    public enum Look {
        STUDENT_BAG(true), STUDENT_SPORT(false), STUDENT_BOOKS(true), COMMUTER(false), VISITOR(false), SHOP_CLERK(true),
        RESIDENT(true), CASUAL_VISITOR(true), OFFICE_STAFF(true), VENDOR(false), RESEARCHER(true), GUARD(false),
        TECHNICIAN(false), ATHLETE(false), PERFORMER(true), AIRPORT_STAFF(true),
        STUDENT_BLAZER(true), STUDENT_CASUAL(false), TEACHER(true), BARISTA(true),
        RAIL_STAFF(false), MEDIC(true), ENGINEER(false), TRAVELER(true),
        ESPER_UNIFORM(true), ESPER_TRACK(false), ESPER_LAB(true), ESPER_WORK(false), ESPER_ART(true), ESPER_FIELD(false);

        private final boolean slim;
        private final String id = name().toLowerCase(Locale.ROOT);

        Look(boolean slim) { this.slim = slim; }
        public String id() { return id; }
        public boolean slim() { return slim; }
    }

    public enum Activity {
        STAND(Expression.NEUTRAL), PHONE(Expression.FOCUS), SIT(Expression.NEUTRAL), READ(Expression.FOCUS),
        WAVE(Expression.SMILE), CHAT(Expression.SMILE), PHOTO(Expression.FOCUS), CHECK(Expression.FOCUS),
        STRETCH(Expression.NEUTRAL), TEND(Expression.FOCUS), SCAN(Expression.FOCUS), PERFORM(Expression.SMILE),
        EAT(Expression.NEUTRAL), DRINK(Expression.NEUTRAL), CAST(Expression.FOCUS), TREAT(Expression.FOCUS), REPAIR(Expression.FOCUS);

        final Expression expression;
        private final String id = name().toLowerCase(Locale.ROOT);
        Activity(Expression expression) { this.expression = expression; }
        public String id() { return id; }
    }

    public enum Expression {
        NEUTRAL, SMILE, FOCUS, SURPRISED;
        private final String id = name().toLowerCase(Locale.ROOT);
        public String id() { return id; }
    }

    private static final List<Look> LOOKS = List.of(Look.values());
    private static final List<Activity> ACTIVITIES = List.of(Activity.values());
    private static final List<Expression> EXPRESSIONS = List.of(Expression.values());
    private static final EntityDataAccessor<Integer> LOOK = SynchedEntityData.defineId(CityNpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTIVITY = SynchedEntityData.defineId(CityNpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> EXPRESSION = SynchedEntityData.defineId(CityNpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SERVICE = SynchedEntityData.defineId(CityNpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ESPER = SynchedEntityData.defineId(CityNpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ABILITY_LEVEL = SynchedEntityData.defineId(CityNpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> CP = SynchedEntityData.defineId(CityNpcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> MAX_CP = SynchedEntityData.defineId(CityNpcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> OVERLOAD = SynchedEntityData.defineId(CityNpcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> MAX_OVERLOAD = SynchedEntityData.defineId(CityNpcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SHIELD = SynchedEntityData.defineId(CityNpcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> COMBAT_MODE = SynchedEntityData.defineId(CityNpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> CAST_SKILL = SynchedEntityData.defineId(CityNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> SPARRING = SynchedEntityData.defineId(CityNpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> COMMUTER = SynchedEntityData.defineId(CityNpcEntity.class, EntityDataSerializers.STRING);
    private boolean ambient;
    private Player tradingPlayer;
    private MerchantOffers offers = new MerchantOffers();
    private long conversationUntil;
    private java.util.UUID conversationPartner;
    private Activity interruptedActivity;
    private Expression interruptedExpression;
    private CityNpcCombat combat;

    public CityNpcEntity(EntityType<? extends CityNpcEntity> type, Level level) {
        super(type, level);
    }

    public Look look() { return LOOKS.get(entityData.get(LOOK)); }
    public Activity activity() { return ACTIVITIES.get(entityData.get(ACTIVITY)); }
    public Expression expression() { return EXPRESSIONS.get(entityData.get(EXPRESSION)); }
    public boolean ambient() { return ambient; }
    public CityEspers.Profile esper() { return CityEspers.Profile.values()[entityData.get(ESPER)]; }
    public int esperLevel() {return entityData.get(ABILITY_LEVEL)>0?entityData.get(ABILITY_LEVEL):esper().level();}
    void abilityLevel(int value){entityData.set(ABILITY_LEVEL,Math.max(0,Math.min(5,value)));}
    public float abilityCp(){return entityData.get(CP);}
    public float abilityMaxCp(){return entityData.get(MAX_CP);}
    public float abilityOverload(){return entityData.get(OVERLOAD);}
    public float abilityMaxOverload(){return entityData.get(MAX_OVERLOAD);}
    public float abilityShield(){return entityData.get(SHIELD);}
    public CityNpcCombat.Mode combatMode(){return CityNpcCombat.Mode.values()[entityData.get(COMBAT_MODE)];}
    public String castSkill(){return entityData.get(CAST_SKILL);}
    public boolean sparring(){return entityData.get(SPARRING);}
    public String commuterId(){return entityData.get(COMMUTER);}
    void commuter(String id){entityData.set(COMMUTER,id);}
    @Override public net.minecraft.world.phys.Vec3 getVehicleAttachmentPoint(net.minecraft.world.entity.Entity vehicle){
        return vehicle instanceof cn.academy.port.train.TrainCarEntity?new net.minecraft.world.phys.Vec3(0,.6,0):super.getVehicleAttachmentPoint(vehicle);
    }
    public CityNpcCombat combat(){if(combat==null)combat=new CityNpcCombat(this);return combat;}
    void identity(String value){combat().identity(value);}
    void combatSnapshot(cn.academy.port.core.AbilityProgress state,CityNpcCombat.Mode mode,String skill,double shield) {
        entityData.set(CP,(float)state.cp);entityData.set(MAX_CP,(float)state.maxCp());
        entityData.set(OVERLOAD,(float)state.overload);entityData.set(MAX_OVERLOAD,(float)state.maxOverload());
        entityData.set(COMBAT_MODE,mode.ordinal());entityData.set(CAST_SKILL,skill);entityData.set(SHIELD,(float)shield);
        entityData.set(SPARRING,combat!=null&&combat.dueling());
    }
    void setEsper(CityEspers.Profile profile) {entityData.set(ESPER,profile.ordinal());abilityLevel(profile.level());}
    void setLook(Look look) { entityData.set(LOOK,look.ordinal()); }
    CityServicePlan.Role service() { return CityServicePlan.Role.values()[entityData.get(SERVICE)]; }

    void prepareAmbient(Look look, Activity activity) {
        ambient = true;
        setInvulnerable(true);
        entityData.set(LOOK, look.ordinal());
        perform(activity, activity.expression);
    }

    void prepareService(CityServicePlan.Site site) {
        prepareAmbient(site.look(), site.role() == CityServicePlan.Role.LIBRARIAN ? Activity.READ : Activity.TEND);
        entityData.set(SERVICE, site.role().ordinal());
        setCustomName(Component.translatable("academy.city.npc." + site.role().name().toLowerCase(Locale.ROOT)));
        setCustomNameVisible(true);
        CityEspers.prepareService(this,site.role());
        identity("service:"+site.role().name());
    }

    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return super.mobInteract(player, hand);
        if (!level().isClientSide && player instanceof net.minecraft.server.level.ServerPlayer server) {
            if(combat().dueling(server)){combat().finishDuel("duel_finished");return InteractionResult.CONSUME;}
            CityDialogue.open(server,this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    void beginConversation(Player player) {
        if (!conversing()) { interruptedActivity=activity(); interruptedExpression=expression(); }
        conversationUntil=level().getGameTime()+200;
        conversationPartner=player==null?null:player.getUUID();
        getNavigation().stop();
        if(player!=null) getLookControl().setLookAt(player, 30, 30);
        perform(Activity.CHAT, Expression.SMILE);
    }
    boolean conversing() { return conversationUntil > level().getGameTime()||combat!=null&&combat.busy(); }
    boolean occupiedByOther(Player player) {
        return getTradingPlayer()!=null && getTradingPlayer()!=player
                || conversing() && (conversationPartner==null || !conversationPartner.equals(player.getUUID()));
    }
    void extendConversation() { conversationUntil=level().getGameTime()+200; }
    void endConversation() {
        conversationUntil=0; conversationPartner=null;
        if(interruptedActivity!=null) perform(interruptedActivity,interruptedExpression);
        interruptedActivity=null;interruptedExpression=null;
    }
    @Override public void tick() {
        super.tick();
        if(!level().isClientSide) combat().tick();
        if (level().isClientSide || conversationUntil == 0) return;
        if (conversing()) {
            if (tickCount % 8 == 0 && conversationPartner != null && level() instanceof net.minecraft.server.level.ServerLevel server) {
                var partner=server.getPlayerByUUID(conversationPartner);
                if (partner != null && distanceToSqr(partner) < 64) getLookControl().setLookAt(partner,30,30);
            }
        } else {
            endConversation();
        }
    }

    void trade(Player player, MerchantOffers available) {
        offers = available;
        setTradingPlayer(player);
        getNavigation().stop();
        openTradingScreen(player, getDisplayName(), 1);
    }
    @Override public void setTradingPlayer(@Nullable Player player) { tradingPlayer = player; }
    @Override @Nullable public Player getTradingPlayer() { return tradingPlayer; }
    @Override public MerchantOffers getOffers() { return offers; }
    @Override public void overrideOffers(MerchantOffers available) { offers = available; }
    @Override public void notifyTrade(MerchantOffer offer) { offer.increaseUses(); }
    @Override public void notifyTradeUpdated(ItemStack stack) {}
    @Override public int getVillagerXp() { return 0; }
    @Override public void overrideXp(int xp) {}
    @Override public boolean showProgressBar() { return false; }
    @Override public SoundEvent getNotifyTradeSound() { return SoundEvents.VILLAGER_YES; }
    @Override public boolean isClientSide() { return level().isClientSide; }

    void perform(Activity activity, Expression expression) {
        entityData.set(ACTIVITY, activity.ordinal());
        entityData.set(EXPRESSION, expression.ordinal());
    }

    @Override
    public boolean shouldBeSaved() {
        return !ambient && super.shouldBeSaved();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(LOOK, Look.STUDENT_BAG.ordinal());
        builder.define(ACTIVITY, Activity.STAND.ordinal());
        builder.define(EXPRESSION, Expression.NEUTRAL.ordinal());
        builder.define(SERVICE, CityServicePlan.Role.NONE.ordinal());
        builder.define(ESPER,CityEspers.Profile.NONE.ordinal());
        builder.define(ABILITY_LEVEL,0);builder.define(CP,0f);builder.define(MAX_CP,0f);builder.define(OVERLOAD,0f);builder.define(MAX_OVERLOAD,0f);
        builder.define(SHIELD,0f);builder.define(COMBAT_MODE,CityNpcCombat.Mode.DAILY.ordinal());builder.define(CAST_SKILL,"");
        builder.define(SPARRING,false);
        builder.define(COMMUTER,"");
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        Look look = LOOKS.stream().filter(candidate -> candidate.id().equals(tag.getString("Look")))
                .findFirst().orElse(Look.STUDENT_BAG);
        Activity activity = ACTIVITIES.stream().filter(candidate -> candidate.id().equals(tag.getString("Activity")))
                .findFirst().orElse(Activity.STAND);
        Expression expression = EXPRESSIONS.stream().filter(candidate -> candidate.id().equals(tag.getString("Expression")))
                .findFirst().orElse(activity.expression);
        entityData.set(LOOK, look.ordinal());
        setEsper(CityEspers.Profile.byId(tag.getString("Esper")));
        if(tag.contains("EsperLevel"))abilityLevel(esper()==CityEspers.Profile.NONE?0:Math.max(1,Math.min(5,tag.getInt("EsperLevel"))));
        perform(activity, expression);
        if(tag.contains("NpcAbility")&&!level().isClientSide)combat().load(tag.getCompound("NpcAbility"));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Look", look().id());
        tag.putString("Activity", activity().id());
        tag.putString("Expression", expression().id());
        tag.putString("Esper",esper().id());
        tag.putInt("EsperLevel",esperLevel());
        if(combat!=null)tag.put("NpcAbility",combat.save());
    }
    @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source){return !cn.academy.port.city.defense.CityDefense.exposesCivilian(this,source)&&super.isInvulnerableTo(source);}
    @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount) {
        if(!level().isClientSide)amount=combat().protect(source,amount);
        return amount>0&&super.hurt(source,amount);
    }
    @Override public void remove(RemovalReason reason) {
        if(!level().isClientSide&&combat!=null)combat.close();super.remove(reason);
    }
}
