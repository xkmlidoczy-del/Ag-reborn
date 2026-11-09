package net.neoforged.neoforge.event.entity.living;
import net.neoforged.bus.api.Event;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
public class LivingDamageEvent {
    public static class Post extends Event {
        public final LivingEntity entity;
        public final DamageContainer container;
        public Post(LivingEntity e,DamageContainer c) {
            entity=e;
            container=c;
        }
    }
}
