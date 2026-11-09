# NEW412 native precision seam

Reviewed target: Minecraft 1.21.1, NeoForge 21.1.252. Native sources and executable
classes are the corresponding `build/moddev/artifacts/neoforge-21.1.252-*` artifacts.
`NativeVectorParryMixinPlugin` pins symbolic executable fingerprints; a mismatch
makes NEW412 precision unavailable without admitting an alternate movement path.

The reviewed call chain contains:

1. Exactly one `LivingEntity.hurt(DamageSource,float):boolean` invocation of
   `LivingEntity.knockback(double,double,double):void` (native bytecode offset 826)
2. Exactly one `LivingEntity.knockback` invocation of
   `CommonHooks.onLivingKnockBack(LivingEntity,float,double,double):LivingKnockBackEvent`
3. Exactly one native `LivingEntity.setDeltaMovement(double,double,double)` call
   in that knockback method

Native source order is committed damage, the base knockback call, death/totem
handling, then hurt return. The cooldown differential branch skips this base
knockback. CommonHooks constructs one event, posts it once, and returns the same
event even when canceled. Knockback tests cancellation before reading its strength
and ratios. It then applies knockback resistance and, only for positive effective
strength, native damping and the ground-dependent vertical term.

The dedicated mixin calls each original operation with unchanged arguments.
The bridge creates a scoped receipt only for a controller-admitted, positively
absorbed, precise-window hit. Its event wrapper claims the receipt **before**
calling CommonHooks. Nested event knockback cannot claim it and invalidates the pending outer turn. Any reentrant hurt,
including a rejected hurt that produces no knockback, invalidates the pending
outer receipt. The receipt is removed/restored in `finally` before controller
completion callbacks run, including native event-listener exceptions.

After all event listeners return, cancellation, changed strength/ratios, changed
position, or controller revalidation failure preserve the returned event exactly.
An admitted turn marks its controller receipt consumed before changing only the
event's ratio X and Z. There is no strength edit, global event subscriber,
projectile reflection, extra knockback invocation, velocity write, or HP write in
this adapter. Direct or unrelated knockback remains native.

Completion receives velocity captured after event listeners and immediately
before native motion, and the observed velocity after the original call returns.
The controller must prove the native positive horizontal impulse actually ran and
matches its captured resistance/ground formula before emitting precision success.
A ratio edit alone is not success: cancellation, full resistance, incomplete calls,
or unchanged/mismatched velocity cannot manufacture precision feedback.

The final transformation witness requires the merged availability interface and
both actual call-site wrapper invocations. MixinExtras 0.5.3 installs its wrappers
after the ordinary plugin `postApply` callback. The plugin therefore registers a
read-only extension during config selection and verifies the final class tree in
`IExtension.export`, after all extension `postApply` callbacks have completed.
This callback does not write a file and does not depend on debug export being on.
Merged handlers alone leave precision unavailable. `VectorParryNativeBridge.available(entity)` exposes
this precision-only availability to the controller. Missing or failed optional
admission does not replace or cancel ordinary damage/knockback.

`VectorParryNativeSeamRegressionTest` checks raw native fingerprints, mutated and
missing methods/sites, compiled exact injection annotations, installed witness
mutation controls, one-use dispatch and nested-hurt invalidation, bridge ordering,
forbidden movement/strength writes, and cleanup structure. Its constructed ASM
fixture tests the witness predicate only; it is not a live Mixin transformation.
Pass an exported transformed LivingEntity class as its optional first argument to
check the real installed structure. Server GameTests separately exercise native
hurt/event/motion behavior and actual controller eligibility.
