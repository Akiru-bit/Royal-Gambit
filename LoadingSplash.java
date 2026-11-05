import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Royal Gambit - minimal animated loading screen.
 *
 * Usage (e.g. in RoyalGambit.main):
 *   SwingUtilities.invokeLater(() ->
 *       LoadingSplash.show(() -> new RoyalGambit().setVisible(true)));
 *
 * Put your logo next to the .class files (or in src) as logo.png / logo.jpg / logo.jfif.
 */
public class LoadingSplash extends JWindow {

    // ---- palette (navy + gold, light minimal background) ----
    private static final Color BG    = new Color(246, 247, 245);
    private static final Color NAVY  = new Color(15, 42, 85);
    private static final Color GOLD  = new Color(244, 178, 39);
    private static final Color MUTED = new Color(120, 128, 142);
    private static final Color TRACK = new Color(225, 228, 232);

    private static final String TITLE = "ROYAL GAMBIT";
    private static final int MIN_MS = 3200;      // minimum time the splash stays up
    private static final int FADE_OUT_MS = 450;

    // ---- loading steps: replace the sleeps with your real loading work ----
    public static class Step {
        final String label; final Runnable work;
        public Step(String label, Runnable work) { this.label = label; this.work = work; }
    }

    private final List<Step> steps = new ArrayList<>();
    private final Runnable onDone;
    private final BufferedImage logo;

    private volatile int stepsDone = 0;
    private volatile String status = "Starting";
    private volatile boolean workFinished = false;

    private float shown = 0f;                    // eased progress 0..1
    private long start = System.nanoTime();
    private long fadeStart = -1;

