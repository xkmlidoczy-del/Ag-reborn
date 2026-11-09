package cn.academy.port.client;

import java.util.function.IntPredicate;

/** Modern high-power adapter: a mouse press belonging to a menu must be released before casting. */
public final class MenuMouseInputFence {
    private static final int BUTTONS = 8;
    private int blocked;

    public void press(int button, boolean screenOpen) {
        if (screenOpen && valid(button)) blocked |= 1 << button;
    }

    public void release(int button) {
        if (valid(button)) blocked &= ~(1 << button);
    }

    /** Called at the actual screen-close callback, including closes between client ticks. */
    public void screenClosing(IntPredicate physicallyDown) {
        for (int button = 0; button < BUTTONS; button++) {
            if (physicallyDown.test(button)) blocked |= 1 << button;
        }
    }

    /** A raw release also clears the fence when a platform callback was not delivered. */
    public void poll(IntPredicate physicallyDown) {
        for (int button = 0; button < BUTTONS; button++) {
            if ((blocked & 1 << button) != 0 && !physicallyDown.test(button)) release(button);
        }
    }

    public boolean blocks(int sourceKey) {
        int button = sourceKey + 100;
        return valid(button) && (blocked & 1 << button) != 0;
    }

    public void clear() { blocked = 0; }
    private static boolean valid(int button) { return button >= 0 && button < BUTTONS; }
}
