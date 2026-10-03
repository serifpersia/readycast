package com.readycast.caster;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.view.Surface;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Lean secondary-display capture. Mirrors one display, encodes H.264 from the
 * encoder Surface, writes raw Annex-B to stdout. Logs go to stderr.
 *
 * <p>Args as key=value: display_id, max_size, video_bit_rate, max_fps.
 */
public final class CastServer {

    private CastServer() {
    }

    public static void main(String[] args) throws Exception {
        int displayId = opt(args, "display_id=", 1);
        int maxSize = opt(args, "max_size=", 1920);
        int bitRate = opt(args, "video_bit_rate=", 6000000);
        float maxFps = Float.parseFloat(str(args, "max_fps=", "60"));

        DisplayMirror.Display display = DisplayMirror.info(displayId);
        if (display == null) {
            log("display " + displayId + " not found, exiting");
            return;
        }
        int[] size = DisplayMirror.fit(display.width, display.height, maxSize);
        log("capturing display " + displayId + " (" + display.width + "x" + display.height
                + ") at " + size[0] + "x" + size[1] + " " + bitRate / 1000000 + "Mbps");

        MediaCodec codec = MediaCodec.createEncoderByType("video/avc");
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, "video/avc");
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 60);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        format.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100_000);
        if (Build.VERSION.SDK_INT >= 23) {
            format.setInteger(MediaFormat.KEY_PRIORITY, 0);
        }
        if (Build.VERSION.SDK_INT >= 26) {
            format.setInteger(MediaFormat.KEY_LATENCY, 1);
        }
        format.setFloat("max-fps-to-encoder", maxFps);
        format.setInteger(MediaFormat.KEY_WIDTH, size[0]);
        format.setInteger(MediaFormat.KEY_HEIGHT, size[1]);
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        Surface surface = codec.createInputSurface();
        DisplayMirror.Session session = DisplayMirror.mirror("readycast", size[0], size[1], display, surface);
        codec.start();
        log("streaming");

        drain(codec);

        try {
            session.release();
        } catch (Exception e) {
            log("release failed: " + e);
        }
        try {
            codec.stop();
        } catch (Exception e) {
            // ignore
        }
        codec.release();
        log("stopped");
    }

    /** Copies encoder output to stdout until the pipe breaks (app killed us). */
    private static void drain(MediaCodec codec) {
        OutputStream out = new FileOutputStream(FileDescriptor.out);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        byte[] chunk = new byte[64 * 1024];
        try {
            for (;;) {
                int id = codec.dequeueOutputBuffer(info, -1);
                if (id < 0) {
                    continue;
                }
                try {
                    ByteBuffer buf = codec.getOutputBuffer(id);
                    while (buf != null && buf.hasRemaining()) {
                        int n = Math.min(buf.remaining(), chunk.length);
                        buf.get(chunk, 0, n);
                        out.write(chunk, 0, n);
                    }
                } finally {
                    codec.releaseOutputBuffer(id, false);
                }
            }
        } catch (Exception e) {
            log("drain ended: " + e);
        }
    }

    private static int opt(String[] args, String key, int def) {
        try {
            return Integer.parseInt(str(args, key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String str(String[] args, String key, String def) {
        for (String arg : args) {
            if (arg.startsWith(key)) {
                return arg.substring(key.length());
            }
        }
        return def;
    }

    static void log(String s) {
        System.err.println("readycast: " + s);
    }
}