    private LoadingSplash(Runnable onDone) {
        this.onDone = onDone;
        this.logo = loadLogo();

        steps.add(new Step("Loading assets",      () -> sleep(600)));
        steps.add(new Step("Preparing board",     () -> sleep(600)));
        steps.add(new Step("Loading pieces",      () -> sleep(500)));
        steps.add(new Step("Starting game engine",() -> sleep(600)));

        JPanel panel = new JPanel() {
            @Override protected void paintComponent(Graphics g0) { paintSplash((Graphics2D) g0, getWidth(), getHeight()); }
        };
        panel.setPreferredSize(new Dimension(560, 520));
        setContentPane(panel);
        pack();
        setLocationRelativeTo(null);
        setShape(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 28, 28));
    }

    public static void show(Runnable onDone) {
        LoadingSplash s = new LoadingSplash(onDone);
        s.setVisible(true);
        s.begin();
    }

    private void begin() {
        // background thread runs the real loading steps
        new Thread(() -> {
            for (Step st : steps) {
                status = st.label;
                try { st.work.run(); } catch (Exception ignored) {}
                stepsDone++;
            }
            workFinished = true;
        }, "splash-loader").start();

        // ~60 fps animation timer
        new Timer(16, e -> {
            float target = steps.isEmpty() ? 1f : stepsDone / (float) steps.size();
            shown += (target - shown) * 0.08f;

            long elapsed = (System.nanoTime() - start) / 1_000_000;
            if (fadeStart < 0 && workFinished && shown > 0.995f && elapsed >= MIN_MS) {
                fadeStart = System.nanoTime();
            }
            if (fadeStart >= 0 && (System.nanoTime() - fadeStart) / 1_000_000 >= FADE_OUT_MS) {
                ((Timer) e.getSource()).stop();
                dispose();
                if (onDone != null) {
                    try { onDone.run(); }
                    catch (Throwable ex) { ex.printStackTrace(); }
                }
                return;
            }
            repaint();
        }).start();
    }

    // ------------------------------------------------------------------ painting
    private void paintSplash(Graphics2D g, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        double t = (System.nanoTime() - start) / 1_000_000_000.0;

        g.setColor(BG);
        g.fillRect(0, 0, w, h);

        // --- logo: fade in + gentle scale-up, then a soft float ---
        float logoA = ease(clamp(t / 0.9));
        double scale = 0.88 + 0.12 * logoA;
        double floatY = Math.sin(t * 2.0) * 5 * logoA;
        int logoSize = 230;
        int cx = w / 2, logoCy = 190;

        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.SrcOver.derive(logoA));
        if (logo != null) {
            int lw = (int) (logoSize * scale);
            int lh = (int) (lw * logo.getHeight() / (double) logo.getWidth());
            g.drawImage(logo, cx - lw / 2, (int) (logoCy - lh / 2 + floatY), lw, lh, null);
        } else {
            g.setColor(NAVY);
            g.setFont(new Font("Serif", Font.BOLD, 120));
            drawCentered(g, "\u265E", cx, (int) (logoCy + 40 + floatY));
        }
        g.setComposite(old);

        // --- title: letters rise in one after another ---
        Font tf = new Font("Segoe UI", Font.BOLD, 28);
        g.setFont(tf);
        FontMetrics fm = g.getFontMetrics();
        int tracking = 7;
        int total = 0;
        for (char c : TITLE.toCharArray()) total += fm.charWidth(c) + tracking;
        total -= tracking;
        int x = cx - total / 2, baseY = 350;
        int i = 0;
        for (char c : TITLE.toCharArray()) {
            double local = clamp((t - 0.7 - i * 0.05) / 0.45);
            float a = ease(local);
            g.setComposite(AlphaComposite.SrcOver.derive(a));
            g.setColor(NAVY);
            g.drawString(String.valueOf(c), x, baseY + (int) ((1 - a) * 12));
            x += fm.charWidth(c) + tracking;
            i++;
        }
        g.setComposite(old);

        // --- gold underline that grows from the center ---
        double ul = ease(clamp((t - 1.4) / 0.6));
        int ulw = (int) (total * 0.35 * ul);
        g.setColor(GOLD);
        g.fill(new RoundRectangle2D.Float(cx - ulw / 2f, baseY + 16, ulw, 3, 3, 3));

        // --- progress bar ---
        float barA = ease(clamp((t - 1.2) / 0.5));
        g.setComposite(AlphaComposite.SrcOver.derive(barA));
        int bw = 300, bh = 5, bx = cx - bw / 2, by = 420;
        g.setColor(TRACK);
        g.fill(new RoundRectangle2D.Float(bx, by, bw, bh, bh, bh));
        int fill = (int) (bw * Math.min(1f, shown));
        if (fill > 0) {
            g.setColor(GOLD);
            g.fill(new RoundRectangle2D.Float(bx, by, Math.max(fill, bh), bh, bh, bh));
            // shimmer sweep
            Shape clip = g.getClip();
            g.setClip(new RoundRectangle2D.Float(bx, by, Math.max(fill, bh), bh, bh, bh));
            int sx = bx + (int) (((t * 0.9) % 1.4 - 0.2) * bw);
            g.setPaint(new GradientPaint(sx - 30, 0, new Color(255, 255, 255, 0),
                                         sx,      0, new Color(255, 255, 255, 120), true));
            g.fillRect(sx - 30, by, 60, bh);
            g.setClip(clip);
        }

        // --- status text with animated dots ---
        g.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        g.setColor(MUTED);
        int dots = (int) (t * 2.5) % 4;
        String msg = workFinished ? "Ready" : status + ".".repeat(dots);
        drawCentered(g, msg, cx, by + 34);
        g.setComposite(old);

        // --- fade-out overlay ---
        if (fadeStart >= 0) {
            float f = clamp((System.nanoTime() - fadeStart) / 1_000_000.0 / FADE_OUT_MS);
            g.setColor(new Color(BG.getRed(), BG.getGreen(), BG.getBlue(), (int) (255 * f)));
            g.fillRect(0, 0, w, h);
        }
    }

    // ------------------------------------------------------------------ helpers
    private static void drawCentered(Graphics2D g, String s, int cx, int y) {
        g.drawString(s, cx - g.getFontMetrics().stringWidth(s) / 2, y);
    }
    private static float clamp(double v) { return (float) Math.max(0, Math.min(1, v)); }
    private static float ease(double v)  { return (float) (1 - Math.pow(1 - v, 3)); } // ease-out cubic
    private static void sleep(long ms)   { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }

    private static BufferedImage loadLogo() {
        String[] names = {"logo.png", "logo.jpg", "logo.jpeg", "logo.jfif"};
        for (String n : names) {
            try (InputStream in = LoadingSplash.class.getResourceAsStream("/" + n)) {
                if (in != null) return ImageIO.read(in);
            } catch (Exception ignored) {}
            for (String dir : new String[]{"", "assets/", "resources/", "src/"}) {
                File f = new File(dir + n);
                if (f.exists()) {
                    try { return ImageIO.read(f); } catch (Exception ignored) {}
                }
            }
        }
        return null; // falls back to a drawn knight
    }

    // Running either class now does the same thing: splash -> Create Account screen
    public static void main(String[] args) {
        RoyalGambit.main(args);
    }
}