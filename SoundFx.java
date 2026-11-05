import javax.sound.sampled.*;
import java.util.EnumMap;
import java.util.Random;

/**
 * ROYAL GAMBIT - sound effects. Every sound is generated in code (no .wav files needed).
 *
 *   SoundFx.play(SoundFx.Type.MOVE);
 *   SoundFx.enabled = false;      // mute
 *   SoundFx.volume  = 0.5f;       // 0.0 - 1.0
 */
public final class SoundFx {
    private SoundFx() {}

    enum Type { MOVE, CAPTURE, CASTLE, CHECK, PROMOTE, WIN, LOSE, DRAW }

    static boolean enabled = true;
    static float volume = 0.7f;

    private static final float RATE = 44100f;
    private static final EnumMap<Type, Clip> clips = new EnumMap<>(Type.class);
    private static final Random RNG = new Random(7);
    private static boolean failed;

    /** Builds all clips once (safe to call many times; call it on a background thread at start-up). */
    static synchronized void preload() {
        if (!clips.isEmpty() || failed) return;
        try {
            AudioFormat fmt = new AudioFormat(RATE, 16, 1, true, false);
            for (Type t : Type.values()) {
                byte[] data = toPcm(synth(t));
                Clip c = AudioSystem.getClip();
                c.open(fmt, data, 0, data.length);
                clips.put(t, c);
            }
        } catch (Throwable e) {           // no sound device: the game keeps working silently
            failed = true;
            clips.clear();
        }
    }

    static synchronized void play(Type t) {
        if (!enabled || volume <= 0.001f) return;
        preload();
        Clip c = clips.get(t);
        if (c == null) return;
        try {
            if (c.isRunning()) c.stop();
            c.setFramePosition(0);
            if (c.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl g = (FloatControl) c.getControl(FloatControl.Type.MASTER_GAIN);
                float db = (float) (20.0 * Math.log10(volume));
                g.setValue(Math.max(g.getMinimum(), Math.min(g.getMaximum(), db)));
            }
            c.start();
        } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------------ synthesis

    private static double[] synth(Type t) {
        switch (t) {
            case MOVE:                                                  // soft wooden "tock"
                return tock(260, 0.14, 0.45, 38);
            case CAPTURE: {                                             // heavier thud + sharp click
                double[] a = tock(165, 0.24, 0.9, 20);
                mix(a, tock(430, 0.12, 0.5, 55), 0);
                return a;
            }
            case CASTLE: {                                              // two tocks: king, then rook
                double[] a = new double[(int) (RATE * 0.30)];
                mix(a, tock(250, 0.14, 0.45, 38), 0);
                mix(a, tock(215, 0.14, 0.45, 38), (int) (RATE * 0.12));
                return a;
            }
            case CHECK: {                                               // two-note alert
                double[] a = new double[(int) (RATE * 0.34)];
                mix(a, tone(880, 0.14, 14), 0);
                mix(a, tone(1175, 0.20, 12), (int) (RATE * 0.12));
                return a;
            }
            case PROMOTE: {                                             // rising sparkle
                double[] a = new double[(int) (RATE * 0.50)];
                double[] f = { 523, 659, 784, 1047 };
                for (int i = 0; i < f.length; i++) mix(a, tone(f[i], i == 3 ? 0.22 : 0.10, 10), (int) (RATE * 0.07 * i));
                return a;
            }
            case WIN: {                                                 // bright major chord
                double[] a = new double[(int) (RATE * 0.90)];
                double[] f = { 523, 659, 784, 1047 };
                for (int i = 0; i < f.length; i++) mix(a, tone(f[i], 0.7, 4), (int) (RATE * 0.09 * i));
                return a;
            }
            case LOSE: {                                                // falling notes
                double[] a = new double[(int) (RATE * 0.90)];
                double[] f = { 392, 330, 262 };
                for (int i = 0; i < f.length; i++) mix(a, tone(f[i], 0.45, 6), (int) (RATE * 0.22 * i));
                return a;
            }
            default: {                                                  // draw: two level notes
                double[] a = new double[(int) (RATE * 0.70)];
                mix(a, tone(440, 0.35, 6), 0);
                mix(a, tone(392, 0.40, 6), (int) (RATE * 0.25));
                return a;
            }
        }
    }

    /** Short percussive hit: a falling sine plus a burst of noise, both decaying fast. */
    private static double[] tock(double freq, double dur, double noise, double decay) {
        double[] out = new double[(int) (RATE * dur)];
        double phase = 0;
        for (int i = 0; i < out.length; i++) {
            double t = i / RATE;
            double f = freq * (1.0 - 1.4 * t);
            phase += 2 * Math.PI * f / RATE;
            double env = Math.exp(-t * decay);
            double click = (RNG.nextDouble() * 2 - 1) * Math.exp(-t * decay * 3.5) * noise;
            out[i] = Math.sin(phase) * env * 0.75 + click;
        }
        return out;
    }

    /** Soft musical note with a quick attack and exponential decay. */
    private static double[] tone(double freq, double dur, double decay) {
        double[] out = new double[(int) (RATE * dur)];
        for (int i = 0; i < out.length; i++) {
            double t = i / RATE;
            double attack = Math.min(1.0, t / 0.004);
            double env = attack * Math.exp(-t * decay);
            out[i] = (Math.sin(2 * Math.PI * freq * t) * 0.7 + Math.sin(4 * Math.PI * freq * t) * 0.18) * env * 0.6;
        }
        return out;
    }

    private static void mix(double[] dst, double[] src, int offset) {
        for (int i = 0; i < src.length && i + offset < dst.length; i++) dst[i + offset] += src[i];
    }

    private static byte[] toPcm(double[] s) {
        byte[] b = new byte[s.length * 2];
        for (int i = 0; i < s.length; i++) {
            double v = Math.tanh(s[i] * 1.2);                           // soft clip so layered sounds never crackle
            int x = (int) Math.round(v * 30000);
            b[2 * i] = (byte) (x & 0xFF);
            b[2 * i + 1] = (byte) ((x >> 8) & 0xFF);
        }
        return b;
    }
}