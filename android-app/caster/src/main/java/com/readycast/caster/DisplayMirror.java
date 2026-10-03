package com.readycast.caster;

import android.graphics.Rect;
import android.hardware.display.VirtualDisplay;
import android.os.IBinder;
import android.view.Surface;

public final class DisplayMirror {

    public static final class Display {
        public final int id;
        public final int width;
        public final int height;
        public final int layerStack;

        Display(int id, int width, int height, int layerStack) {
            this.id = id;
            this.width = width;
            this.height = height;
            this.layerStack = layerStack;
        }
    }

    public static final class Session {
        private final Object handle;
        private final boolean surfaceControl;

        Session(Object handle, boolean surfaceControl) {
            this.handle = handle;
            this.surfaceControl = surfaceControl;
        }

        public void release() {
            try {
                if (surfaceControl) {
                    Class.forName("android.view.SurfaceControl")
                            .getMethod("destroyDisplay", IBinder.class)
                            .invoke(null, handle);
                } else {
                    ((VirtualDisplay) handle).release();
                }
            } catch (Exception e) {
                CastServer.log("release failed: " + e);
            }
        }
    }

    private DisplayMirror() {
    }

    public static Display info(int displayId) {
        try {
            Class<?> dmgClass = Class.forName("android.hardware.display.DisplayManagerGlobal");
            Object dmg = dmgClass.getDeclaredMethod("getInstance").invoke(null);
            Object di = dmg.getClass().getMethod("getDisplayInfo", int.class).invoke(dmg, displayId);
            if (di == null) {
                return null;
            }
            Class<?> c = di.getClass();
            return new Display(
                    displayId,
                    c.getDeclaredField("logicalWidth").getInt(di),
                    c.getDeclaredField("logicalHeight").getInt(di),
                    c.getDeclaredField("layerStack").getInt(di));
        } catch (Exception e) {
            CastServer.log("getDisplayInfo failed: " + e);
            return null;
        }
    }

    public static int[] size(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("max_width and max_height are required");
        }
        return new int[]{even(width), even(height)};
    }

    private static int even(int v) {
        return Math.max(2, v & ~1);
    }

    public static Session mirror(String name, int width, int height, Display display, Surface surface) throws Exception {
        try {
            Object vd = android.hardware.display.DisplayManager.class
                    .getMethod("createVirtualDisplay", String.class, int.class, int.class, int.class, Surface.class)
                    .invoke(null, name, width, height, display.id, surface);
            return new Session(vd, false);
        } catch (Exception e) {
            CastServer.log("DisplayManager mirror failed, trying SurfaceControl: " + e);
            return mirrorViaSurfaceControl(name, width, height, display, surface);
        }
    }

    private static Session mirrorViaSurfaceControl(String name, int width, int height, Display display, Surface surface)
            throws Exception {
        Class<?> sc = Class.forName("android.view.SurfaceControl");
        IBinder token = (IBinder) sc.getMethod("createDisplay", String.class, boolean.class).invoke(null, name, false);
        try {
            sc.getMethod("openTransaction").invoke(null);
            try {
                sc.getMethod("setDisplaySurface", IBinder.class, Surface.class).invoke(null, token, surface);
                sc.getMethod("setDisplayProjection", IBinder.class, int.class, Rect.class, Rect.class)
                        .invoke(null, token, 0,
                                new Rect(0, 0, display.width, display.height),
                                new Rect(0, 0, width, height));
                sc.getMethod("setDisplayLayerStack", IBinder.class, int.class).invoke(null, token, display.layerStack);
            } finally {
                sc.getMethod("closeTransaction").invoke(null);
            }
        } catch (Exception e) {
            sc.getMethod("destroyDisplay", IBinder.class).invoke(null, token);
            throw e;
        }
        return new Session(token, true);
    }
}
