import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Sound effects for Royal Gambit. No audio files needed: the sounds are generated in code.
 * Keep this file in the same folder as Page.java.
 * Call Sfx.install() once (Page.open() does it) and every button click gets a sound.
 * The volume follows the "Sound effects volume" slider in Settings (0 = silent).
 *
 * Chess sounds: Sfx.move(), Sfx.capture(), Sfx.check(), Sfx.checkmate(),
 * or Sfx.chessMove(capture, check, mate) which picks the right ones for a move.
 */
public class Sfx {
    static final float RATE = 44100f;
    static boolean installed = false;

    static final java.util.Random RNG = new java.util.Random(7);

    // Pre-built waveforms (values -1..1)
    static final double[] CLICK  = tone(0.07, 1250, 1800, 55);   // short bright "tick"
    static final double[] LOCKED = tone(0.16, 190, 150, 18);     // low dull "nope"

    static final double[] MOVE    = mix(tone(0.09, 260, 120, 40), noise(0.04, 120, 0.35, 1.2));        // soft wooden "tock"
    static final double[] CAPTURE = mix(noise(0.12, 35, 0.5, 2.2), tone(0.18, 200, 70, 20),            // heavier "clack"
                                        at(tone(0.09, 300, 140, 40), 0.06));
    static final double[] CHECK   = mix(tone(0.14, 880, 880, 14), at(tone(0.22, 1175, 1175, 10), 0.12)); // two rising alert notes
    static final double[] MATE    = mix(noise(0.15, 25, 0.4, 2.0),                                     // thump, then a low minor chord
                                        at(tone(0.40, 392, 392, 6), 0.00),
                                        at(tone(0.40, 311, 311, 6), 0.22),
                                        at(tone(1.40, 147, 147, 2.2), 0.44),
                                        at(tone(1.40, 175, 175, 2.2), 0.44),
                                        at(tone(1.40, 220, 220, 2.2), 0.44));

    /** Sine sweep from f0 to f1 Hz with an exponential fade-out. */
    static double[] tone(double seconds, double f0, double f1, double decay) {
        int n = (int) (seconds * RATE);
        double[] out = new double[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) n;
            double f = f0 + (f1 - f0) * t;
            phase += 2 * Math.PI * f / RATE;
            double env = Math.exp(-decay * i / RATE) * Math.min(1.0, i / 60.0);   // quick attack, smooth decay
            out[i] = (Math.sin(phase) + 0.35 * Math.sin(2 * phase)) * env * 0.6;
        }
        return out;
    }

    /** Low-passed noise burst with an exponential fade-out (gain compensates for the filter). */
    static double[] noise(double seconds, double decay, double smooth, double gain) {
        int n = (int) (seconds * RATE);
        double[] out = new double[n];
        double lp = 0;
        for (int i = 0; i < n; i++) {
            lp += smooth * ((RNG.nextDouble() * 2 - 1) - lp);
            out[i] = lp * gain * Math.exp(-decay * i / RATE) * Math.min(1.0, i / 20.0);
        }
        return out;
    }

    /** Delays a waveform by the given number of seconds. */
    static double[] at(double[] w, double delay) {
        int d = (int) (delay * RATE);
        double[] out = new double[d + w.length];
        System.arraycopy(w, 0, out, d, w.length);
        return out;
    }

    /** Layers several waveforms on top of each other. */
    static double[] mix(double[]... waves) {
        int n = 0;
        for (double[] w : waves) n = Math.max(n, w.length);
        double[] out = new double[n];
        for (double[] w : waves) for (int i = 0; i < w.length; i++) out[i] += w[i] * 0.85;
        return out;
    }

    /** Current volume 0..1 from Settings. */
    static double volume() {
        return Math.max(0, Math.min(100, Page.getI("settings.volume", 70))) / 100.0;
    }

    static void play(double[] wave) {
        double vol = volume();
        if (vol <= 0.001) return;
        byte[] data = new byte[wave.length * 2];
        for (int i = 0; i < wave.length; i++) {
            short v = (short) Math.max(-32767, Math.min(32767, wave[i] * vol * 32767));
            data[2 * i] = (byte) (v & 0xFF);
            data[2 * i + 1] = (byte) ((v >> 8) & 0xFF);
        }
        try {
            AudioFormat fmt = new AudioFormat(RATE, 16, 1, true, false);
            Clip clip = AudioSystem.getClip();
            clip.addLineListener(ev -> { if (ev.getType() == LineEvent.Type.STOP) clip.close(); });
            clip.open(fmt, data, 0, data.length);
            clip.start();
        } catch (Exception | Error e) {
            // no audio device available: stay silent
        }
    }

    public static void click()     { play(CLICK); }
    public static void locked()    { play(LOCKED); }
    public static void move()      { play(MOVE); }
    public static void capture()   { play(CAPTURE); }
    public static void check()     { play(CHECK); }
    public static void checkmate() { play(MATE); }

    /** Plays the right sound for a chess move: move/capture first, then check or checkmate just after. */
    public static void chessMove(boolean capture, boolean check, boolean mate) {
        if (capture) capture(); else move();
        if (mate || check) {
            Timer t = new Timer(160, e -> { if (mate) checkmate(); else check(); });
            t.setRepeats(false);
            t.start();
        }
    }

    /** Plays a click for every button, menu item and clickable card in the app. */
    public static void install() {
        if (installed) return;
        installed = true;
        // warm up the audio system so the first click is not delayed
        new Thread(() -> {
            try { AudioSystem.getClip().close(); } catch (Exception | Error e) { }
        }, "sfx-warmup").start();

        Toolkit.getDefaultToolkit().addAWTEventListener(ev -> {
            if (!(ev instanceof MouseEvent)) return;
            MouseEvent me = (MouseEvent) ev;
            if (me.getID() != MouseEvent.MOUSE_PRESSED || !SwingUtilities.isLeftMouseButton(me)) return;
            Object src = me.getSource();

            if (src instanceof Page.StageCard) {
                if (Page.unlocked(((Page.StageCard) src).idx)) click(); else locked();
            } else if (src instanceof AbstractButton) {
                if (((AbstractButton) src).isEnabled()) click();
            } else if (src instanceof Page.BotCard || src instanceof Page.ModeCard
                    || src instanceof Page.AvatarPick || src instanceof Page.AccountButton
                    || src instanceof Page.GearButton) {
                click();
            }
        }, AWTEvent.MOUSE_EVENT_MASK);
    }
}