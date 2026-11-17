package dev.acafauna.entity.ai;

import dev.acafauna.entity.FaunaAnimal;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.tags.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Server-only ecology, bounded searches, no block mutation and no forced chunks. */
public final class EcologyGoals {
    private abstract static class Base extends Goal {
        final FaunaAnimal a;
        private int nextCheck;
        Base(FaunaAnimal a){this.a=a;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
        final boolean due(int interval){if(a.tickCount<nextCheck)return false;nextCheck=a.tickCount+interval;return true;}
    }
    public static final class Flee extends Base {
        private Vec3 destination;
        private int until;
        public Flee(FaunaAnimal a){super(a);}
        @Override public boolean canUse(){
            if(a.getTarget()!=null)return false;
            LivingEntity threat=null;
            if(a.panicking())threat=a.getLastHurtByMob();
            if(threat==null&&a.profile().passive()&&due(20))
                threat=a.level().getNearestPlayer(a,Math.max(3,a.getBbWidth()*3));
            if(threat instanceof Player p&&(p.isCreative()||p.isSpectator()||a.isFood(p.getMainHandItem())))return false;
            if(threat==null)return false;
            Vec3 away=a.position().subtract(threat.position()).multiply(1,0,1).normalize();
            destination=a.position().add(away.scale(8));until=a.tickCount+60;return true;
        }
        @Override public void start(){a.pose(FaunaAnimal.ALERT,16);a.sound("alert",.35F);}
        @Override public boolean canContinueToUse(){return a.tickCount<until&&a.getTarget()==null;}
        @Override public void tick(){
            if(a.profile().flies())a.getMoveControl().setWantedPosition(destination.x,a.getY()+3,destination.z,1.5);
            else a.getNavigation().moveTo(destination.x,destination.y,destination.z,1.6);
        }
    }
    public static final class Hunt extends Base {
        private LivingEntity candidate;
        public Hunt(FaunaAnimal a){super(a);}
        @Override public boolean canUse(){
            if(a.isBaby())return false;
            if(a.getTarget()!=null){candidate=a.getTarget();return true;}
            if(!a.hungry()||!due(20))return false;
            if(a.profile().conditional()) {
                Player p=a.level().players().stream().filter(player->a.mayHuntPlayer(player)&&a.distanceToSqr(player)<=a.profile().aggression()*a.profile().aggression())
                    .min(Comparator.comparingDouble(a::distanceToSqr)).orElse(null);
                if(p!=null&&a.mayHuntPlayer(p)&&a.hasLineOfSight(p)){candidate=p;return true;}
            }
            if(!a.profile().predator())return false;
            candidate=a.level().getEntitiesOfClass(LivingEntity.class,a.getBoundingBox().inflate(16),
                e->a.isPrey(e)&&a.hasLineOfSight(e)).stream().min(Comparator.comparingDouble(a::distanceToSqr)).orElse(null);
            return candidate!=null;
        }
        @Override public boolean canContinueToUse(){
            if(candidate==null||!candidate.isAlive()||a.distanceToSqr(candidate)>24*24)return false;
            if(candidate instanceof Player p)return a.validPlayer(p)&&(a.hasAngerAt(p)||a.mayHuntPlayer(p)&&a.hasLineOfSight(p));
            return a.hasAngerAt(candidate)||(a.hungry()&&a.isPrey(candidate));
        }
        @Override public void start(){
            a.resetWarning();
            if(candidate instanceof Player&&!a.hasAngerAt(candidate))a.warn(20);
            else a.setTarget(candidate);
        }
        @Override public void tick(){
            // GoalSelector can tick once more before it rechecks continuation.
            if(candidate==null||!candidate.isAlive())return;
            a.getLookControl().setLookAt(candidate,30,30);
            if(candidate instanceof Player&&!a.hasAngerAt(candidate)&&a.warningTicks()<20) {
                if(!a.hasLineOfSight(candidate)||a.distanceToSqr(candidate)>a.profile().aggression()*a.profile().aggression()){candidate=null;return;}
                a.warningTick();a.getNavigation().stop();return;
            }
            a.setTarget(candidate);
            if(a.getTarget()!=candidate)return;
            a.getNavigation().moveTo(candidate,a.profile().id().equals("cheetah")?1.8:1.35);
            a.doHurtTarget(candidate);
        }
        @Override public void stop(){a.setTarget(null);a.resetWarning();candidate=null;}
        @Override public boolean requiresUpdateEveryTick(){return true;}
    }
    public static final class Feed extends Base {
        private ItemEntity food;
        private int until;
        public Feed(FaunaAnimal a){super(a);}
        @Override public boolean canUse(){
            if(!a.canForage()||!due(40))return false;
            food=a.level().getEntitiesOfClass(ItemEntity.class,a.getBoundingBox().inflate(8),
                item->item.isAlive()&&a.isFood(item.getItem())).stream().min(Comparator.comparingDouble(a::distanceToSqr)).orElse(null);
            if(food!=null)return true;
            if(!a.profile().diet().equals("HERBIVORE")&&!a.profile().diet().equals("OMNIVORE"))return false;
            var ground=a.level().getBlockState(a.blockPosition().below());
            return ground.is(Blocks.GRASS_BLOCK)||ground.is(BlockTags.LEAVES)||a.isInWater()&&a.profile().amphibious();
        }
        @Override public void start(){until=a.tickCount+60;if(food==null){a.getNavigation().stop();a.pose(FaunaAnimal.EATING,40);}}
        @Override public boolean canContinueToUse(){return a.tickCount<until&&a.hungry()&&a.getTarget()==null;}
        @Override public void tick(){
            if(food==null){if(a.tickCount>=until-20)a.fed();return;}
            if(!food.isAlive()){until=0;return;}
            a.getNavigation().moveTo(food,1.0);
            if(a.distanceToSqr(food)<Math.pow(a.getBbWidth()*.5+1,2)) {
                food.getItem().shrink(1);if(food.getItem().isEmpty())food.discard();a.fed();until=0;
            }
        }
        @Override public void stop(){food=null;}
    }
    public static final class Breathe extends Base {
        private BlockPos target;
        public Breathe(FaunaAnimal a){super(a);}
        @Override public boolean canUse(){
            if(!a.profile().aquatic()&&!a.profile().amphibious())return false;
            if(a.profile().mobility().equals("AQUATIC"))return false;
            if(a.getAirSupply()>500||!a.isInWater())return false;
            var pos=a.blockPosition();target=null;
            for(int y=1;y<=16;y++) {
                var p=pos.above(y);
                if(!a.level().hasChunkAt(p))break;
                if(!a.level().getFluidState(p).is(FluidTags.WATER)&&a.level().getBlockState(p).isAir()){target=p;break;}
            }
            return target!=null;
        }
        @Override public boolean canContinueToUse(){return a.isInWater()&&a.getAirSupply()<a.getMaxAirSupply()-40&&target!=null;}
        @Override public void tick(){
            a.getNavigation().moveTo(target.getX()+.5,target.getY()+.1,target.getZ()+.5,1.4);
            a.getMoveControl().setWantedPosition(target.getX()+.5,target.getY()+.1,target.getZ()+.5,1.4);
            a.setDeltaMovement(a.getDeltaMovement().add(0,.035,0));
        }
    }
    public static final class Herd extends Base {
        private FaunaAnimal leader;
        private int nextSteer;
        public Herd(FaunaAnimal a){super(a);}
        @Override public boolean canUse(){
            if(!a.profile().herd()||!due(40)||a.getTarget()!=null)return false;
            leader=a.level().getEntitiesOfClass(FaunaAnimal.class,a.getBoundingBox().inflate(12),
                other->other!=a&&other.getType()==a.getType()&&other.isAlive()&&other.getId()<a.getId())
                .stream().min(Comparator.comparingInt(Entity::getId)).orElse(null);
            return leader!=null&&a.distanceToSqr(leader)>36;
        }
        @Override public boolean canContinueToUse(){return leader!=null&&leader.isAlive()&&a.distanceToSqr(leader)>16&&a.distanceToSqr(leader)<24*24&&a.getTarget()==null;}
        @Override public void start(){nextSteer=0;}
        @Override public void tick(){if(a.tickCount>=nextSteer){nextSteer=a.tickCount+10;a.getNavigation().moveTo(leader,1.1);}}
    }
    public static final class Signature extends Base {
        private int until,selected;
        public Signature(FaunaAnimal a){super(a);}
        private boolean supports(int pose){
            if(pose==FaunaAnimal.RESTING)return true;
            return switch(a.profile().id()){
                case "nile_crocodile"->a.onGround()&&!a.isInWater();
                case "brown_bear"->pose==FaunaAnimal.SPECIAL_A?a.onGround()&&!a.isInWater():a.canWade();
                case "mallard_duck"->pose==FaunaAnimal.SPECIAL_A?a.isInWater():a.onGround()&&!a.isInWater();
                case "hippopotamus"->pose==FaunaAnimal.SPECIAL_A||a.isInWater();
                case "malayan_tapir","beaver"->pose==FaunaAnimal.SPECIAL_A?a.onGround()&&!a.isInWater():a.isInWater();
                case "capybara"->pose==FaunaAnimal.SPECIAL_A?a.isInWater():a.onGround()&&!a.isInWater();
                case "harbor_seal"->pose==FaunaAnimal.SPECIAL_A?a.onGround()&&!a.isInWater():a.isInWater()||a.onGround();
                case "flamingo"->pose==FaunaAnimal.SPECIAL_A?a.onGround()&&!a.isInWater():a.canWade()||a.isInWater()||a.onGround();
                case "woodpecker"->pose==FaunaAnimal.SPECIAL_A?a.onGround():a.touchesTree();
                case "bald_eagle"->pose==FaunaAnimal.SPECIAL_A||a.isInWater()||a.level().getFluidState(a.blockPosition().below()).is(FluidTags.WATER);
                case "sea_otter","orca"->a.isInWater()&&!a.isUnderWater();
                case "bottlenose_dolphin"->a.isInWater()&&(pose==FaunaAnimal.SPECIAL_B||!a.isUnderWater());
                case "bluespotted_ray"->a.isInWater()&&(pose==FaunaAnimal.SPECIAL_B||a.onGround());
                case "hammerhead_shark"->a.isInWater();
                default->true;
            };
        }
        @Override public boolean canUse(){
            if(a.getTarget()!=null||a.panicking()||!due(40)||a.getRandom().nextInt(6)!=0)return false;
            if(!a.isActiveTime()){selected=FaunaAnimal.RESTING;return true;}
            if(a.profile().id().equals("two_toed_sloth")){selected=FaunaAnimal.SPECIAL_B;return true;}
            boolean first=a.getRandom().nextBoolean();
            selected=first?FaunaAnimal.SPECIAL_A:FaunaAnimal.SPECIAL_B;
            if(!supports(selected))selected=first?FaunaAnimal.SPECIAL_B:FaunaAnimal.SPECIAL_A;
            return supports(selected);
        }
        @Override public void start(){
            until=a.tickCount+40+a.getRandom().nextInt(60);a.getNavigation().stop();
            a.pose(selected,until-a.tickCount);
            if(selected!=FaunaAnimal.RESTING)a.sound("special",.3F);
        }
        @Override public boolean canContinueToUse(){return a.tickCount<until&&a.getTarget()==null&&!a.panicking()&&supports(selected);}
        @Override public void stop(){a.pose(FaunaAnimal.NORMAL,0);}
    }
    public static final class Hang extends Base {
        // Height of the highest supporting hooks in animation.two_toed_sloth.special_a.
        private static final double GRIP_HEIGHT = .68;
        private BlockPos branch;
        private Vec3 anchor;
        private int until, retryAfter;

