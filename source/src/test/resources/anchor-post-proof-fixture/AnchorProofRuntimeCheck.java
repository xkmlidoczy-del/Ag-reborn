import cn.academy.port.highpower.*;
import cn.academy.port.highpower.mixin.NativeAnchorPostMixinPlugin;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.EventBus;
import net.neoforged.bus.api.Event;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
public class AnchorProofRuntimeCheck {
    static int checks,calls;
    static LivingEntity entity=new LivingEntity();
    static DamageContainer container=new DamageContainer();
    static void check(boolean value,String message) {
        checks++;
        if(!value)throw new AssertionError(message);
    }
    static void pending() {
        HighPowerCombat.pending.add(entity);
        HighPowerCombat.guards.add(entity);
        NativeAnchorPostMixinPlugin.available=true;
    }
    static Event busPost(IEventBus bus,Event event,Operation<Event> original,LivingEntity entity,DamageContainer container) {
        return AnchorDamagePostProof.nativeBusPost(bus,event,a->bus instanceof EventBus actual?actual.dispatch(event,original,a):original.call(a),entity,container);
    }
    static void deep(int depth,Runnable action) {
        if(depth>0)deep(depth-1,action);
        else action.run();
    }
    public static void main(String[] args) {
        Post post=new Post(entity,container);
        check(!AnchorDamagePostProof.matches(post),"no native scope");
        pending();
        AnchorDamagePostProof.nativePost(entity,container,a-> {
            calls++;
            busPost(NeoForge.EVENT_BUS,post,b-> {
                calls++;
                check(!AnchorDamagePostProof.matches(new Post(entity,container)),"matching values are not identity");
                check(AnchorDamagePostProof.matches(post),"actual event authenticates once");
                check(!AnchorDamagePostProof.matches(post),"replay refused");
                return post;
            }
            ,entity,container);
            return null;
        }
        );
        check(calls==2,"both original operations run once");
        check(!AnchorDamagePostProof.matches(post),"receipt closed after return");
        pending();
        AnchorDamagePostProof.nativePost(entity,container,a-> {
            busPost(NeoForge.EVENT_BUS,post,b-> {
                Post synthetic=new Post(entity,container);
                busPost(NeoForge.EVENT_BUS,synthetic,c-> {
                    check(!AnchorDamagePostProof.matches(synthetic),"nested helper synthetic refused");
                    check(!AnchorDamagePostProof.matches(post),"outer receipt suspended during nested helper");
                    check(HighPowerCombat.anchorFallPending(entity),"pending guard retained for synthetic dispatch");
                    return synthetic;
                }
                ,entity,container);
                check(AnchorDamagePostProof.matches(post),"outer identity restored after nested helper");
                return post;
            }
            ,entity,container);
            return null;
        }
        );
        pending();
        AnchorDamagePostProof.nativePost(entity,container,a-> {
            busPost(NeoForge.EVENT_BUS,post,b-> {
                ((EventBus)NeoForge.EVENT_BUS).dispatch(post,c-> {
                    check(!AnchorDamagePostProof.matches(post),"same actual object direct nested repost refused");
                    check(HighPowerCombat.anchorFallPending(entity),"same object nested refusal preserves ordinary outer settlement guard");
                    return post;
                }
                ,new Object[] {
                    NeoForge.EVENT_BUS,post
                }
                );
                check(AnchorDamagePostProof.matches(post),"outer object accepted after nested same-object repost");
                return post;
            }
            ,entity,container);
            return null;
        }
        );
        pending();
        deep(80,()->AnchorDamagePostProof.nativePost(entity,container,a-> {
            busPost(NeoForge.EVENT_BUS,post,b-> {
                check(!HighPowerCombat.anchorFallPending(entity),"deep baseline abandons enhancement before dispatch");
                return post;
            }
            ,entity,container);
            return null;
        }
        ));
        pending();
        AnchorDamagePostProof.nativePost(entity,container,a-> {
            busPost(NeoForge.EVENT_BUS,post,b-> {
                deep(140, () -> ((EventBus)NeoForge.EVENT_BUS).dispatch(post, nested -> {
                    check(!AnchorDamagePostProof.matches(post), "deep same-object repost is refused");
                    check(!HighPowerCombat.anchorFallPending(entity), "truncated witness refuses enhancement");
                    check(HighPowerCombat.anchorPostGuardPending(entity), "truncation retains outer accounting identity gate");
                    return post;
                }, new Object[]{NeoForge.EVENT_BUS, post}));
                check(AnchorDamagePostProof.matches(post), "outer actual post still settles after deep same-object refusal");
                check(!AnchorDamagePostProof.matches(post), "outer ordinary settlement remains one-use");
                return post;
            }
            ,entity,container);
            return null;
        }
        );
        pending();
        RuntimeException marker=new RuntimeException("listener");
        try {
            AnchorDamagePostProof.nativePost(entity,container,a-> {
                busPost(NeoForge.EVENT_BUS,post,b-> {
                    throw marker;
                }
                ,entity,container);
                return null;
            }
            );
            throw new AssertionError("listener exception swallowed");
        }
        catch(RuntimeException thrown) {
            check(thrown==marker,"native subscriber exception preserved");
        }
        check(!AnchorDamagePostProof.matches(post),"receipt closed after exception");
        pending();
        AnchorDamagePostProof.nativePost(entity,container,a-> {
            busPost(NeoForge.EVENT_BUS,post,b-> {
                AnchorDamagePostProof.nativePost(entity,container,c-> {
                    check(!HighPowerCombat.anchorFallPending(entity),"native reentry abandons enhancement");
                    check(!AnchorDamagePostProof.matches(post),"outer proof is suspended during native reentry");
                    return null;
                }
                );
                check(HighPowerCombat.anchorPostGuardPending(entity),"native reentry preserves outer accounting guard");
                check(!AnchorDamagePostProof.matches(new Post(entity,container)),"synthetic object refused after native reentry defense veto");
                check(AnchorDamagePostProof.matches(post),"outer authentic accounting restored after native reentry");
                return post;
            }
            ,entity,container);
            return null;
        }
        );
        pending();
        NativeAnchorPostMixinPlugin.available=false;
        int before=calls;
        AnchorDamagePostProof.nativePost(entity,container,a-> {
            calls++;
            check(!HighPowerCombat.anchorFallPending(entity),"unavailable observer restores ordinary dispatch");
            return null;
        }
        );
        check(calls==before+1,"unavailable original invoked once");
        pending();
        AnchorDamagePostProof.nativePost(entity,container,a-> {
            busPost(new IEventBus() {
            }
            ,post,b-> {
                check(!HighPowerCombat.anchorFallPending(entity),"unapproved bus abandons enhancement before native dispatch");
                check(!AnchorDamagePostProof.matches(post),"unapproved bus cannot authenticate");
                return post;
            }
            ,entity,container);
            return null;
        }
        );
        pending();
        HighPowerCombat.fail=true;
        before=calls;
        AnchorDamagePostProof.nativePost(entity,container,a-> {
            calls++;
            check(!HighPowerCombat.pending.contains(entity),"preparation failure abandons enhancement before original");
            return null;
        }
        );
        HighPowerCombat.fail=false;
        check(calls==before+1&&!AnchorDamagePostProof.available(),"preparation failure disables only observer and invokes original once");
        System.out.println("PASS "+checks+" isolated identity, nested dispatch, replay, exception, and fallback checks; fixture collaborators, no live combat claim");
    }
}
