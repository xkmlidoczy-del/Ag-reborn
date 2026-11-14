package cn.acamech.qa;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Streams real framebuffer samples into a constant frame rate video on the audio clock. */
final class ShowcaseCapture {
    private static final int FPS = 30;
    private final Process encoder;
    private final OutputStream frames;
    private final byte[] rgb;
    private final Path wave;
    private final long zero;
    private final Map<String, Number> audioFirst;
    private int count, fresh, held;

    ShowcaseCapture(Minecraft mc) throws IOException {
        int w = mc.getMainRenderTarget().width, h = mc.getMainRenderTarget().height;
        var dir = mc.gameDirectory.toPath().toAbsolutePath();
        wave = dir.resolve("game.wav");
        rgb = new byte[w * h * 3];
        encoder = new ProcessBuilder(System.getProperty("acamech.qa.ffmpeg"), "-hide_banner", "-loglevel", "warning",
                "-y", "-f", "rawvideo", "-pixel_format", "rgb24", "-video_size", w + "x" + h,
                "-framerate", Integer.toString(FPS), "-i", "pipe:0", "-an", "-c:v", "libx264", "-preset", "veryfast",
                "-crf", "17", "-pix_fmt", "yuv420p", "-threads", "4", "-movflags", "+faststart", dir.resolve("native.mp4").toString())
                .redirectError(dir.resolve("ffmpeg.log").toFile()).start();
        frames = new BufferedOutputStream(encoder.getOutputStream(), rgb.length);
        zero = System.nanoTime();
        audioFirst = audioAnchor();
    }

    int currentFrame() { return (int) ((System.nanoTime() - zero) * FPS / 1_000_000_000L); }

    void frame(Minecraft mc) throws IOException {
        int target = currentFrame();
        if (count > target) return;
        while (count != 0 && count < target) {
            frames.write(rgb); count++; held++;
        }
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int[] pixels = image.getPixelsRGBA();
            if (pixels.length * 3 != rgb.length) throw new IOException("Capture window resized during recording");
            for (int i = 0, j = 0; i < pixels.length; i++) {
                int abgr = pixels[i];
                rgb[j++] = (byte) abgr;
                rgb[j++] = (byte) (abgr >>> 8);
                rgb[j++] = (byte) (abgr >>> 16);
            }
        }
        frames.write(rgb); count++; fresh++;
    }

    Map<String, Object> finish() throws Exception {
        var audioLast = audioAnchor();
        frames.close();
        if (!encoder.waitFor(24, TimeUnit.SECONDS) || encoder.exitValue() != 0) {
            abort();
            throw new IOException("Video encode failed; see ffmpeg.log");
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("fps", FPS); result.put("frames", count); result.put("fresh_samples", fresh);
        result.put("held_frames", held); result.put("audio_first", audioFirst); result.put("audio_last", audioLast);
        return result;
    }

    void abort() { encoder.destroyForcibly(); }

    private Map<String, Number> audioAnchor() {
        try (var in = Files.newInputStream(wave)) {
            byte[] header = in.readNBytes(68);
            if (header.length != 68 || !new String(header, 60, 4, StandardCharsets.US_ASCII).equals("data")) return null;
            var fmt = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            long sample = (Files.size(wave) - 68) / fmt.getShort(32);
            return Map.of("seconds", (System.nanoTime() - zero) / 1e9, "sample", sample, "sample_rate", fmt.getInt(24));
        } catch (IOException e) {
            return null;
        }
    }
}
