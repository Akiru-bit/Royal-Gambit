import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Cinematic end-of-game animation for ROYAL GAMBIT.
 * Shown as the dialog's glass pane on top of the board.
 *
 *   VICTORY - golden rays, a bouncing crown, a confetti burst and drifting sparks
 *   DEFEAT  - screen shake, a crown that slams down and cracks, flying shards, falling embers
 *   DRAW    - a swaying set of scales and slowly rising bubbles
 *
 * Needs Page.java and RoyalGambit.java (for Page.GOLD, Page.GOLD_LIGHT, RoyalGambit.SANS, RoyalGambit.aa).
 * Press Esc or "VIEW BOARD" to close it and look at the final position.
 */
public class GameEndOverlay extends JComponent {

    enum Kind { VICTORY, DEFEAT, DRAW }

    private static final int BAND = 300;                       // height of the banner across the screen
    private static final Color[] CONFETTI = {
        new Color(0xFFD54F), new Color(0xFF7043), new Color(0x4FC3F7),
        new Color(0x81C784), new Color(0xBA68C8), new Color(0xFFF4D6) };

    /** One particle: confetti, spark, shard, ember or bubble. */
    private static class P {
        float x, y, vx, vy, g, rot, vr, size, age, life, flip;
        int shape;                                              // 0 rect, 1 soft circle, 2 shard (triangle)
        Color color;
        boolean fade = true;
    }

    private final Kind kind;
    private final String subtitle;
    private final Runnable onAgain;                             // null = no "play again" button
    private final List<P> ps = new ArrayList<>();
    private final Random rnd = new Random();
    private final Timer timer;
    private long start, lastTick;
    private float t;
    private boolean burst;
    private int hover = -1;

    // =====================================================================
    //  Public API
    // =====================================================================
    static void show(JDialog d, Kind kind, String subtitle, Runnable playAgain) {
        hide(d);
        GameEndOverlay o = new GameEndOverlay(kind, subtitle, playAgain);
        d.setGlassPane(o);
        o.setVisible(true);
        o.start();
        o.requestFocusInWindow();
    }

    /** Removes the overlay (if any) from this dialog. Safe to call at any time. */
    static void hide(JDialog d) {
        if (d != null && d.getGlassPane() instanceof GameEndOverlay) ((GameEndOverlay) d.getGlassPane()).dismiss();
    }