        public Hang(FaunaAnimal animal) { super(animal); }

        @Override public boolean canUse() {
            if(a.tickCount<retryAfter||!due(20)||a.panicking()||a.getTarget()!=null||a.isInWater())return false;
            double grip=GRIP_HEIGHT*(a.isBaby()?.5:1.0);
            int height=(int)Math.round(a.getY()+grip);
            BlockPos candidate=BlockPos.containing(a.getX(),height,a.getZ());
            Vec3 place=new Vec3(candidate.getX()+.5,height-grip,candidate.getZ()+.5);
            if(Math.abs(place.y-a.getY())>.42||a.position().distanceToSqr(place)>.42*.42
                    ||!isBranch(candidate)||!a.level().noCollision(a,a.getBoundingBox().move(place.subtract(a.position()))))return false;
            branch=candidate;
            anchor=place;
            return true;
        }

        private boolean isBranch(BlockPos pos) {
            if(!a.level().hasChunkAt(pos))return false;
            var state=a.level().getBlockState(pos);
            return state.is(BlockTags.LOGS)||state.is(BlockTags.LEAVES);
        }

        @Override public void start() {
            int duration=84+a.getRandom().nextInt(36);
            until=a.tickCount+duration;
            a.getNavigation().stop();
            a.pose(FaunaAnimal.SPECIAL_A,duration);
            a.sound("special",.3F);
            tick();
        }

