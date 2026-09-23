package com.github.tacowasa059.multiscreenxray.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.github.tacowasa059.multiscreenxray.config.XrayProfile;
import net.minecraft.world.entity.Entity;

/** Marks a vanilla render pass that should contain entities, hand, and HUD only. */
public final class XrayOverlayPass implements AutoCloseable {
    private static final ThreadLocal<State> STATE = new ThreadLocal<>();
    private final State previous;

    private XrayOverlayPass(RenderTarget target, XrayProfile profile) {
        previous = STATE.get();
        STATE.set(new State(target, new ProfileMatcher(profile), profile.showHand));
    }

    public static XrayOverlayPass enter(RenderTarget target, XrayProfile profile) {
        return new XrayOverlayPass(target, profile);
    }

    public static boolean active() {
        return STATE.get() != null;
    }

    public static RenderTarget target() {
        State state = STATE.get();
        return state == null ? null : state.target();
    }

    public static boolean visible(Entity entity) {
        State state = STATE.get();
        return state == null || state.matcher().matchesEntity(entity);
    }

    public static boolean showHand() {
        State state = STATE.get();
        return state == null || state.showHand();
    }

    public static float aspectRatio() {
        State state = STATE.get();
        if (state == null || state.target().height <= 0) return 1.0f;
        return (float) state.target().width / state.target().height;
    }

    public static void captureWorldFov(double fieldOfView) {
        State state = STATE.get();
        if (state != null && !Double.isFinite(state.worldFov)) {
            state.worldFov = fieldOfView;
        }
    }

    public double worldFov() {
        State state = STATE.get();
        return state == null ? Double.NaN : state.worldFov;
    }

    @Override
    public void close() {
        if (previous == null) {
            STATE.remove();
        } else {
            STATE.set(previous);
        }
    }

    private static final class State {
        private final RenderTarget target;
        private final ProfileMatcher matcher;
        private final boolean showHand;
        private double worldFov = Double.NaN;

        private State(RenderTarget target, ProfileMatcher matcher, boolean showHand) {
            this.target = target;
            this.matcher = matcher;
            this.showHand = showHand;
        }

        private RenderTarget target() { return target; }
        private ProfileMatcher matcher() { return matcher; }
        private boolean showHand() { return showHand; }
    }
}
