package cn.academy.port.highpower;

import java.util.function.ToIntFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Read-only presentation selection. Only a fresh HP/shield/CP panel may suppress its duplicate summary. */
public final class HighPowerStatusSummary {
    private HighPowerStatusSummary() {}
    public static Component select(Component input, boolean panelAvailable, int usableWidth, ToIntFunction<Component> width) {
        if (!panelAvailable || input == null || usableWidth <= 0 || !input.getSiblings().isEmpty()
                || !(input.getContents() instanceof TranslatableContents contents)
                || !contents.getKey().equals("hud.academy.highpower.summary.v1")) return input;
        Object[] args = contents.getArgs();
        if (args.length != 5 || !(args[0] instanceof Integer) || !(args[1] instanceof String)
                || !(args[2] instanceof String) || !(args[3] instanceof String) || !(args[4] instanceof String)) return input;
        // The client has already verified an actual panel and a fresh spendable-CP lease.
        // Render no duplicate pools; unrelated skill feedback retains its original component.
        return Component.empty().setStyle(input.getStyle());
    }
}