        @Override public boolean canContinueToUse() {
            return a.tickCount<until&&a.action()==FaunaAnimal.SPECIAL_A&&a.getTarget()==null
                    &&!a.panicking()&&!a.isInWater()&&a.position().distanceToSqr(anchor)<.25*.25
                    &&isBranch(branch)&&a.level().noCollision(a,a.getBoundingBox());
        }

        @Override public void tick() {
            a.setPos(anchor.x,anchor.y,anchor.z);
            a.setDeltaMovement(Vec3.ZERO);
            a.fallDistance=0;
        }

        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override public void stop() {
            if(a.action()==FaunaAnimal.SPECIAL_A)a.pose(FaunaAnimal.NORMAL,0);
            retryAfter=a.tickCount+80;
        }
    }
    public static final class Habitat extends Base {
        private Vec3 target;
        private int until;
        private boolean landing;
        public Habitat(FaunaAnimal a){super(a);}
        @Override public boolean canUse(){
            if(!due(100)||a.getTarget()!=null)return false;
            boolean seekWater=a.profile().aquatic()&&!a.isInWater()
                ||a.profile().amphibious()&&!a.isInWater()&&a.getRandom().nextBoolean();
            boolean seekTree=a.profile().climbs()&&a.getRandom().nextBoolean();
            boolean seekLand=a.profile().amphibious()&&a.isInWater()&&a.getRandom().nextInt(4)==0
                ||a.profile().flies()&&!a.onGround()&&a.getRandom().nextBoolean();
            landing=seekLand&&a.profile().flies()&&!a.isInWater();
            if(!seekWater&&!seekTree&&!seekLand)return false;
            target=null;double best=Double.MAX_VALUE;
            for(int dx=-8;dx<=8;dx++)for(int dz=-8;dz<=8;dz++)for(int dy=a.profile().flies()?-12:-3;dy<=3;dy++) {
                var pos=a.blockPosition().offset(dx,dy,dz);
                if(!a.level().hasChunkAt(pos))continue;
                var state=a.level().getBlockState(pos);
                boolean valid=seekWater?state.getFluidState().is(FluidTags.WATER):
                    seekTree?state.is(BlockTags.LOGS):state.isAir()&&a.level().getBlockState(pos.below()).isSolid();
                double distance=pos.distSqr(a.blockPosition());
                if(valid&&distance<best&&distance>2){best=distance;target=Vec3.atCenterOf(seekTree?pos.relative(Direction.NORTH):pos);}
            }
            return target!=null;
        }
        @Override public void start(){until=a.tickCount+100;}
        @Override public boolean canContinueToUse(){return target!=null&&a.tickCount<until&&a.getTarget()==null&&(landing?!a.onGround():a.position().distanceToSqr(target)>1);}
        @Override public void tick(){
            a.getNavigation().moveTo(target.x,target.y,target.z,1.0);
            if(landing&&a.position().distanceToSqr(target)<2.25){
                a.getNavigation().stop();a.setNoGravity(false);a.setYya(0);a.setZza(0);
                a.setDeltaMovement(a.getDeltaMovement().multiply(.75,1,.75).add(0,-.08,0));
            }
            if(a.profile().aquatic()&&!a.isInWater()) {
                var movement=target.subtract(a.position()).multiply(1,0,1).normalize().scale(.08);
                a.setDeltaMovement(movement.x,a.onGround()?.22:a.getDeltaMovement().y,movement.z);
            }
        }
        @Override public void stop(){if(landing&&a.onGround())a.pose(FaunaAnimal.RESTING,40);}
    }
    public static final class LandedRest extends Base {
        public LandedRest(FaunaAnimal a){super(a);}
        @Override public boolean canUse(){return a.profile().flies()&&a.onGround()&&a.action()==FaunaAnimal.RESTING&&!a.panicking()&&a.getTarget()==null;}
        @Override public boolean canContinueToUse(){return canUse();}
        @Override public void tick(){a.getNavigation().stop();}
    }
    private EcologyGoals(){}
}