    // =====================================================================
    private GameEndOverlay(Kind kind, String subtitle, Runnable onAgain) {
        this.kind = kind;
        this.subtitle = subtitle == null ? "" : subtitle;
        this.onAgain = onAgain;
        setOpaque(false);
        setFocusable(true);
        timer = new Timer(16, e -> tick());
        timer.setCoalesce(true);

        MouseAdapter ma = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int h = buttonAt(e.getPoint());
                if (h != hover) { hover = h; setCursor(Cursor.getPredefinedCursor(h >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR)); repaint(); }
            }
            @Override public void mouseClicked(MouseEvent e) {
                if (t < 1.3f) return;                           // buttons are not live until they have faded in
                int b = buttonAt(e.getPoint());
                if (b < 0) return;
                boolean again = onAgain != null && b == 0;
                dismiss();
                if (again) onAgain.run();
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        getActionMap().put("close", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { dismiss(); }
        });
    }

    private void start() {
        start = lastTick = System.nanoTime();
        t = 0;
        timer.start();
    }

    private void dismiss() {
        timer.stop();
        ps.clear();
        setVisible(false);
    }

    // =====================================================================
    //  Simulation
    // =====================================================================
    private void tick() {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastTick) / 1e9f);
        lastTick = now;
        t = (now - start) / 1e9f;
        spawn(dt);
        for (Iterator<P> it = ps.iterator(); it.hasNext(); ) {
            P p = it.next();
            p.vy += p.g * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.rot += p.vr * dt;
            p.age += dt;
            if (p.age > p.life || p.y > getHeight() + 40 || p.y < -80) it.remove();
        }
        repaint();
    }

    private float rf(float a, float b) { return a + rnd.nextFloat() * (b - a); }

    private P make(float x, float y, float vx, float vy, float g, float size, float life, int shape, Color c) {
        P p = new P();
        p.x = x; p.y = y; p.vx = vx; p.vy = vy; p.g = g; p.size = size; p.life = life; p.shape = shape; p.color = c;
        p.rot = rf(0, 6.28f); p.vr = rf(-8, 8); p.flip = rf(4, 10);
        ps.add(p);
        return p;
    }

    private void spawn(float dt) {
        int w = getWidth(), h = getHeight(), cx = w / 2, cy = h / 2;
        if (w == 0) return;
        switch (kind) {
            case VICTORY:
                if (!burst && t > 0.3f) {
                    burst = true;
                    for (int i = 0; i < 170; i++)
                        make(rf(0, w), rf(-220, -10), rf(-70, 70), rf(90, 280), 130, rf(6, 12), 7, 0,
                                CONFETTI[rnd.nextInt(CONFETTI.length)]);
                }
                if (t > 0.5f && rnd.nextFloat() < dt * 45) {        // sparks twinkling around the crown
                    double a = rnd.nextDouble() * Math.PI * 2;
                    float r = rf(40, 150);
                    P p = make(cx + (float) Math.cos(a) * r, cy - 70 + (float) Math.sin(a) * r * 0.7f,
                            rf(-10, 10), rf(-25, -5), 0, rf(3, 6), rf(0.7f, 1.4f), 1, new Color(0xFFE082));
                    p.vr = 0;
                }
                if (t > 1.0f && rnd.nextFloat() < dt * 14)           // a slow trickle of confetti afterwards
                    make(rf(0, w), -12, rf(-30, 30), rf(60, 140), 40, rf(6, 11), 9, 0,
                            CONFETTI[rnd.nextInt(CONFETTI.length)]);
                break;
            case DEFEAT:
                if (!burst && t > 0.4f) {
                    burst = true;
                    for (int i = 0; i < 60; i++) {
                        double a = rnd.nextDouble() * Math.PI * 2, sp = rf(120, 520);
                        make(cx, cy - 55, (float) Math.cos(a) * (float) sp, (float) Math.sin(a) * (float) sp - 150, 900,
                                rf(5, 13), 3.5f, 2, rnd.nextBoolean() ? new Color(0x8E2A22) : new Color(0x5A5A66));
                    }
                }
                if (rnd.nextFloat() < dt * 40)                        // ash and embers drifting down
                    make(rf(0, w), -10, rf(-15, 15), rf(35, 90), 6, rf(2, 4.5f), 9, 1,
                            rnd.nextInt(4) == 0 ? new Color(0xFF7043) : new Color(0x9A9AA5));
                break;
            default:
                if (rnd.nextFloat() < dt * 22) {                      // soft bubbles rising
                    P p = make(rf(0, w), h + 20, rf(-12, 12), rf(-80, -35), 0, rf(4, 11), 9, 1, new Color(0xA9C7E6));
                    p.vr = 0;
                }
        }
    }

    // =====================================================================
    //  Helpers
    // =====================================================================
    private static float clamp(float v) { return v < 0 ? 0 : v > 1 ? 1 : v; }
    private static float easeOutCubic(float x) { x = 1 - x; return 1 - x * x * x; }
    private static float easeOutBack(float x) {
        float c1 = 1.70158f, c3 = c1 + 1, m = x - 1;
        return 1 + c3 * m * m * m + c1 * m * m;
    }

    private Color accent() {
        return kind == Kind.VICTORY ? Page.GOLD_LIGHT : kind == Kind.DEFEAT ? new Color(0xE05050) : new Color(0x8FB4D9);
    }

    private String title() { return kind == Kind.VICTORY ? "VICTORY" : kind == Kind.DEFEAT ? "DEFEAT" : "DRAW"; }

    private Rectangle[] buttons() {
        int n = onAgain != null ? 2 : 1, bw = 160, bh = 42, gap = 16;
        int total = n * bw + (n - 1) * gap, x = (getWidth() - total) / 2, y = getHeight() / 2 + 84;
        Rectangle[] r = new Rectangle[n];
        for (int i = 0; i < n; i++) r[i] = new Rectangle(x + i * (bw + gap), y, bw, bh);
        return r;
    }

    private int buttonAt(Point p) {
        Rectangle[] r = buttons();
        for (int i = 0; i < r.length; i++) if (r[i].contains(p)) return i;
        return -1;
    }

    private static Color a(Color c, int al) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, al))); }

    // =====================================================================
    //  Painting
    // =====================================================================
    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        RoyalGambit.aa(g);
        int w = getWidth(), h = getHeight(), cx = w / 2, cy = h / 2;
        Color acc = accent();

        // dim the board
        float dim = clamp(t / 0.5f);
        g.setColor(new Color(0, 0, 0, (int) (dim * (kind == Kind.DEFEAT ? 190 : 160))));
        g.fillRect(0, 0, w, h);
        if (kind == Kind.DEFEAT) {                                    // red vignette
            float r = Math.max(w, h) * 0.75f;
            g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), r, new float[] { 0.45f, 1f },
                    new Color[] { new Color(0, 0, 0, 0), new Color(120, 10, 10, (int) (dim * 150)) }));
            g.fillRect(0, 0, w, h);
        }

        // screen shake on defeat when the crown lands
        if (kind == Kind.DEFEAT && t > 0.4f && t < 0.9f) {
            float k = 1 - (t - 0.4f) / 0.5f;
            g.translate(Math.sin(t * 90) * 9 * k, Math.cos(t * 75) * 7 * k);
        }

        // banner (grows from the centre line)
        float bs = easeOutCubic(clamp((t - 0.1f) / 0.45f));
        if (bs > 0.01f) {
            Graphics2D b = (Graphics2D) g.create();
            b.translate(0, cy);
            b.scale(1, bs);
            Color fill = kind == Kind.VICTORY ? new Color(0x1B1538) : kind == Kind.DEFEAT ? new Color(0x2A0D10) : new Color(0x14223A);
            float[] fr = { 0f, 0.16f, 0.84f, 1f };
            b.setPaint(new LinearGradientPaint(0, 0, w, 0, fr,
                    new Color[] { a(fill, 0), a(fill, 235), a(fill, 235), a(fill, 0) }));
            b.fillRect(0, -BAND / 2, w, BAND);
            b.setPaint(new LinearGradientPaint(0, 0, w, 0, fr, new Color[] { a(acc, 0), acc, acc, a(acc, 0) }));
            b.setStroke(new BasicStroke(2.5f));
            b.drawLine(0, -BAND / 2, w, -BAND / 2);
            b.drawLine(0, BAND / 2, w, BAND / 2);

            if (kind == Kind.VICTORY) {                               // rotating golden rays behind the crown
                b.clipRect(0, -BAND / 2, w, BAND);
                float R = Math.max(w, h);
                b.setPaint(new RadialGradientPaint(new Point2D.Float(cx, -70), R * 0.6f, new float[] { 0f, 1f },
                        new Color[] { new Color(255, 215, 90, 120), new Color(255, 215, 90, 0) }));
                int n = 16;
                for (int i = 0; i < n; i++) {
                    double a1 = t * 0.35 + i * (Math.PI * 2 / n), a2 = a1 + Math.PI / n * 0.9;
                    Path2D ray = new Path2D.Double();
                    ray.moveTo(cx, -70);
                    ray.lineTo(cx + Math.cos(a1) * R, -70 + Math.sin(a1) * R);
                    ray.lineTo(cx + Math.cos(a2) * R, -70 + Math.sin(a2) * R);
                    ray.closePath();
                    b.fill(ray);
                }
            }
            b.dispose();
        }

        // particles
        for (P p : ps) {
            float alpha = p.fade ? clamp(Math.min(1f, (p.life - p.age) / 1.2f)) : 1f;
            if (p.shape == 1 && kind == Kind.VICTORY) alpha = (float) Math.sin(Math.PI * clamp(p.age / p.life));   // twinkle
            if (kind == Kind.DRAW) alpha *= 0.55f;
            Graphics2D pg = (Graphics2D) g.create();
            pg.translate(p.x, p.y);
            pg.rotate(p.rot);
            pg.setColor(a(p.color, (int) (alpha * 255)));
            if (p.shape == 0) {                                       // confetti: flips like paper
                float sy = (float) Math.cos(p.age * p.flip);
                pg.scale(1, Math.abs(sy) < 0.12f ? 0.12f : sy);
                pg.fill(new Rectangle2D.Float(-p.size / 2, -p.size / 4, p.size, p.size / 2));
            } else if (p.shape == 1) {
                pg.fill(new Ellipse2D.Float(-p.size / 2, -p.size / 2, p.size, p.size));
                if (kind == Kind.DRAW) {
                    pg.setColor(a(Color.WHITE, (int) (alpha * 160)));
                    pg.setStroke(new BasicStroke(1f));
                    pg.draw(new Ellipse2D.Float(-p.size / 2, -p.size / 2, p.size, p.size));
                }
            } else {
                Path2D tri = new Path2D.Float();
                tri.moveTo(-p.size, p.size / 2);
                tri.lineTo(p.size, 0);
                tri.lineTo(-p.size / 3, -p.size);
                tri.closePath();
                pg.fill(tri);
            }
            pg.dispose();
        }

        // icon
        float ie = clamp((t - 0.2f) / 0.6f);
        if (ie > 0) {
            Graphics2D ig = (Graphics2D) g.create();
            float s = easeOutBack(ie);
            if (kind == Kind.DEFEAT) {                                // slams down from above
                ig.translate(cx, cy - 72 - (1 - easeOutCubic(clamp((t - 0.2f) / 0.25f))) * 260);
                ig.scale(1, 1);
            } else {
                ig.translate(cx, cy - 72);
                ig.scale(s, s);
            }
            ig.setComposite(AlphaComposite.SrcOver.derive(clamp(ie * 3)));
            drawIcon(ig, 50);
            ig.dispose();
        }

        // title (pops in with a little overshoot)
        float te = clamp((t - 0.45f) / 0.55f);
        if (te > 0) {
            Graphics2D tg = (Graphics2D) g.create();
            tg.setComposite(AlphaComposite.SrcOver.derive(clamp(te * 2.5f)));
            int size = 66;
            Font f = new Font(RoyalGambit.SANS, Font.BOLD, size);
            String txt = title();
            tg.setFont(f);
            FontMetrics fm = tg.getFontMetrics();
            float s = easeOutBack(te);
            tg.translate(cx, cy + 30);
            tg.scale(s, s);
            int tw = fm.stringWidth(txt);
            tg.setColor(new Color(0, 0, 0, 190));
            tg.drawString(txt, -tw / 2 + 3, 3);
            tg.setPaint(new GradientPaint(0, -48, Color.WHITE, 0, 0, acc));
            tg.drawString(txt, -tw / 2, 0);
            tg.dispose();
        }

        // subtitle
        float se = clamp((t - 0.9f) / 0.4f);
        if (se > 0 && !subtitle.isEmpty()) {
            Graphics2D sg = (Graphics2D) g.create();
            sg.setComposite(AlphaComposite.SrcOver.derive(se));
            int size = 20;
            Font f = new Font(RoyalGambit.SANS, Font.BOLD, size);
            FontMetrics fm = sg.getFontMetrics(f);
            while (size > 12 && fm.stringWidth(subtitle) > w - 80) { f = new Font(RoyalGambit.SANS, Font.BOLD, --size); fm = sg.getFontMetrics(f); }
            sg.setFont(f);
            GameBoard.shadowText(sg, subtitle, (w - fm.stringWidth(subtitle)) / 2, cy + 64 - (int) ((1 - se) * -8), GameBoard.TEXT);
            sg.dispose();
        }

        // buttons
        float be = clamp((t - 1.3f) / 0.4f);
        if (be > 0) {
            Graphics2D bg = (Graphics2D) g.create();
            bg.setComposite(AlphaComposite.SrcOver.derive(be));
            Rectangle[] r = buttons();
            String[] labels = onAgain != null ? new String[] { "PLAY AGAIN", "VIEW BOARD" } : new String[] { "VIEW BOARD" };
            bg.setFont(new Font(RoyalGambit.SANS, Font.BOLD, 15));
            FontMetrics fm = bg.getFontMetrics();
            for (int i = 0; i < r.length; i++) {
                Color base = i == hover ? GameBoard.BTN_BLUE.brighter() : GameBoard.BTN_BLUE;
                Shape s = new RoundRectangle2D.Float(r[i].x, r[i].y, r[i].width, r[i].height, 14, 14);
                bg.setPaint(new GradientPaint(0, r[i].y, base.brighter(), 0, r[i].y + r[i].height, base.darker()));
                bg.fill(s);
                bg.setColor(i == hover ? Page.GOLD_LIGHT : Page.GOLD);
                bg.setStroke(new BasicStroke(2f));
                bg.draw(s);
                GameBoard.shadowText(bg, labels[i], r[i].x + (r[i].width - fm.stringWidth(labels[i])) / 2,
                        r[i].y + (r[i].height - fm.getHeight()) / 2 + fm.getAscent(), Color.WHITE);
            }
            bg.dispose();
        }
        g.dispose();
    }

    // =====================================================================
    //  Icons (drawn with Java2D, centred on 0,0; r = radius)
    // =====================================================================
    private void drawIcon(Graphics2D g, float r) {
        if (kind == Kind.DRAW) { drawScales(g, r); return; }
        boolean lost = kind == Kind.DEFEAT;
        g.scale(r, r);
        if (lost) g.rotate(Math.toRadians(16));

        Path2D crown = new Path2D.Float();
        crown.moveTo(-0.85, 0.55);
        crown.lineTo(-0.95, -0.55);
        crown.lineTo(-0.45, -0.1);
        crown.lineTo(0, -0.8);
        crown.lineTo(0.45, -0.1);
        crown.lineTo(0.95, -0.55);
        crown.lineTo(0.85, 0.55);
        crown.closePath();

        Color top = lost ? new Color(0x8A8A94) : new Color(0xFFE082);
        Color bot = lost ? new Color(0x4A3C3C) : new Color(0xC98A12);
        g.setPaint(new GradientPaint(0, -0.8f, top, 0, 0.55f, bot));
        g.fill(crown);
        g.setColor(lost ? new Color(0x2A1A1A) : new Color(0x7A4E00));
        g.setStroke(new BasicStroke(0.06f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(crown);

        Color[] jewel = lost ? new Color[] { new Color(0x555560), new Color(0x555560), new Color(0x555560) }
                             : new Color[] { new Color(0xE53935), new Color(0x42A5F5), new Color(0xE53935) };
        double[] jx = { -0.5, 0, 0.5 };
        for (int i = 0; i < 3; i++) {
            g.setColor(jewel[i]);
            g.fill(new Ellipse2D.Double(jx[i] - 0.11, 0.2, 0.22, 0.22));
        }
        g.setColor(lost ? new Color(0x777782) : new Color(0xFFF4D6));
        for (double x : new double[] { -0.95, 0, 0.95 })             // tips
            g.fill(new Ellipse2D.Double(x - 0.09, (x == 0 ? -0.8 : -0.55) - 0.09, 0.18, 0.18));

        if (lost) {                                                   // crack through the crown
            g.setColor(new Color(0x120A0A));
            g.setStroke(new BasicStroke(0.07f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
            Path2D crack = new Path2D.Float();
            crack.moveTo(0.05, -0.75);
            crack.lineTo(-0.1, -0.35);
            crack.lineTo(0.12, -0.05);
            crack.lineTo(-0.08, 0.25);
            crack.lineTo(0.06, 0.55);
            g.draw(crack);
        }
    }

    private void drawScales(Graphics2D g, float r) {
        g.scale(r, r);
        Color gold = new Color(0xB9D3EE);
        g.setColor(gold);
        g.setStroke(new BasicStroke(0.09f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(0, -0.8, 0, 0.75));                  // post
        g.draw(new Line2D.Double(-0.45, 0.78, 0.45, 0.78));           // base
        g.fill(new Ellipse2D.Double(-0.1, -0.92, 0.2, 0.2));

        double sway = Math.sin(t * 2.2) * 0.07 * Math.min(1, t / 1.2);   // beam rocks gently and settles level
        AffineTransform keep = g.getTransform();
        g.translate(0, -0.55);
        g.rotate(sway);
        g.draw(new Line2D.Double(-0.95, 0, 0.95, 0));                 // beam
        for (int side = -1; side <= 1; side += 2) {
            double x = side * 0.95;
            AffineTransform at = g.getTransform();
            g.translate(x, 0);
            g.rotate(-sway);                                          // pans stay upright
            g.setStroke(new BasicStroke(0.04f));
            g.draw(new Line2D.Double(0, 0, -0.32, 0.55));
            g.draw(new Line2D.Double(0, 0, 0.32, 0.55));
            g.setStroke(new BasicStroke(0.09f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Arc2D.Double(-0.38, 0.25, 0.76, 0.6, 180, 180, Arc2D.OPEN));
            g.setTransform(at);
        }
        g.setTransform(keep);
    }
}