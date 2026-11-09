package net.neoforged.bus;
import net.neoforged.bus.api.*;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
public class EventBus implements IEventBus {
    public Event dispatch(Event event,Operation<Event> callback,Object[] args) {
        return post(event,new EventListener[] {
            e->callback.call(args)
        }
        );
    }
    private Event post(Event event,EventListener[] listeners) {
        for(var listener:listeners)listener.invoke(event);
        return event;
    }
}
