import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.TextLayout;
import java.awt.geom.*;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;

/**
 * ROYAL GAMBIT - main menu (Java Swing, no extra libraries, no board).
 * Keep this file in the same folder as RoyalGambit.java (it reuses its colors and fonts)
 * and Sfx.java (button click sound effects).
 * Run in VS Code:  javac RoyalGambit.java Sfx.java Page.java && java Page
 *
 * Menu:
 *   PLAYER VS BOT     -> choose a bot (name + avatar) and a difficulty (Easy / Medium / Hard / Extreme)
 *   PLAYER VS PLAYER  -> Local Player (same device) or Global Player (online matchmaking)
 *   CHESS PUZZLE      -> 10 stage challenges, unlocked one by one, stars saved on this computer
 *
 * Top-right navigation:
 *   MENU (drop-down, opens below on the right) -> Profile, Stats, Achievements, Settings
 */
public class Page extends JFrame {

    static final Color BG_DARK    = RoyalGambit.BG_DARK;
    static final Color BG_LIGHT   = RoyalGambit.BG_LIGHT;
    static final Color GOLD       = RoyalGambit.GOLD;
    static final Color GOLD_LIGHT = RoyalGambit.GOLD_LIGHT;
    static final Color CREAM      = RoyalGambit.CREAM;
    // Brighter than RoyalGambit.MUTED so secondary text stays readable on the dark picture background
    static final Color MUTED      = new Color(0xE6E0CC);

    // Dark text colours used on the light-blue dialogs (Player vs Bot, Player vs Player, Chess Puzzle)
    static final Color INK        = new Color(0x0B2545);   // main text
    static final Color INK_SOFT   = new Color(0x1F3F66);   // secondary text
    static final Color BLUE_EDGE  = new Color(0x1B4F8F);   // borders

    // =====================================================================
    //  Data: difficulties, bots, puzzle stages
    // =====================================================================
    static final String[] DIFFICULTIES = { "Easy", "Medium", "Hard", "Extreme" };
    static final String[] DIFF_DESC = {
        "Relaxed play. The bot makes plenty of mistakes.",
        "A fair fight. The bot plays solid, sensible moves.",
        "Tough opponent. The bot rarely misses tactics.",
        "No mercy. The bot calculates deeply and punishes errors."
    };
    static String level = DIFFICULTIES[0];   // chosen bot difficulty

    static class Bot {
        final String name, tagline;
        final Color bg, skin, hair, outfit;
        final int style;   // 0 short hair, 1 long hair, 2 helmet, 3 crown, 4 glasses
        Bot(String name, String tagline, int bg, int skin, int hair, int outfit, int style) {
            this.name = name; this.tagline = tagline;
            this.bg = new Color(bg); this.skin = new Color(skin);
            this.hair = new Color(hair); this.outfit = new Color(outfit);
            this.style = style;
        }
    }

    static final Bot[] BOTS = {
        new Bot("Pawny",               "Fresh recruit, still learning",  0x3E7C4F, 0xF2C9A0, 0x5A3A1E, 0x2B5A38, 0),
        new Bot("Sir Galahad",         "Charges in with the knights",    0x2F5D8C, 0xD9A066, 0x2B2B2B, 0x8A94A6, 2),
        new Bot("Lady Bishop",         "Strikes from the diagonals",     0x7A3F8C, 0xFFDFC4, 0x7B3F1D, 0x4F2A5C, 1),
        new Bot("Rook Rolland",        "Solid as a castle wall",         0x8C5A2F, 0x8D5A3B, 0x1A1A1A, 0x5B3A1E, 0),
        new Bot("Queen Vera",          "Fierce and relentless",          0xA03040, 0xC68642, 0x111111, 0x6E1F2B, 3),
        new Bot("King Aurelio",        "Calm, patient, strategic",       0xB8892B, 0xF2C9A0, 0xC9C9C9, 0x6B4E16, 3),
        new Bot("Professor Zugzwang",  "Endgame specialist",             0x3A6B6B, 0xFFDFC4, 0xD8D8D8, 0x24474A, 4),
        new Bot("Blitz Betty",         "Lightning-fast moves",           0xC9692B, 0xD9A066, 0xE0A030, 0x8A3F10, 1),
        new Bot("Grandmaster Orion",   "Calculates ten moves ahead",     0x2B2F4F, 0x8D5A3B, 0x0A0A0A, 0x14172E, 4),
        new Bot("Shadow Knight",       "Plays in silence",               0x3A3A3A, 0xC68642, 0x000000, 0x1A1A1A, 2),
    };
    static Bot bot = BOTS[0];                // chosen opponent

    static class Stage {
        final String title, goal, hint;
        Stage(String title, String goal, String hint) { this.title = title; this.goal = goal; this.hint = hint; }
    }

    static final Stage[] STAGES = {
        new Stage("The First Strike",     "Checkmate in 1 move.",                       "Look for a queen move that leaves the king no escape."),
        new Stage("Back-Rank Blunder",    "Checkmate the king on the back rank.",       "The king is trapped behind its own pawns."),
        new Stage("Knight's Fork",        "Win a queen with a knight fork.",            "One knight jump can attack two pieces at once."),
        new Stage("Pinned Down",          "Win material by exploiting a pin.",          "A pinned piece cannot move without losing something bigger."),
        new Stage("Skewer",               "Win a rook with a skewer.",                  "Attack the valuable piece first so it has to move away."),
        new Stage("Double Trouble",       "Checkmate in 2 moves.",                      "Start with a forcing check or a quiet killer move."),
        new Stage("Deflection",           "Lure the defender away, then checkmate.", "Whatever guards the key square can be chased off."),
        new Stage("Sacrifice for Glory",  "Sacrifice a piece to force checkmate.",      "Give up material now to open the king's defences."),
        new Stage("Endgame Master",       "Promote a pawn and win the endgame.",        "Use your king to escort the pawn."),
        new Stage("Royal Finale",         "Checkmate in 3 moves.",                      "Calculate every check and every reply."),
    };

    // Puzzle progress (0-3 stars per stage), saved on this computer
    static final Preferences PREFS = Preferences.userNodeForPackage(Page.class);

    // ---------- Guest mode: nothing is written to disk; data lives in memory until the app closes ----------
    static boolean guest = false;
    static final java.util.Map<String, String> GUEST_DATA = new java.util.HashMap<>();

    static int getI(String k, int d) {
        if (!guest) return PREFS.getInt(k, d);
        String v = GUEST_DATA.get(k);
        try { return v == null ? d : Integer.parseInt(v); } catch (NumberFormatException e) { return d; }
    }
    static void putI(String k, int v) { if (guest) GUEST_DATA.put(k, String.valueOf(v)); else PREFS.putInt(k, v); }
    static String getS(String k, String d) { return guest ? GUEST_DATA.getOrDefault(k, d) : PREFS.get(k, d); }
    static void putS(String k, String v) { if (guest) GUEST_DATA.put(k, v); else PREFS.put(k, v); }

    /** Reloads puzzle stars: empty for a guest, the saved ones for a registered account. */
    static void loadStars() {
        for (int i = 0; i < stars.length; i++) stars[i] = guest ? 0 : PREFS.getInt("puzzle.stars." + i, 0);
    }
    static final int[] stars = new int[STAGES.length];
    static {
        for (int i = 0; i < stars.length; i++) stars[i] = getI("puzzle.stars." + i, 0);
    }
    static boolean unlocked(int i) { return i == 0 || stars[i - 1] > 0; }
    static int completedCount() { int n = 0; for (int s : stars) if (s > 0) n++; return n; }
    static void completePuzzle(int i, int s) {
        stars[i] = Math.max(stars[i], s);
        putI("puzzle.stars." + i, stars[i]);
    }
    static void resetPuzzles() {
        for (int i = 0; i < stars.length; i++) { stars[i] = 0; putI("puzzle.stars." + i, 0); }
    }

    // =====================================================================
    //  Menu data: avatar, stats, achievements, settings (saved on this computer)
    // =====================================================================
    // Fixed court colours (the theme chooser was removed)
    static final Color THEME_DARK  = BG_DARK;
    static final Color THEME_LIGHT = BG_LIGHT;

    // Player nickname: editable in Profile, saved on this computer, shown on the top-right button
    static final int MAX_NAME = 16;
    // Saved per login username, so every account on this computer keeps its own name
    static String nameKey(String login) {
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256").digest(
                    (login == null ? "" : login.trim()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("profile.name.");
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", h[i]));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return "profile.name";
        }
    }
    static String nickname(String login, String fallback) {
        String saved = getS(nameKey(login), "").trim();
        return saved.isEmpty() ? fallback : saved;
    }

    static int avatarIndex() { return Math.max(0, Math.min(BOTS.length - 1, getI("profile.avatar", 0))); }

    static final String[] LANGUAGES = { "English", "Filipino", "Spanish", "French", "German", "Japanese" };

    static int stat(String k) { return getI("stats." + k, 0); }
    static void bump(String k) { putI("stats." + k, stat(k) + 1); }
    static int totalStars() { int n = 0; for (int s : stars) n += s; return n; }

    /**
     * Call this from your game screens when a match ends so Stats and Achievements update.
     * mode: "bot", "local" or "global"      result: "win", "loss" or "draw"
     */
    static void recordGame(String mode, String result) {
        bump("games");
        bump(result);
        bump(mode);
    }

    static String rankTitle() {
        int w = stat("win");
        if (w < 3) return "Pawn";
        if (w < 10) return "Knight";
        if (w < 25) return "Bishop";
        if (w < 50) return "Rook";
        if (w < 100) return "Queen";
        return "King";
    }

    static final String[] ACH_NAME = {
        "First Game", "Winner", "Veteran", "Social Player",
        "First Strike", "Puzzle Apprentice", "Puzzle Master", "Star Collector", "Perfectionist"
    };
    static final String[] ACH_DESC = {
        "Finish your first game.",
        "Win a game.",
        "Win 10 games.",
        "Play 5 games against other players.",
        "Solve the first chess puzzle.",
        "Complete 5 puzzle stages.",
        "Complete all 10 puzzle stages.",
        "Collect 15 puzzle stars.",
        "Earn 3 stars on every puzzle stage."
    };
    static boolean[] achievements() {
        return new boolean[] {
            stat("games") >= 1,
            stat("win") >= 1,
            stat("win") >= 10,
            stat("local") + stat("global") >= 5,
            stars[0] > 0,
            completedCount() >= 5,
            completedCount() >= STAGES.length,
            totalStars() >= 15,
            totalStars() >= STAGES.length * 3
        };
    }

    // =====================================================================
    //  Hooks - plug your real game screens in here
    // =====================================================================
    /** Called when the player presses START in the bot picker. */
    static void startBotGame(Component parent, String player, Bot b, String difficulty) {
        GameBoard.playBot(parent, player, b, difficulty);
    }
    /** Local match: two people on the same device. */
    static void startLocalGame(Component parent, String white, String black) {
        GameBoard.playLocal(parent, white, black);
    }
    /** Global match: replace the simulated search in searchGlobal() with your real matchmaking. */
    static void startGlobalGame(Component parent, String player, String opponent) {
        GameBoard.playGlobal(parent, player, opponent);
    }
    /** Called when a puzzle stage starts: opens the playable PuzzleBoard. */
    static void onStartPuzzle(Component parent, int stage) {
        PuzzleBoard.open(parent, stage);   // playable board; calls completePuzzle(stage, stars) when solved
    }
    static void onSettings(Component parent) {
        showSettings(parent);
    }
    static void info(Component parent, String text) {
        JOptionPane.showMessageDialog(parent, text, "Royal Gambit", JOptionPane.INFORMATION_MESSAGE);
    }

    // =====================================================================
    //  Small UI helpers
    // =====================================================================
    static JLabel lbl(String t, int size, int style, Color c, String family) {
        JLabel l = new JLabel(t);
        l.setFont(new Font(family, style, size));
        l.setForeground(c);
        return l;
    }

    // =====================================================================
    //  Custom title bar (replaces the native one so we decide which buttons exist)
    //    main menu window -> Close only
    //    sub windows      -> Minimize, Maximize / Restore, Close
    // =====================================================================
    static Color goldA(int alpha) { return new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), alpha); }

    static javax.swing.border.Border windowBorder() { return BorderFactory.createLineBorder(goldA(170), 1); }

    /** Puts a custom title bar at the top of a JFrame / JDialog (it sits in the root pane's menu-bar slot). */
    static void installTitleBar(JRootPane rp, Window w, String title, boolean canMin, boolean canMax) {
        JMenuBar holder = new JMenuBar();
        holder.setLayout(new BorderLayout());
        holder.setBorder(null);
        holder.setOpaque(false);
        holder.add(new TitleBar(w, title, canMin, canMax), BorderLayout.CENTER);
        rp.setJMenuBar(holder);
        rp.setBorder(windowBorder());
    }

    static class TitleBar extends JPanel {
        private final Window win;
        private final boolean canMax;
        private final CtlButton maxBtn;
        private Rectangle restoreBounds;
        private boolean maxed = false;
        private Dimension baseSize;      // window size before maximizing; the content is stretched relative to it
        private Point grab;

        TitleBar(Window win, String title, boolean canMin, boolean canMax) {
            super(new BorderLayout());
            this.win = win;
            this.canMax = canMax;
            setOpaque(true);
            setBackground(THEME_DARK);
            setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, goldA(120)));
            setPreferredSize(new Dimension(10, 36));

            JLabel text = lbl(title, 13, Font.PLAIN, CREAM, RoyalGambit.SANS);
            text.setBorder(new EmptyBorder(0, 14, 0, 0));
            add(text, BorderLayout.CENTER);

            JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            btns.setOpaque(false);
            if (canMin) btns.add(new CtlButton(0, "Minimize", this::minimize));
            maxBtn = canMax ? new CtlButton(1, "Maximize", this::toggleMaximize) : null;
            if (maxBtn != null) btns.add(maxBtn);
            btns.add(new CtlButton(2, "Close", this::close));
            add(btns, BorderLayout.EAST);

            // drag the window by its title bar; double-click toggles maximize
            MouseAdapter drag = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e) || maxed) { grab = null; return; }
                    Point p = e.getLocationOnScreen();
                    grab = new Point(p.x - win.getX(), p.y - win.getY());
                }
                @Override public void mouseDragged(MouseEvent e) {
                    if (grab == null || maxed) return;
                    Point p = e.getLocationOnScreen();
                    win.setLocation(p.x - grab.x, p.y - grab.y);
                }
                @Override public void mouseReleased(MouseEvent e) { grab = null; }
                @Override public void mouseClicked(MouseEvent e) {
                    if (TitleBar.this.canMax && SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2)
                        toggleMaximize();
                }
            };
            addMouseListener(drag);
            addMouseMotionListener(drag);
            text.addMouseListener(drag);
            text.addMouseMotionListener(drag);
        }

        void close() { win.dispatchEvent(new WindowEvent(win, WindowEvent.WINDOW_CLOSING)); }

        /** A modal dialog cannot be iconified itself, so minimizing it minimizes the main window
         *  (its owned dialogs are hidden with it and come back when the window is restored). */
        void minimize() {
            Window w = win;
            while (w != null && !(w instanceof Frame)) w = w.getOwner();
            if (w instanceof Frame) ((Frame) w).setExtendedState(Frame.ICONIFIED);
        }

        /** Stretches the window's content (sizes + fonts) to fit the maximized window, or restores it. */
        private void rescaleContent() {
            if (!(win instanceof RootPaneContainer) || baseSize == null) return;
            float f = 1f;
            if (maxed) {
                Dimension cur = win.getSize();
                f = Math.min(cur.width / (float) baseSize.width, cur.height / (float) baseSize.height);
                f = Math.max(1f, Math.min(f, 2.2f));
            }
            scaleTree(((RootPaneContainer) win).getContentPane(), f);
        }

        void toggleMaximize() {
            if (!canMax) return;
            if (!maxed) {
                restoreBounds = win.getBounds();
                baseSize = win.getSize();
                GraphicsConfiguration gc = win.getGraphicsConfiguration();
                Rectangle sb = gc != null ? gc.getBounds()
                        : GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
                Insets in = gc != null ? Toolkit.getDefaultToolkit().getScreenInsets(gc) : new Insets(0, 0, 0, 0);
                win.setBounds(sb.x + in.left, sb.y + in.top,
                        sb.width - in.left - in.right, sb.height - in.top - in.bottom);
            } else if (restoreBounds != null) {
                win.setBounds(restoreBounds);
            }
            maxed = !maxed;
            rescaleContent();                       // stretch (or shrink back) everything inside the window
            maxBtn.restore = maxed;
            maxBtn.setToolTipText(maxed ? "Restore" : "Maximize");
            maxBtn.repaint();
            win.validate();
            win.repaint();
        }
    }

    /** Scales fixed sizes and fonts of a component tree by f (f = 1 puts the originals back). */
    static void scaleTree(Component c, float f) {
        if (c instanceof JComponent) {
            JComponent jc = (JComponent) c;
            Object[] o = (Object[]) jc.getClientProperty("rg.orig");
            if (o == null) {
                o = new Object[] {
                    jc.isPreferredSizeSet() ? jc.getPreferredSize() : null,
                    jc.isMinimumSizeSet()   ? jc.getMinimumSize()   : null,
                    jc.isMaximumSizeSet()   ? jc.getMaximumSize()   : null,
                    jc.isFontSet()          ? jc.getFont()          : null };
                jc.putClientProperty("rg.orig", o);
            }
            jc.setPreferredSize(scaledSize((Dimension) o[0], f));
            jc.setMinimumSize(scaledSize((Dimension) o[1], f));
            jc.setMaximumSize(scaledSize((Dimension) o[2], f));
            Font fo = (Font) o[3];
            if (fo != null) jc.setFont(f == 1f ? fo : fo.deriveFont(fo.getSize2D() * f));
            jc.invalidate();
        }
        if (c instanceof Container)
            for (Component k : ((Container) c).getComponents()) scaleTree(k, f);
    }

    static Dimension scaledSize(Dimension d, float f) {
        if (d == null || f == 1f) return d;
        return new Dimension(d.width >= 20000 ? d.width : Math.round(d.width * f),
                             d.height >= 20000 ? d.height : Math.round(d.height * f));
    }

    /** One title-bar button: kind 0 = minimize, 1 = maximize / restore, 2 = close. */
    static class CtlButton extends JComponent {
        final int kind;
        boolean hover, restore;

        CtlButton(int kind, String tip, Runnable r) {
            this.kind = kind;
            Dimension d = new Dimension(46, 35);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
            setToolTipText(tip);
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
                @Override public void mouseReleased(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e) && contains(e.getPoint())) r.run();
                }
            });
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            if (hover) {
                g.setColor(kind == 2 ? new Color(0xE81123) : new Color(255, 255, 255, 38));
                g.fillRect(0, 0, w, h);
            }
            g.setColor(kind == 2 && hover ? Color.WHITE : CREAM);
            g.setStroke(new BasicStroke(1.3f));
            int cx = w / 2, cy = h / 2;
            switch (kind) {
                case 0:
                    g.drawLine(cx - 5, cy, cx + 5, cy);
                    break;
                case 1:
                    if (!restore) {
                        g.drawRect(cx - 5, cy - 5, 10, 10);
                    } else {
                        g.drawRect(cx - 5, cy - 2, 7, 7);
                        g.drawLine(cx - 2, cy - 5, cx + 5, cy - 5);
                        g.drawLine(cx + 5, cy - 5, cx + 5, cy + 2);
                        g.drawLine(cx - 2, cy - 5, cx - 2, cy - 2);
                    }
                    break;
                default:
                    g.drawLine(cx - 5, cy - 5, cx + 5, cy + 5);
                    g.drawLine(cx - 5, cy + 5, cx + 5, cy - 5);
            }
            g.dispose();
        }
    }

    static JDialog dialog(Component parent, String title, int w, int h) {
        return dialog(parent, title, w, h, false);
    }

    /** lightBlue = true gives the dialog a light-blue background instead of the picture. */
    static JDialog dialog(Component parent, String title, int w, int h, boolean lightBlue) {
        Window owner = parent instanceof Window ? (Window) parent : SwingUtilities.getWindowAncestor(parent);
        JDialog d = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        d.setUndecorated(true);                                   // custom title bar below replaces the native one
        installTitleBar(d.getRootPane(), d, title, true, true);   // Minimize, Maximize, Close
        Bg p = new Bg();
        p.lightBlue = lightBlue;
        p.scrim = 150;
        p.setBorder(new EmptyBorder(22, 26, 20, 26));
        d.setContentPane(p);
        d.setSize(w, h);
        d.setLocationRelativeTo(parent);
        return d;
    }

    /** Dialog for the top-right Menu pages (Profile, Stats, Achievements, Settings): Close button only. */
    static JDialog menuDialog(Component parent, String title, int w, int h) {
        JDialog d = dialog(parent, title, w, h, false);
        installTitleBar(d.getRootPane(), d, title, false, false);   // no Minimize, no Maximize
        return d;
    }

    static JPanel header(String title, String sub) { return header(title, sub, false); }

    /** ink = true uses dark text for the light-blue dialogs. */
    static JPanel header(String title, String sub, boolean ink) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(0, 0, 14, 0));
        JLabel t = ink ? lbl(title, 26, Font.BOLD, INK, RoyalGambit.SERIF)
                       : lbl(title, 26, Font.PLAIN, GOLD, RoyalGambit.SERIF);
        JLabel s = ink ? lbl(sub, 15, Font.BOLD, INK_SOFT, RoyalGambit.SANS)
                       : lbl(sub, 16, Font.PLAIN, MUTED, RoyalGambit.SANS);
        t.setAlignmentX(Component.CENTER_ALIGNMENT);
        s.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(t);
        p.add(Box.createVerticalStrut(4));
        p.add(s);
        return p;
    }

    static RoyalGambit.GoldButton gold(String text, int w, int h, Runnable r) {
        RoyalGambit.GoldButton b = new RoyalGambit.GoldButton(text);
        b.setBorder(new EmptyBorder(0, 0, 0, 0));
        b.setFont(new Font(RoyalGambit.SANS, Font.BOLD, 15));
        RoyalGambit.fix(b, w, h);
        b.addActionListener(e -> r.run());
        return b;
    }

    static OutlineButton outline(String text, int w, int h, int fontSize, Runnable r) {
        OutlineButton b = new OutlineButton(text);
        b.setBorder(new EmptyBorder(0, 0, 0, 0));
        b.setFont(new Font(RoyalGambit.SANS, Font.BOLD, fontSize));
        RoyalGambit.fix(b, w, h);
        b.addActionListener(e -> r.run());
        return b;
    }

    /** Outline button with dark text, for the light-blue dialogs. */
    static OutlineButton outlineLight(String text, int w, int h, int fontSize, Runnable r) {
        OutlineButton b = outline(text, w, h, fontSize, r);
        b.light = true;
        return b;
    }

    static String[] wrap(String s, FontMetrics fm, int maxW) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        String cur = "";
        for (String w : s.split(" ")) {
            String t = cur.isEmpty() ? w : cur + " " + w;
            if (fm.stringWidth(t) > maxW && !cur.isEmpty()) { out.add(cur); cur = w; } else cur = t;
        }
        if (!cur.isEmpty()) out.add(cur);
        return out.toArray(new String[0]);
    }

    static void centered(Graphics2D g, String s, int cx, int y) {
        g.drawString(s, cx - g.getFontMetrics().stringWidth(s) / 2, y);
    }

    static Shape starShape(double cx, double cy, double R) {
        Path2D.Double s = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double r = i % 2 == 0 ? R : R * 0.45, a = -Math.PI / 2 + i * Math.PI / 5;
            double x = cx + r * Math.cos(a), y = cy + r * Math.sin(a);
            if (i == 0) s.moveTo(x, y); else s.lineTo(x, y);
        }
        s.closePath();
        return s;
    }

    // =====================================================================
    //  Background: soft radial glow, no board
    // =====================================================================
    // Background picture: put "background1.png" in the same folder as the .java files (or on the classpath).
    // If it cannot be loaded, the old dark gradient is used instead.
    static final java.awt.image.BufferedImage BG_IMG = loadBackground();

    static java.awt.image.BufferedImage loadBackground() {
        String[] names = { "background1.png", "background (2).png", "background.png" };
        try {
            for (String n : names) {
                java.io.File f = new java.io.File(n);
                if (f.exists()) return javax.imageio.ImageIO.read(f);
                java.net.URL u = Page.class.getResource("/" + n);
                if (u != null) return javax.imageio.ImageIO.read(u);
            }
        } catch (Exception e) { /* fall back to gradient */ }
        return null;
    }

    static class Bg extends JPanel {
        private java.awt.image.BufferedImage cache;
        private int cw, ch;
        boolean lightBlue;   // true = soft light-blue gradient instead of the background picture
        int scrim = 0;       // 0-255: dark overlay on top of the picture so light text stays readable
        Bg() { setLayout(new BorderLayout()); }

        /** Whole image stretched to fill the panel, so the full frame is always visible (no bars, no crop).
         *  Very tall/narrow panels (small dialogs) would look badly squashed, so those use "cover" instead. */
        private java.awt.image.BufferedImage scaled(int w, int h) {
            if (cache != null && cw == w && ch == h) return cache;
            double imgAspect = (double) BG_IMG.getWidth() / BG_IMG.getHeight();
            double aspect = (double) w / h;
            boolean stretch = aspect > imgAspect * 0.75 && aspect < imgAspect * 1.25;
            int iw = w, ih = h;
            if (!stretch) {
                double k = Math.max((double) w / BG_IMG.getWidth(), (double) h / BG_IMG.getHeight());
                iw = Math.max(1, (int) Math.round(BG_IMG.getWidth() * k));
                ih = Math.max(1, (int) Math.round(BG_IMG.getHeight() * k));
            }
            java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
            g.drawImage(BG_IMG, (w - iw) / 2, (h - ih) / 2, iw, ih, null);
            g.dispose();
            cache = out; cw = w; ch = h;
            return out;
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            RoyalGambit.aa(g);
            int w = getWidth(), h = getHeight();
            if (lightBlue) {
                g.setPaint(new GradientPaint(0, 0, new Color(0xCFE8FB), 0, h, new Color(0x9CCBF0)));
                g.fillRect(0, 0, w, h);
            } else if (BG_IMG != null && w > 0 && h > 0) {
                g.drawImage(scaled(w, h), 0, 0, null);
            } else {
                g.setColor(THEME_DARK);
                g.fillRect(0, 0, w, h);
                float r = Math.max(w, h) * 0.65f;
                g.setPaint(new RadialGradientPaint(new Point2D.Float(w / 2f, h * 0.42f), r,
                        new float[] { 0f, 1f }, new Color[] { THEME_LIGHT, THEME_DARK }));
                g.fillRect(0, 0, w, h);
            }
            if (!lightBlue && scrim > 0) {
                g.setColor(new Color(0, 0, 0, scrim));
                g.fillRect(0, 0, w, h);
            }
            g.dispose();
        }
    }

    // =====================================================================
    //  Outlined gold button (secondary actions, can be "selected")
    // =====================================================================
    static class OutlineButton extends JButton {
        boolean hover, selected, filled, light;   // filled = solid yellow inside, light = dark text for light backgrounds
        OutlineButton(String t) {
            super(t);
            setContentAreaFilled(false); setFocusPainted(false); setBorderPainted(false); setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            RoyalGambit.aa(g);
            int w = getWidth(), h = getHeight() - getInsets().bottom;
            int r = Math.max(6, h / 6);
            if (!isEnabled()) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
            Shape s = new RoundRectangle2D.Float(1, 1, w - 2, h - 2, r, r);
            if (light) {
                g.setColor(new Color(255, 255, 255, selected ? 230 : hover || getModel().isPressed() ? 170 : 110));
                g.fill(s);
            } else if (filled) {
                g.setPaint(new GradientPaint(0, 0, hover || getModel().isPressed() ? GOLD_LIGHT : GOLD.brighter(),
                        0, h, hover || getModel().isPressed() ? GOLD : GOLD.darker()));
                g.fill(s);
            } else if (selected) {
                g.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 80));
                g.fill(s);
            } else if (hover || getModel().isPressed()) {
                g.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), getModel().isPressed() ? 50 : 30));
                g.fill(s);
            }
            g.setColor(light ? (selected ? INK : BLUE_EDGE) : hover || selected ? GOLD_LIGHT : GOLD);
            g.setStroke(new BasicStroke(light && selected ? 2.4f : 1.6f));
            g.draw(s);
            g.setFont(getFont());
            if (filled) g.setColor(BG_DARK);              // dark text on the yellow fill
            else if (light) g.setColor(INK);              // dark text on the light-blue dialogs
            FontMetrics fm = g.getFontMetrics();
            g.drawString(getText(), (w - fm.stringWidth(getText())) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
        }
    }

    // =====================================================================
    //  Settings gear (vector)
    // =====================================================================
    static class GearButton extends JComponent {
        boolean hover;
        GearButton(Runnable action) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Settings");
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { action.run(); }
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            RoyalGambit.aa(g);
            double cx = getWidth() / 2.0, cy = getHeight() / 2.0, r = Math.min(cx, cy) - 2;
            Area gear = new Area(new Ellipse2D.Double(cx - r * 0.68, cy - r * 0.68, r * 1.36, r * 1.36));
            Rectangle2D tooth = new Rectangle2D.Double(cx - r * 0.2, cy - r, r * 0.4, r * 0.5);
            for (int i = 0; i < 8; i++) {
                AffineTransform t = AffineTransform.getRotateInstance(i * Math.PI / 4, cx, cy);
                gear.add(new Area(t.createTransformedShape(tooth)));
            }
            gear.subtract(new Area(new Ellipse2D.Double(cx - r * 0.3, cy - r * 0.3, r * 0.6, r * 0.6)));
            g.setColor(hover ? GOLD_LIGHT : GOLD);
            g.fill(gear);
            g.dispose();
        }
    }

    // =====================================================================
    //  PLAYER VS BOT: avatars, bot cards, picker dialog
    // =====================================================================
    /** Draws a round vector avatar for a bot at (x, y) with diameter d. */
    static void drawAvatar(Graphics2D g0, Bot b, int x, int y, int d) {
        Graphics2D g = (Graphics2D) g0.create();
        RoyalGambit.aa(g);
        g.translate(x, y);
        Shape circle = new Ellipse2D.Double(0, 0, d, d);
        g.setPaint(new GradientPaint(0, 0, b.bg.brighter(), 0, d, b.bg.darker()));
        g.fill(circle);
        g.setClip(circle);

        g.setColor(b.outfit);                                        // shoulders
        g.fill(new Ellipse2D.Double(d * 0.12, d * 0.70, d * 0.76, d * 0.6));
        g.setColor(b.skin);                                          // head
        g.fill(new Ellipse2D.Double(d * 0.30, d * 0.20, d * 0.40, d * 0.46));

        g.setColor(b.hair);
        switch (b.style) {
            case 0:  // short hair
                g.fill(new Arc2D.Double(d * 0.28, d * 0.16, d * 0.44, d * 0.40, 0, 180, Arc2D.CHORD));
                break;
            case 1:  // long hair
                g.fill(new Arc2D.Double(d * 0.28, d * 0.16, d * 0.44, d * 0.40, 0, 180, Arc2D.CHORD));
                g.fill(new RoundRectangle2D.Double(d * 0.26, d * 0.28, d * 0.08, d * 0.40, d * 0.06, d * 0.06));
                g.fill(new RoundRectangle2D.Double(d * 0.66, d * 0.28, d * 0.08, d * 0.40, d * 0.06, d * 0.06));
                break;
            case 2:  // helmet
                g.setColor(new Color(0x9AA3B2));
                g.fill(new Arc2D.Double(d * 0.26, d * 0.13, d * 0.48, d * 0.44, 0, 180, Arc2D.CHORD));
                g.setColor(new Color(0xC9D0DC));
                g.fill(new Rectangle2D.Double(d * 0.485, d * 0.13, d * 0.03, d * 0.2));
                break;
            case 3:  // crown over short hair
                g.fill(new Arc2D.Double(d * 0.28, d * 0.17, d * 0.44, d * 0.38, 0, 180, Arc2D.CHORD));
                Path2D.Double c = new Path2D.Double();
                c.moveTo(d * 0.32, d * 0.22); c.lineTo(d * 0.30, d * 0.06); c.lineTo(d * 0.41, d * 0.15);
                c.lineTo(d * 0.50, d * 0.03); c.lineTo(d * 0.59, d * 0.15); c.lineTo(d * 0.70, d * 0.06);
                c.lineTo(d * 0.68, d * 0.22); c.closePath();
                g.setColor(GOLD);
                g.fill(c);
                break;
            default: // glasses + short hair
                g.fill(new Arc2D.Double(d * 0.28, d * 0.16, d * 0.44, d * 0.40, 0, 180, Arc2D.CHORD));
                break;
        }

        g.setColor(new Color(0x222222));                             // eyes
        g.fill(new Ellipse2D.Double(d * 0.40, d * 0.40, d * 0.04, d * 0.04));
        g.fill(new Ellipse2D.Double(d * 0.56, d * 0.40, d * 0.04, d * 0.04));
        g.setStroke(new BasicStroke(Math.max(1f, d / 40f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Double(d * 0.42, d * 0.45, d * 0.16, d * 0.10, 200, 140, Arc2D.OPEN));   // smile
        if (b.style == 4) {                                          // glasses
            g.draw(new Ellipse2D.Double(d * 0.37, d * 0.37, d * 0.10, d * 0.10));
            g.draw(new Ellipse2D.Double(d * 0.53, d * 0.37, d * 0.10, d * 0.10));
            g.draw(new Line2D.Double(d * 0.47, d * 0.42, d * 0.53, d * 0.42));
        }

        g.setClip(null);
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(Math.max(1.5f, d / 28f)));
        g.draw(new Ellipse2D.Double(0.5, 0.5, d - 1, d - 1));
        g.dispose();
    }

    static class BotCard extends JComponent {
        final Bot bot;
        boolean selected, hover;
        BotCard(Bot b) {
            this.bot = b;
            setPreferredSize(new Dimension(270, 78));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            RoyalGambit.aa(g);
            int w = getWidth(), h = getHeight();
            Shape s = new RoundRectangle2D.Float(1, 1, w - 2, h - 2, 16, 16);
            g.setColor(new Color(255, 255, 255, selected ? 235 : hover ? 175 : 115));
            g.fill(s);
            g.setColor(selected ? INK : BLUE_EDGE);
            g.setStroke(new BasicStroke(selected ? 2.6f : 1.4f));
            g.draw(s);
            int d = h - 18;
            drawAvatar(g, bot, 10, 9, d);
            int tx = d + 24;
            g.setColor(INK);
            g.setFont(new Font(RoyalGambit.SANS, Font.BOLD, 15));
            g.drawString(bot.name, tx, h / 2 - 3);
            g.setColor(INK_SOFT);
            g.setFont(new Font(RoyalGambit.SANS, Font.BOLD, 13));
            g.drawString(bot.tagline, tx, h / 2 + 15);
            g.dispose();
        }
    }

    static int levelIndex() {
        for (int i = 0; i < DIFFICULTIES.length; i++) if (DIFFICULTIES[i].equals(level)) return i;
        return 0;
    }

    static void onPlayVsBot(Component parent, String player) {
        JDialog d = dialog(parent, "Player vs Bot", 640, 720, true);
        JPanel p = (JPanel) d.getContentPane();
        p.add(header("CHOOSE YOUR OPPONENT", "Pick a bot, then pick a difficulty", true), BorderLayout.NORTH);

        // bot grid (scrollable)
        JPanel grid = new JPanel(new GridLayout(0, 2, 12, 12));
        grid.setOpaque(false);
        BotCard[] cards = new BotCard[BOTS.length];
        for (int i = 0; i < BOTS.length; i++) {
            final BotCard card = new BotCard(BOTS[i]);
            card.selected = BOTS[i] == bot;
            card.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    bot = card.bot;
                    for (BotCard c : cards) { if (c != null) { c.selected = c == card; c.repaint(); } }
                }
            });
            cards[i] = card;
            grid.add(card);
        }
        JPanel gridWrap = new JPanel(new BorderLayout());
        gridWrap.setOpaque(false);
        gridWrap.add(grid, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(gridWrap, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        p.add(scroll, BorderLayout.CENTER);

        // difficulty + start
        JPanel south = new JPanel();
        south.setOpaque(false);
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.setBorder(new EmptyBorder(14, 0, 0, 0));

        JLabel dl = lbl("DIFFICULTY", 14, Font.BOLD, INK, RoyalGambit.SANS);
        dl.setAlignmentX(Component.CENTER_ALIGNMENT);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        row.setOpaque(false);
        JLabel desc = lbl(DIFF_DESC[levelIndex()], 14, Font.BOLD, INK_SOFT, RoyalGambit.SANS);
        desc.setAlignmentX(Component.CENTER_ALIGNMENT);
        OutlineButton[] db = new OutlineButton[DIFFICULTIES.length];
        for (int i = 0; i < db.length; i++) {
            final int k = i;
            db[i] = outlineLight(DIFFICULTIES[i].toUpperCase(), 120, 38, 13, () -> {
                level = DIFFICULTIES[k];
                for (int j = 0; j < db.length; j++) { db[j].selected = j == k; db[j].repaint(); }
                desc.setText(DIFF_DESC[k]);
            });
            db[i].selected = DIFFICULTIES[i].equals(level);
            row.add(db[i]);
        }
        RoyalGambit.GoldButton start = gold("START GAME", 280, 48, () -> {
            d.dispose();
            startBotGame(parent, player, bot, level);
        });
        start.setAlignmentX(Component.CENTER_ALIGNMENT);

        south.add(dl);
        south.add(Box.createVerticalStrut(8));
        south.add(row);
        south.add(Box.createVerticalStrut(8));
        south.add(desc);
        south.add(Box.createVerticalStrut(16));
        south.add(start);
        p.add(south, BorderLayout.SOUTH);
        d.setVisible(true);
    }

    // =====================================================================
    //  PLAYER VS PLAYER: Local / Global
    // =====================================================================
    static class ModeCard extends JComponent {
        final String title, desc;
        final boolean globe;
        boolean hover;
        ModeCard(String title, String desc, boolean globe, Runnable click) {
            this.title = title; this.desc = desc; this.globe = globe;
            setPreferredSize(new Dimension(250, 230));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) { click.run(); }
            });
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            RoyalGambit.aa(g);
            int w = getWidth(), h = getHeight();
            Shape s = new RoundRectangle2D.Float(1, 1, w - 2, h - 2, 20, 20);
            g.setColor(new Color(255, 255, 255, hover ? 190 : 120));
            g.fill(s);
            g.setColor(hover ? INK : BLUE_EDGE);
            g.setStroke(new BasicStroke(hover ? 2.6f : 1.6f));
            g.draw(s);

            // icon
            double cx = w / 2.0, cy = 62, r = 34;
            g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(INK);
            if (globe) {
                g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
                g.draw(new Ellipse2D.Double(cx - r * 0.42, cy - r, r * 0.84, r * 2));
                g.draw(new Line2D.Double(cx - r, cy, cx + r, cy));
                g.draw(new Arc2D.Double(cx - r * 0.9, cy - r * 0.55, r * 1.8, r * 1.1, 0, 180, Arc2D.OPEN));
                g.draw(new Arc2D.Double(cx - r * 0.9, cy - r * 0.55, r * 1.8, r * 1.1, 180, 180, Arc2D.OPEN));
            } else {
                for (int k = -1; k <= 1; k += 2) {
                    double px = cx + k * 20;
                    g.draw(new Ellipse2D.Double(px - 10, cy - 28, 20, 20));
                    g.draw(new Arc2D.Double(px - 20, cy - 2, 40, 44, 0, 180, Arc2D.OPEN));
                }
            }

            g.setFont(new Font(RoyalGambit.SERIF, Font.BOLD, 20));
            g.setColor(INK);
            centered(g, title, w / 2, 134);

            g.setFont(new Font(RoyalGambit.SANS, Font.BOLD, 13));
            g.setColor(INK_SOFT);
            int y = 160;
            for (String line : wrap(desc, g.getFontMetrics(), w - 44)) { centered(g, line, w / 2, y); y += 19; }
            g.dispose();
        }
    }

    static void onPlayVsPlayer(Component parent, String player) {
        JDialog d = dialog(parent, "Player vs Player", 620, 400, true);
        JPanel p = (JPanel) d.getContentPane();
        p.add(header("CHOOSE MATCH TYPE", "Play with a friend or find a rival online", true), BorderLayout.NORTH);
        JPanel row = new JPanel(new GridLayout(1, 2, 18, 0));
        row.setOpaque(false);
        row.add(new ModeCard("LOCAL PLAYER", "Two players share this device and take turns.", false,
                () -> { d.dispose(); startLocal(parent, player); }));
        row.add(new ModeCard("GLOBAL PLAYER", "Get matched with a player from anywhere in the world.", true,
                () -> { d.dispose(); searchGlobal(parent, player); }));
        p.add(row, BorderLayout.CENTER);
        d.setVisible(true);
    }

    static void startLocal(Component parent, String player) {
        JTextField white = new JTextField(player == null || player.isEmpty() ? "Player 1" : player, 16);
        JTextField black = new JTextField("Player 2", 16);
        JPanel form = new JPanel(new GridLayout(4, 1, 0, 4));
        form.add(new JLabel("White player name:"));
        form.add(white);
        form.add(new JLabel("Black player name:"));
        form.add(black);
        int ok = JOptionPane.showConfirmDialog(parent, form, "Local match",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;
        String w = white.getText().trim().isEmpty() ? "Player 1" : white.getText().trim();
        String b = black.getText().trim().isEmpty() ? "Player 2" : black.getText().trim();
        startLocalGame(parent, w, b);
    }

    /** Simulated matchmaking (about 3 seconds). Replace the timer body with your real online search. */
    static void searchGlobal(Component parent, String player) {
        String[] names = { "KnightRider", "QueenGambit_88", "EndgameEric", "SilentBishop", "RookieRaven", "CheckmateKai" };
        JDialog d = dialog(parent, "Global match", 440, 260);
        JPanel p = (JPanel) d.getContentPane();
        p.add(header("GLOBAL MATCH", "Finding a worthy rival..."), BorderLayout.NORTH);

        JLabel status = lbl("Searching for an opponent", 18, Font.BOLD, Color.WHITE, RoyalGambit.SANS);
        status.setHorizontalAlignment(SwingConstants.CENTER);
        p.add(status, BorderLayout.CENTER);

        JPanel foot = new JPanel(new FlowLayout(FlowLayout.CENTER));
        foot.setOpaque(false);
        final javax.swing.Timer t = new javax.swing.Timer(400, null);
        foot.add(outline("CANCEL", 160, 40, 14, () -> { t.stop(); d.dispose(); }));
        p.add(foot, BorderLayout.SOUTH);

        final int[] tick = { 0 };
        t.addActionListener(e -> {
            tick[0]++;
            if (tick[0] >= 8) {
                t.stop();
                d.dispose();
                startGlobalGame(parent, player == null || player.isEmpty() ? "You" : player,
                        names[(int) (Math.random() * names.length)]);
                return;
            }
            status.setText("Searching for an opponent" + ".".repeat(tick[0] % 4));
        });
        d.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) { t.stop(); }
        });
        t.start();
        d.setVisible(true);
    }

    // =====================================================================
    //  CHESS PUZZLE: 10 stages
    // =====================================================================
    static class StageCard extends JComponent {
        final int idx;
        boolean hover;
        StageCard(int idx, Runnable click) {
            this.idx = idx;
            setPreferredSize(new Dimension(130, 190));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) {
                    hover = true;
                    setCursor(Cursor.getPredefinedCursor(unlocked(idx) ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                    repaint();
                }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) {
                    if (unlocked(idx)) click.run();
                    // locked stage: Sfx plays the low "locked" tone on mouse press
                }
            });
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            RoyalGambit.aa(g);
            int w = getWidth(), h = getHeight();
            boolean open = unlocked(idx), done = stars[idx] > 0;
            Color edge = open ? BLUE_EDGE : new Color(INK_SOFT.getRed(), INK_SOFT.getGreen(), INK_SOFT.getBlue(), 150);
            Shape s = new RoundRectangle2D.Float(1, 1, w - 2, h - 2, 18, 18);
            g.setColor(open ? new Color(255, 255, 255, hover ? 190 : 120)
                            : new Color(255, 255, 255, 45));
            g.fill(s);
            g.setColor(hover && open ? INK : edge);
            g.setStroke(new BasicStroke(hover && open ? 2.6f : 1.6f));
            g.draw(s);

            // badge: number, check or padlock
            int cx = w / 2, cy = 40, r = 22;
            g.setStroke(new BasicStroke(2f));
            g.setColor(edge);
            g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
            if (open) {
                g.setFont(new Font(RoyalGambit.SERIF, Font.BOLD, 22));
                g.setColor(INK);
                centered(g, String.valueOf(idx + 1), cx, cy + 8);
            } else {
                g.setColor(edge);
                g.fill(new RoundRectangle2D.Double(cx - 8, cy - 2, 16, 12, 4, 4));
                g.setStroke(new BasicStroke(2.2f));
                g.draw(new Arc2D.Double(cx - 5.5, cy - 12, 11, 15, 0, 180, Arc2D.OPEN));
            }

            // title
            g.setFont(new Font(RoyalGambit.SANS, Font.BOLD, 14));
            g.setColor(open ? INK : INK_SOFT);
            int y = 92;
            for (String line : wrap(STAGES[idx].title, g.getFontMetrics(), w - 18)) { centered(g, line, cx, y); y += 17; }

            // status line
            g.setFont(new Font(RoyalGambit.SANS, Font.BOLD, 12));
            g.setColor(done ? new Color(0x0A5C2B) : INK_SOFT);
            centered(g, !open ? "LOCKED" : done ? "COMPLETED" : "TAP TO START", cx, h - 38);

            // stars
            for (int i = 0; i < 3; i++) {
                Shape st = starShape(cx + (i - 1) * 26, h - 18, 9);
                if (i < stars[idx]) {
                    g.setColor(GOLD); g.fill(st);
                    g.setColor(INK); g.setStroke(new BasicStroke(1.2f)); g.draw(st);
                } else {
                    g.setColor(INK_SOFT); g.setStroke(new BasicStroke(1.4f)); g.draw(st);
                }
            }
            g.dispose();
        }
    }

    static void onChessPuzzle(Component parent) {
        JDialog d = dialog(parent, "Chess Puzzle", 780, 540, true);
        JPanel p = (JPanel) d.getContentPane();
        p.add(header("CHESS PUZZLE", "Solve each stage to unlock the next", true), BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(2, 5, 12, 12));
        grid.setOpaque(false);
        StageCard[] cards = new StageCard[STAGES.length];
        JLabel prog = lbl("", 15, Font.BOLD, INK, RoyalGambit.SANS);
        Runnable refresh = () -> {
            prog.setText("Completed " + completedCount() + " / " + STAGES.length);
            for (StageCard c : cards) if (c != null) c.repaint();
        };
        for (int i = 0; i < STAGES.length; i++) {
            final int idx = i;
            cards[i] = new StageCard(idx, () -> { openStage(d, idx); refresh.run(); });
            grid.add(cards[i]);
        }
        p.add(grid, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.setBorder(new EmptyBorder(14, 0, 0, 0));
        south.add(prog, BorderLayout.WEST);
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btns.setOpaque(false);
        btns.add(outlineLight("RESET PROGRESS", 160, 36, 12, () -> {
            int ok = JOptionPane.showConfirmDialog(d, "Reset all puzzle progress?", "Chess Puzzle",
                    JOptionPane.YES_NO_OPTION);
            if (ok == JOptionPane.YES_OPTION) { resetPuzzles(); refresh.run(); }
        }));
        btns.add(outlineLight("CLOSE", 110, 36, 12, d::dispose));
        south.add(btns, BorderLayout.EAST);
        p.add(south, BorderLayout.SOUTH);

        refresh.run();
        d.setVisible(true);
    }

    static void openStage(Component parent, int idx) {
        Stage s = STAGES[idx];
        String[] opts = { "Start", "Cancel" };
        int pick = JOptionPane.showOptionDialog(parent,
                "Stage " + (idx + 1) + " of " + STAGES.length + ": " + s.title
                + "\n\nGoal: " + s.goal + "\nHint: " + s.hint,
                "Chess Puzzle", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, opts, opts[0]);
        if (pick == 0) onStartPuzzle(parent, idx);
    }

    // =====================================================================
    //  TUTORIAL: Coach Royal teaches the rules step by step
    // =====================================================================
    static final Bot COACH = new Bot("Coach Royal", "Your AI chess guide",
            0x1F3B5C, 0xF2C9A0, 0xB0B0B0, 0x24344F, 3);

    /** One tutorial step. pieces = "wK e1,bP d5,..." or "START". show = square whose moves are shown
     *  and can be clicked. green / red = extra squares to mark (comma separated). */
    static class Lesson {
        final String text, pieces, show, green, red;
        Lesson(String text, String pieces, String show, String green, String red) {
            this.text = text; this.pieces = pieces; this.show = show; this.green = green; this.red = red;
        }
    }

    static final Lesson[] LESSONS = {
        new Lesson("I'm Coach Royal, and I'll teach you chess step by step. The game is played on a board of 64 squares, "
                + "8 by 8. Each player has 16 pieces. You play White at the bottom, and White always moves first. "
                + "Press NEXT to continue, or SKIP & PLAY to jump straight into a game.",
                "START", null, null, null),
        new Lesson("The goal of chess is checkmate: attacking the enemy king so that it cannot escape. Look here: the White "
                + "rook on a8 attacks the Black king, and its own pawns block every escape square. That is checkmate, "
                + "and White wins. The king is never actually captured.",
                "bK g8,bP f7,bP g7,bP h7,wR a8,wK g1", null, null, "g8"),
        new Lesson("Let's meet the pieces, starting with the pawn. A pawn moves one square straight forward, or two squares "
                + "on its very first move. It captures one square diagonally forward. Pawns never move backward.",
                "wP e2,bP d3,bP f3", "e2", null, null),
        new Lesson("The rook moves any number of squares in a straight line: forward, backward, left or right. It cannot "
                + "jump over pieces. It stops before its own pieces, and it can capture an enemy piece in its way.",
                "wR d4,wP d6,bP g4", "d4", null, null),
        new Lesson("The bishop moves any number of squares diagonally, and it cannot jump over pieces either. Each bishop "
                + "stays on the same square color for the whole game, so you start with one light-squared and one "
                + "dark-squared bishop.",
                "wB c4,bP f7,wP a2", "c4", null, null),
        new Lesson("The queen is your most powerful piece. She moves like a rook and a bishop combined: any number of "
                + "squares in a straight line or diagonally. Protect her carefully!",
                "wQ d4,bP g7,bN a4", "d4", null, null),
        new Lesson("The knight moves in an L shape: two squares in one direction, then one square to the side. It is the "
                + "only piece that can jump over other pieces, so being surrounded does not trap it.",
                "wN e4,wP e5,wP d4,wP f4,wP e3,bP f6", "e4", null, null),
        new Lesson("The king moves one square in any direction. He is the most important piece, but he can never move to "
                + "a square that an enemy piece attacks. If your king is trapped, you lose the game.",
                "wK e4", "e4", null, null),
        new Lesson("Castling is a special move that protects your king. The king slides two squares toward a rook, and "
                + "that rook jumps over to the other side of him. The green dots show where the king would land. You can "
                + "castle only if neither piece has moved, nothing stands between them, and the king is not in check "
                + "or passing through an attacked square.",
                "wK e1,wR a1,wR h1", null, "g1,c1", null),
        new Lesson("En passant is a special pawn capture. If an enemy pawn moves two squares and lands right beside your "
                + "pawn, you may capture it on your very next move, as if it had moved only one square. Here Black just "
                + "played d7 to d5, so White can capture by moving to d6, marked in red.",
                "wP e5,bP d5", null, null, "d6"),
        new Lesson("Promotion: when a pawn reaches the far end of the board, it becomes a queen, rook, bishop or knight "
                + "of your choice, almost always a queen. Move the pawn forward and watch it transform!",
                "wP e7,bP a7,wK g1,bK h6", "e7", null, null),
        new Lesson("Check means your king is under attack, and you must answer at once by moving the king, blocking the "
                + "attack, or capturing the attacker. If you cannot, it is checkmate. If a player has no legal move but "
                + "is not in check, it is stalemate and the game is a draw.",
                "bK e8,wR e1,bP a7,wK g1", null, null, "e8"),
        new Lesson("Almost ready! Pieces are worth points: pawn 1, knight 3, bishop 3, rook 5, queen 9. Fight for the "
                + "center, bring out your knights and bishops early, castle to keep your king safe, and avoid trading "
                + "big pieces for small ones. You are ready to play. Good luck!",
                "START", null, null, null),
    };

    /** Picks an installed font that can draw the chess symbols. */
    static final String PIECE_FAMILY = pickPieceFont();
    static String pickPieceFont() {
        String[] c = { "Segoe UI Symbol", "DejaVu Sans", "Apple Symbols", "Noto Sans Symbols2", "Arial Unicode MS", "FreeSerif" };
        java.util.Set<String> have = new java.util.HashSet<>(java.util.Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String n : c) if (have.contains(n) && new Font(n, Font.PLAIN, 20).canDisplay('\u265F')) return n;
        return Font.DIALOG;
    }

    static void drawPiece(Graphics2D g, String p, double x, double y, double sq) {
        char[] glyphs = { '\u265A', '\u265B', '\u265C', '\u265D', '\u265E', '\u265F' };   // K Q R B N P
        char gl = glyphs["KQRBNP".indexOf(p.charAt(1))];
        Font f = new Font(PIECE_FAMILY, Font.PLAIN, (int) (sq * 0.8));
        Shape o = new TextLayout(String.valueOf(gl), f, g.getFontRenderContext()).getOutline(null);
        Rectangle2D b = o.getBounds2D();
        Shape s = AffineTransform.getTranslateInstance(x + sq / 2 - b.getCenterX(), y + sq / 2 - b.getCenterY())
                .createTransformedShape(o);
        boolean white = p.charAt(0) == 'w';
        g.setColor(white ? new Color(0xFFF8E6) : new Color(0x1E1913));
        g.fill(s);
        g.setColor(white ? new Color(0x2A2118) : new Color(0xE6D7AE));
        g.setStroke(new BasicStroke(white ? 1.6f : 1.1f));
        g.draw(s);
    }

    /** Mini board used by the tutorial. Click a marked square to try the move yourself. */
    static class TutorialBoard extends JComponent {
        static final Color LIGHT = new Color(0xEBD9B0), DARK = new Color(0x9B7442);
        final String[][] grid = new String[8][8];                       // row 0 = rank 8, col 0 = file a
        final java.util.Set<Integer> targets = new java.util.HashSet<>();
        final java.util.Set<Integer> extraG = new java.util.HashSet<>();
        final java.util.Set<Integer> extraR = new java.util.HashSet<>();
        int showR = -1, showC = -1;
        Runnable onMove;

        TutorialBoard() {
            setPreferredSize(new Dimension(400, 400));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    int sq = hit(e.getX(), e.getY());
                    if (showR < 0 || sq < 0 || !targets.contains(sq)) return;
                    int r = sq / 8, c = sq % 8;
                    String piece = grid[showR][showC];
                    grid[showR][showC] = null;
                    if (piece.charAt(1) == 'P' && (r == 0 || r == 7)) piece = piece.charAt(0) + "Q";
                    grid[r][c] = piece;
                    showR = showC = -1;
                    targets.clear(); extraG.clear(); extraR.clear();
                    repaint();
                    if (onMove != null) onMove.run();
                }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    int sq = hit(e.getX(), e.getY());
                    setCursor(Cursor.getPredefinedCursor(sq >= 0 && targets.contains(sq) ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                }
            });
        }

        int[] geom() {
            int sq = Math.min(getWidth(), getHeight()) / 8;
            return new int[] { sq, (getWidth() - sq * 8) / 2, (getHeight() - sq * 8) / 2 };
        }
        /** Returns row*8+col of the square under the mouse, or -1. */
        int hit(int mx, int my) {
            int[] gm = geom();
            if (mx < gm[1] || my < gm[2]) return -1;
            int c = (mx - gm[1]) / gm[0], r = (my - gm[2]) / gm[0];
            return c > 7 || r > 7 ? -1 : r * 8 + c;
        }

        static int rowOf(String sq) { return 8 - (sq.charAt(1) - '0'); }
        static int colOf(String sq) { return sq.charAt(0) - 'a'; }
        static boolean in(int r, int c) { return r >= 0 && r < 8 && c >= 0 && c < 8; }

        void mark(String csv, java.util.Set<Integer> out) {
            if (csv == null) return;
            for (String t : csv.split(",")) out.add(rowOf(t.trim()) * 8 + colOf(t.trim()));
        }

        void load(Lesson s) {
            for (String[] row : grid) java.util.Arrays.fill(row, null);
            targets.clear(); extraG.clear(); extraR.clear();
            showR = showC = -1;
            if (s.pieces.equals("START")) {
                String back = "RNBQKBNR";
                for (int c = 0; c < 8; c++) {
                    grid[0][c] = "b" + back.charAt(c); grid[1][c] = "bP";
                    grid[6][c] = "wP";                 grid[7][c] = "w" + back.charAt(c);
                }
            } else {
                for (String tok : s.pieces.split(",")) {
                    tok = tok.trim();
                    grid[rowOf(tok.substring(3))][colOf(tok.substring(3))] = tok.substring(0, 2);
                }
            }
            if (s.show != null) {
                showR = rowOf(s.show); showC = colOf(s.show);
                targetsFor(showR, showC, targets);
            }
            mark(s.green, extraG);
            mark(s.red, extraR);
            repaint();
        }

        /** Simple move rules (ignores check) so the diagram always matches the lesson. */
        void targetsFor(int r, int c, java.util.Set<Integer> out) {
            String p = grid[r][c];
            char col = p.charAt(0), t = p.charAt(1);
            if (t == 'P') {
                int dir = col == 'w' ? -1 : 1, start = col == 'w' ? 6 : 1, r1 = r + dir;
                if (in(r1, c) && grid[r1][c] == null) {
                    out.add(r1 * 8 + c);
                    int r2 = r + 2 * dir;
                    if (r == start && grid[r2][c] == null) out.add(r2 * 8 + c);
                }
                for (int dc = -1; dc <= 1; dc += 2) {
                    int cc = c + dc;
                    if (in(r1, cc) && grid[r1][cc] != null && grid[r1][cc].charAt(0) != col) out.add(r1 * 8 + cc);
                }
            } else if (t == 'N' || t == 'K') {
                int[][] d = t == 'N'
                    ? new int[][] { {1,2},{2,1},{-1,2},{-2,1},{1,-2},{2,-1},{-1,-2},{-2,-1} }
                    : new int[][] { {1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1} };
                for (int[] v : d) {
                    int rr = r + v[0], cc = c + v[1];
                    if (in(rr, cc) && (grid[rr][cc] == null || grid[rr][cc].charAt(0) != col)) out.add(rr * 8 + cc);
                }
            } else {
                int[][] straight = { {1,0},{-1,0},{0,1},{0,-1} }, diag = { {1,1},{1,-1},{-1,1},{-1,-1} };
                for (int[][] set : new int[][][] { straight, diag }) {
                    if (set == straight && t == 'B') continue;
                    if (set == diag && t == 'R') continue;
                    for (int[] v : set) {
                        int rr = r + v[0], cc = c + v[1];
                        while (in(rr, cc)) {
                            if (grid[rr][cc] == null) out.add(rr * 8 + cc);
                            else { if (grid[rr][cc].charAt(0) != col) out.add(rr * 8 + cc); break; }
                            rr += v[0]; cc += v[1];
                        }
                    }
                }
            }
        }

        void marker(Graphics2D g, boolean capture, int x, int y, int sq) {
            if (capture) {
                g.setColor(new Color(0xE05050));
                g.setStroke(new BasicStroke(Math.max(3f, sq / 14f)));
                g.draw(new Ellipse2D.Double(x + sq * 0.08, y + sq * 0.08, sq * 0.84, sq * 0.84));
            } else {
                g.setColor(new Color(47, 168, 90, 200));
                g.fill(new Ellipse2D.Double(x + sq * 0.35, y + sq * 0.35, sq * 0.30, sq * 0.30));
            }
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            RoyalGambit.aa(g);
            int[] gm = geom();
            int sq = gm[0], ox = gm[1], oy = gm[2];
            for (int r = 0; r < 8; r++)
                for (int c = 0; c < 8; c++) {
                    g.setColor((r + c) % 2 == 0 ? LIGHT : DARK);
                    g.fillRect(ox + c * sq, oy + r * sq, sq, sq);
                }
            g.setFont(new Font(RoyalGambit.SANS, Font.BOLD, Math.max(9, sq / 5)));
            FontMetrics fm = g.getFontMetrics();
            for (int i = 0; i < 8; i++) {
                g.setColor((7 + i) % 2 == 0 ? DARK : LIGHT);
                String f = String.valueOf((char) ('a' + i));
                g.drawString(f, ox + i * sq + sq - fm.stringWidth(f) - 3, oy + 8 * sq - 3);
                g.setColor(i % 2 == 0 ? DARK : LIGHT);
                g.drawString(String.valueOf(8 - i), ox + 3, oy + i * sq + fm.getAscent() + 2);
            }
            if (showR >= 0) {
                g.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 120));
                g.fillRect(ox + showC * sq, oy + showR * sq, sq, sq);
            }
            for (int r = 0; r < 8; r++)
                for (int c = 0; c < 8; c++)
                    if (grid[r][c] != null) drawPiece(g, grid[r][c], ox + c * sq, oy + r * sq, sq);
            for (int t : targets) marker(g, grid[t / 8][t % 8] != null, ox + (t % 8) * sq, oy + (t / 8) * sq, sq);
            for (int t : extraG) marker(g, false, ox + (t % 8) * sq, oy + (t / 8) * sq, sq);
            for (int t : extraR) marker(g, true, ox + (t % 8) * sq, oy + (t / 8) * sq, sq);
            g.setColor(GOLD);
            g.setStroke(new BasicStroke(3f));
            g.drawRect(ox - 1, oy - 1, sq * 8 + 2, sq * 8 + 2);
            g.dispose();
        }
    }

    /** Coach avatar + name. */
    static class CoachHeader extends JComponent {
        CoachHeader() { setPreferredSize(new Dimension(380, 84)); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            RoyalGambit.aa(g);
            drawAvatar(g, COACH, 2, 2, 80);
            g.setColor(GOLD);
            g.setFont(new Font(RoyalGambit.SERIF, Font.BOLD, 22));
            g.drawString(COACH.name, 100, 38);
            g.setColor(MUTED);
            g.setFont(new Font(RoyalGambit.SANS, Font.PLAIN, 15));
            g.drawString(COACH.tagline, 100, 60);
            g.dispose();
        }
    }

    /** Speech bubble with a typewriter effect. Click it to show the full text instantly. */
    static class Bubble extends JComponent {
        String full = "";
        int shown;
        final javax.swing.Timer timer = new javax.swing.Timer(16, null);
        Bubble() {
            setPreferredSize(new Dimension(380, 300));
            timer.addActionListener(e -> {
                shown = Math.min(full.length(), shown + 2);
                repaint();
                if (shown >= full.length()) timer.stop();
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { shown = full.length(); timer.stop(); repaint(); }
            });
        }
        void say(String t) { full = t; shown = 0; timer.restart(); }
        void stop() { timer.stop(); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            RoyalGambit.aa(g);
            int w = getWidth(), h = getHeight(), tail = 12;
            Shape box = new RoundRectangle2D.Float(1, tail, w - 2, h - tail - 1, 22, 22);
            Path2D.Float arrow = new Path2D.Float();
            arrow.moveTo(36, tail + 1); arrow.lineTo(50, 1); arrow.lineTo(64, tail + 1); arrow.closePath();
            Area bubble = new Area(box);
            bubble.add(new Area(arrow));
            g.setColor(new Color(255, 255, 255, 18));
            g.fill(bubble);
            g.setColor(GOLD);
            g.setStroke(new BasicStroke(1.5f));
            g.draw(bubble);

            g.setFont(new Font(RoyalGambit.SANS, Font.PLAIN, 18));
            g.setColor(Color.WHITE);
            FontMetrics fm = g.getFontMetrics();
            int y = tail + 36, rem = shown;
            for (String line : wrap(full, fm, w - 44)) {
                if (rem <= 0) break;
                g.drawString(line.substring(0, Math.min(rem, line.length())), 22, y);
                rem -= line.length() + 1;
                y += fm.getHeight() + 5;
            }
            g.dispose();
        }
    }

    static void onTutorial(Component parent, String player) {
        JDialog d = dialog(parent, "Tutorial", 920, 650);
        JPanel p = (JPanel) d.getContentPane();
        p.add(header("CHESS TUTORIAL", "Learn the rules step by step with Coach Royal"), BorderLayout.NORTH);

        CoachHeader coach = new CoachHeader();
        Bubble bubble = new Bubble();
        JPanel left = new JPanel(new BorderLayout(0, 10));
        left.setOpaque(false);
        left.setPreferredSize(new Dimension(380, 400));
        left.add(coach, BorderLayout.NORTH);
        left.add(bubble, BorderLayout.CENTER);

        TutorialBoard board = new TutorialBoard();
        JPanel right = new JPanel(new GridBagLayout());
        right.setOpaque(false);
        right.add(board);

        JPanel center = new JPanel(new BorderLayout(24, 0));
        center.setOpaque(false);
        center.add(left, BorderLayout.WEST);
        center.add(right, BorderLayout.CENTER);
        p.add(center, BorderLayout.CENTER);

        final int[] step = { 0 };
        final Runnable[] render = new Runnable[1];

        JLabel stepLbl = lbl("", 15, Font.BOLD, MUTED, RoyalGambit.SANS);
        stepLbl.setHorizontalAlignment(SwingConstants.CENTER);
        OutlineButton skip = outline("SKIP & PLAY", 170, 40, 13, () -> {
            bubble.stop();
            d.dispose();
            startBotGame(parent, player, bot, level);      // start a game right away
        });
        OutlineButton back = outline("BACK", 110, 40, 13, () -> {
            if (step[0] > 0) { step[0]--; render[0].run(); }
        });
        RoyalGambit.GoldButton next = gold("NEXT", 150, 44, () -> {
            if (step[0] < LESSONS.length - 1) { step[0]++; render[0].run(); }
            else { bubble.stop(); d.dispose(); onPlayVsBot(parent, player); }   // finished: pick a bot and play
        });

        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.setBorder(new EmptyBorder(14, 0, 0, 0));
        JPanel sl = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        sl.setOpaque(false);
        sl.add(skip);
        JPanel sr = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        sr.setOpaque(false);
        sr.add(back);
        sr.add(next);
        south.add(sl, BorderLayout.WEST);
        south.add(stepLbl, BorderLayout.CENTER);
        south.add(sr, BorderLayout.EAST);
        p.add(south, BorderLayout.SOUTH);

        render[0] = () -> {
            Lesson s = LESSONS[step[0]];
            board.load(s);
            String t = s.text;
            if (step[0] == 0) t = (player == null || player.isEmpty() ? "Welcome! " : "Welcome, " + player + "! ") + t;
            if (s.show != null) t += " Try it: click a highlighted square to move the piece.";
            bubble.say(t);
            stepLbl.setText("Step " + (step[0] + 1) + " of " + LESSONS.length);
            back.setEnabled(step[0] > 0);
            back.repaint();
            next.setText(step[0] == LESSONS.length - 1 ? "PLAY NOW" : "NEXT");
            next.repaint();
        };
        board.onMove = () -> bubble.say("Well done! That is a legal move for this piece. Press NEXT when you are ready to continue.");
        d.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) { bubble.stop(); }
        });
        render[0].run();
        d.setVisible(true);
    }

    // =====================================================================
    //  MAIN MENU: drop-down with Profile / Stats / Achievements / Settings
    // =====================================================================
    static Shape gearShape(double cx, double cy, double r) {
        Area gear = new Area(new Ellipse2D.Double(cx - r * 0.68, cy - r * 0.68, r * 1.36, r * 1.36));
        Rectangle2D tooth = new Rectangle2D.Double(cx - r * 0.2, cy - r, r * 0.4, r * 0.5);
        for (int i = 0; i < 8; i++) {
            AffineTransform t = AffineTransform.getRotateInstance(i * Math.PI / 4, cx, cy);
            gear.add(new Area(t.createTransformedShape(tooth)));
        }
        gear.subtract(new Area(new Ellipse2D.Double(cx - r * 0.3, cy - r * 0.3, r * 0.6, r * 0.6)));
        return gear;
    }

    /** Small vector icons: 0 profile, 1 stats, 3 achievements, 4 settings, 5 log out. */
    static void drawIcon(Graphics2D g, int kind, double cx, double cy, double r) {
        switch (kind) {
            case 0:
                g.fill(new Ellipse2D.Double(cx - r * 0.35, cy - r * 0.85, r * 0.7, r * 0.7));
                g.fill(new Arc2D.Double(cx - r * 0.8, cy - r * 0.05, r * 1.6, r * 1.6, 0, 180, Arc2D.CHORD));
                break;
            case 1:
                g.fill(new Rectangle2D.Double(cx - r * 0.8, cy + r * 0.1, r * 0.4, r * 0.8));
                g.fill(new Rectangle2D.Double(cx - r * 0.2, cy - r * 0.4, r * 0.4, r * 1.3));
                g.fill(new Rectangle2D.Double(cx + r * 0.4, cy - r * 0.9, r * 0.4, r * 1.8));
                break;
            case 3:
                g.fill(starShape(cx, cy + r * 0.05, r * 1.0));
                break;
            case 5: {
                g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                // door frame, open on the right
                Path2D door = new Path2D.Double();
                door.moveTo(cx + r * 0.1, cy - r * 0.85);
                door.lineTo(cx - r * 0.8, cy - r * 0.85);
                door.lineTo(cx - r * 0.8, cy + r * 0.85);
                door.lineTo(cx + r * 0.1, cy + r * 0.85);
                g.draw(door);
                // arrow pointing out
                g.draw(new Line2D.Double(cx - r * 0.25, cy, cx + r * 0.9, cy));
                Path2D head = new Path2D.Double();
                head.moveTo(cx + r * 0.45, cy - r * 0.4);
                head.lineTo(cx + r * 0.9, cy);
                head.lineTo(cx + r * 0.45, cy + r * 0.4);
                g.draw(head);
                break;
            }
            default:
                g.fill(gearShape(cx, cy, r * 1.05));
        }
    }

    /** One row of the drop-down. */
    static class SubItem extends JMenuItem {
        final int kind;
        SubItem(String text, int kind, Runnable r) {
            super(text);
            this.kind = kind;
            setOpaque(false);
            setBorder(new EmptyBorder(0, 0, 0, 0));
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(220, 44));
            addActionListener(e -> r.run());
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            RoyalGambit.aa(g);
            int w = getWidth(), h = getHeight();
            boolean hot = getModel().isArmed() || getModel().isRollover();
            g.setColor(THEME_LIGHT);
            g.fill(new RoundRectangle2D.Float(0, 1, w, h - 2, 10, 10));
            if (hot) {
                g.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 60));
                g.fill(new RoundRectangle2D.Float(0, 1, w, h - 2, 10, 10));
            }
            g.setColor(hot ? GOLD_LIGHT : GOLD);
            drawIcon(g, kind, 26, h / 2.0, 8);
            g.setColor(hot ? GOLD_LIGHT : Color.WHITE);
            g.setFont(new Font(RoyalGambit.SANS, Font.BOLD, 15));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(getText(), 48, (h - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
        }
    }

    /** Button with the player's avatar and nickname plus a chevron; opens the drop-down menu. */
    static class AccountButton extends JComponent {
        boolean hover, open;
        long closedAt;
        String label = "MENU";   // replaced by the player's nickname
        AccountButton(Runnable action) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Menu");
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (System.currentTimeMillis() - closedAt < 250) return;   // click that just closed the menu
                    action.run();
                }
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            RoyalGambit.aa(g);
            int w = getWidth(), h = getHeight();
            Shape s = new RoundRectangle2D.Float(1, 1, w - 2, h - 2, Math.max(8, h / 3f), Math.max(8, h / 3f));
            if (open || hover) {
                g.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), open ? 70 : 35));
                g.fill(s);
            }
            Color c = hover || open ? GOLD_LIGHT : GOLD;
            g.setColor(c);
            g.setStroke(new BasicStroke(1.6f));
            g.draw(s);

            int d = h - 10;
            drawAvatar(g, BOTS[avatarIndex()], 6, 5, d);

            g.setColor(c);
            g.setFont(new Font(RoyalGambit.SANS, Font.BOLD, Math.max(12, Math.round(h * 0.34f))));
            FontMetrics fm = g.getFontMetrics();
            int tx = 6 + d + 10, avail = w - tx - 30;   // leave room for the chevron
            String txt = label == null || label.isEmpty() ? "MENU" : label;
            if (fm.stringWidth(txt) > avail) {
                while (txt.length() > 1 && fm.stringWidth(txt + "...") > avail) txt = txt.substring(0, txt.length() - 1);
                txt += "...";
            }
            g.drawString(txt, tx, (h - fm.getHeight()) / 2 + fm.getAscent());

            double cx = w - 18, cy = h / 2.0, k = 5;
            Path2D.Double ch = new Path2D.Double();
            if (open) { ch.moveTo(cx - k, cy + k / 2); ch.lineTo(cx, cy - k / 2); ch.lineTo(cx + k, cy + k / 2); }
            else      { ch.moveTo(cx - k, cy - k / 2); ch.lineTo(cx, cy + k / 2); ch.lineTo(cx + k, cy - k / 2); }
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(ch);
            g.dispose();
        }
    }

    // ---- small components used by the menu dialogs ----
    static class AvatarView extends JComponent {
        int idx;
        AvatarView(int idx) { this.idx = idx; }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            int d = Math.min(getWidth(), getHeight()) - 4;
            drawAvatar(g, BOTS[idx], (getWidth() - d) / 2, (getHeight() - d) / 2, d);
            g.dispose();
        }
    }

    static class AvatarPick extends JComponent {
        final int idx;
        boolean on, hover;
        AvatarPick(int idx, Runnable click) {
            this.idx = idx;
            setPreferredSize(new Dimension(70, 70));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(BOTS[idx].name);
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { click.run(); }
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            RoyalGambit.aa(g);
            int d = Math.min(getWidth(), getHeight()) - 14;
            int x = (getWidth() - d) / 2, y = (getHeight() - d) / 2;
            drawAvatar(g, BOTS[idx], x, y, d);
            if (on || hover) {
                g.setColor(on ? GOLD_LIGHT : GOLD);
                g.setStroke(new BasicStroke(on ? 3f : 1.5f));
                g.draw(new Ellipse2D.Double(x - 4, y - 4, d + 8, d + 8));
            }
            g.dispose();
        }
    }

    static class StatTile extends JComponent {
        final String value, label;
        StatTile(String value, String label) {
            this.value = value; this.label = label;
            setPreferredSize(new Dimension(150, 100));
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            RoyalGambit.aa(g);
            int w = getWidth(), h = getHeight();
            Shape s = new RoundRectangle2D.Float(1, 1, w - 2, h - 2, 16, 16);
            g.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 28));
            g.fill(s);
            g.setColor(GOLD);
            g.setStroke(new BasicStroke(1.4f));
            g.draw(s);
            g.setColor(GOLD_LIGHT);
            g.setFont(new Font(RoyalGambit.SERIF, Font.PLAIN, Math.max(22, (int) (h * 0.38))));
            centered(g, value, w / 2, (int) (h * 0.55));
            g.setColor(MUTED);
            g.setFont(new Font(RoyalGambit.SANS, Font.BOLD, 14));
            centered(g, label, w / 2, (int) (h * 0.80));
            g.dispose();
        }
    }

    static class AchRow extends JComponent {
        final String name, desc;
        final boolean got;
        AchRow(String name, String desc, boolean got) {
            this.name = name; this.desc = desc; this.got = got;
            setPreferredSize(new Dimension(400, 58));
            setMinimumSize(new Dimension(200, 58));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            RoyalGambit.aa(g);
            int w = getWidth(), h = getHeight();
            Shape s = new RoundRectangle2D.Float(1, 1, w - 2, h - 2, 14, 14);
            if (got) {
                g.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 30));
                g.fill(s);
            }
            g.setColor(got ? GOLD : new Color(MUTED.getRed(), MUTED.getGreen(), MUTED.getBlue(), 120));
            g.setStroke(new BasicStroke(1.4f));
            g.draw(s);

            Shape star = starShape(34, h / 2.0, 14);
            if (got) { g.setColor(GOLD_LIGHT); g.fill(star); }
            else     { g.setColor(new Color(MUTED.getRed(), MUTED.getGreen(), MUTED.getBlue(), 140)); g.draw(star); }

            g.setColor(got ? Color.WHITE : MUTED);
            g.setFont(new Font(RoyalGambit.SANS, Font.BOLD, 16));
            g.drawString(name, 62, h / 2 - 3);
            g.setColor(MUTED);
            g.setFont(new Font(RoyalGambit.SANS, Font.PLAIN, 13));
            g.drawString(desc, 62, h / 2 + 15);

            g.setColor(got ? GOLD_LIGHT : MUTED);
            g.setFont(new Font(RoyalGambit.SANS, Font.BOLD, 12));
            String st = got ? "UNLOCKED" : "LOCKED";
            g.drawString(st, w - 16 - g.getFontMetrics().stringWidth(st), h / 2 + 4);
            g.dispose();
        }
    }

    static JPanel closeRow(JDialog d) {
        JPanel s = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        s.setOpaque(false);
        s.setBorder(new EmptyBorder(14, 0, 0, 0));
        s.add(gold("CLOSE", 150, 44, d::dispose));
        return s;
    }

    // ---- dialogs ----
    static void showProfile(Component parent, String player, Consumer<String> onNameSaved) {
        JDialog d = menuDialog(parent, "Profile", 520, 640);
        JPanel p = (JPanel) d.getContentPane();
        p.add(header("PROFILE", "Choose your name and avatar"), BorderLayout.NORTH);

        int[] sel = { avatarIndex() };
        AvatarView big = new AvatarView(sel[0]);
        RoyalGambit.fix(big, 120, 120);
        JTextField name = new JTextField();
        name.setDocument(new PlainDocument() {
            @Override public void insertString(int off, String str, AttributeSet a) throws BadLocationException {
                if (str == null) return;
                int room = MAX_NAME - getLength();
                if (room <= 0) return;
                super.insertString(off, str.length() > room ? str.substring(0, room) : str, a);
            }
        });
        name.setText(player == null || player.isEmpty() ? "Player" : player);
        name.setFont(new Font(RoyalGambit.SANS, Font.BOLD, 22));
        name.setForeground(CREAM);
        name.setCaretColor(GOLD_LIGHT);
        name.setHorizontalAlignment(SwingConstants.CENTER);
        name.setOpaque(false);
        name.setToolTipText("Your player name (max " + MAX_NAME + " characters)");
        name.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 2, 0, GOLD), new EmptyBorder(2, 6, 2, 6)));
        RoyalGambit.fix(name, 280, 42);
        JLabel rank = lbl("Rank: " + rankTitle(), 16, Font.BOLD, GOLD, RoyalGambit.SANS);
        JLabel avName = lbl(BOTS[sel[0]].name, 15, Font.BOLD, MUTED, RoyalGambit.SANS);

        JPanel grid = new JPanel(new GridLayout(2, 5, 8, 8));
        grid.setOpaque(false);
        AvatarPick[] picks = new AvatarPick[BOTS.length];
        for (int i = 0; i < BOTS.length; i++) {
            final int idx = i;
            picks[i] = new AvatarPick(i, () -> {
                sel[0] = idx;
                big.idx = idx;
                big.repaint();
                avName.setText(BOTS[idx].name);
                for (AvatarPick a : picks) { a.on = a.idx == idx; a.repaint(); }
            });
            picks[i].on = i == sel[0];
            grid.add(picks[i]);
        }

        JPanel mid = new JPanel();
        mid.setOpaque(false);
        mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
        for (JComponent c : new JComponent[] { big, name, rank, avName }) {
            c.setAlignmentX(Component.CENTER_ALIGNMENT);
            mid.add(c);
            mid.add(Box.createVerticalStrut(4));
        }
        mid.add(Box.createVerticalStrut(14));
        grid.setAlignmentX(Component.CENTER_ALIGNMENT);
        mid.add(grid);
        p.add(mid, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        south.setOpaque(false);
        south.setBorder(new EmptyBorder(14, 0, 0, 0));
        south.add(outline("CANCEL", 130, 44, 13, d::dispose));
        south.add(gold("SAVE", 150, 44, () -> {
            putI("profile.avatar", sel[0]);
            String n = name.getText().trim();
            if (n.isEmpty()) n = player == null || player.isEmpty() ? "Player" : player;   // never save a blank name
            if (onNameSaved != null) onNameSaved.accept(n);
            d.dispose();
            for (Window w : Window.getWindows()) w.repaint();
        }));
        p.add(south, BorderLayout.SOUTH);
        d.setVisible(true);
    }

    static void showStats(Component parent, String player) {
        JDialog d = menuDialog(parent, "Stats", 560, 470);
        JPanel p = (JPanel) d.getContentPane();
        p.add(header("STATS", player == null || player.isEmpty() ? "Your journey so far" : player + "'s journey so far"), BorderLayout.NORTH);

        int games = stat("games"), wins = stat("win"), losses = stat("loss"), draws = stat("draw");
        int rate = games == 0 ? 0 : Math.round(100f * wins / games);
        JPanel grid = new JPanel(new GridLayout(2, 3, 12, 12));
        grid.setOpaque(false);
        grid.add(new StatTile(String.valueOf(games), "Games played"));
        grid.add(new StatTile(String.valueOf(wins), "Wins"));
        grid.add(new StatTile(String.valueOf(losses), "Losses"));
        grid.add(new StatTile(String.valueOf(draws), "Draws"));
        grid.add(new StatTile(rate + "%", "Win rate"));
        grid.add(new StatTile(totalStars() + " / " + (STAGES.length * 3), "Puzzle stars"));

        JLabel foot = lbl("Vs Bot: " + stat("bot") + "    Local: " + stat("local") + "    Global: " + stat("global")
                + "    |    Puzzles solved: " + completedCount() + " / " + STAGES.length,
                14, Font.BOLD, MUTED, RoyalGambit.SANS);
        foot.setHorizontalAlignment(SwingConstants.CENTER);
        foot.setBorder(new EmptyBorder(14, 0, 0, 0));

        JPanel mid = new JPanel(new BorderLayout());
        mid.setOpaque(false);
        mid.add(grid, BorderLayout.CENTER);
        mid.add(foot, BorderLayout.SOUTH);
        p.add(mid, BorderLayout.CENTER);
        p.add(closeRow(d), BorderLayout.SOUTH);
        d.setVisible(true);
    }

    static void showAchievements(Component parent) {
        JDialog d = menuDialog(parent, "Achievements", 560, 620);
        JPanel p = (JPanel) d.getContentPane();
        boolean[] got = achievements();
        int n = 0;
        for (boolean b : got) if (b) n++;
        p.add(header("ACHIEVEMENTS", n + " of " + got.length + " unlocked"), BorderLayout.NORTH);

        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        for (int i = 0; i < got.length; i++) {
            list.add(new AchRow(ACH_NAME[i], ACH_DESC[i], got[i]));
            list.add(Box.createVerticalStrut(8));
        }
        JScrollPane sp = new JScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        p.add(sp, BorderLayout.CENTER);
        p.add(closeRow(d), BorderLayout.SOUTH);
        d.setVisible(true);
    }

    static void showSettings(Component parent) {
        JDialog d = menuDialog(parent, "Settings", 480, 450);
        JPanel p = (JPanel) d.getContentPane();
        p.add(header("SETTINGS", "Make it yours"), BorderLayout.NORTH);

        int vol = getI("settings.volume", 70);
        JLabel vl = lbl("Sound effects volume: " + vol + "%", 16, Font.BOLD, Color.WHITE, RoyalGambit.SANS);
        JSlider sl = new JSlider(0, 100, vol);
        sl.setOpaque(false);
        sl.setForeground(GOLD);
        sl.addChangeListener(e -> vl.setText("Sound effects volume: " + sl.getValue() + "%"));

        JLabel ll = lbl("Language", 16, Font.BOLD, Color.WHITE, RoyalGambit.SANS);
        JComboBox<String> cb = new JComboBox<>(LANGUAGES);
        cb.setSelectedItem(getS("settings.language", LANGUAGES[0]));
        cb.setBackground(BG_LIGHT);
        cb.setForeground(CREAM);
        cb.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        OutlineButton reset = outline("RESET PUZZLE PROGRESS", 260, 40, 13, () -> {
            int ok = JOptionPane.showConfirmDialog(d, "Erase all puzzle stars and lock the stages again?",
                    "Royal Gambit", JOptionPane.YES_NO_OPTION);
            if (ok == JOptionPane.YES_OPTION) resetPuzzles();
        });

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        for (JComponent c : new JComponent[] { vl, sl, ll, cb, reset }) c.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(vl);
        form.add(Box.createVerticalStrut(6));
        form.add(sl);
        form.add(Box.createVerticalStrut(22));
        form.add(ll);
        form.add(Box.createVerticalStrut(6));
        form.add(cb);
        form.add(Box.createVerticalStrut(26));
        form.add(reset);
        p.add(form, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        south.setOpaque(false);
        south.setBorder(new EmptyBorder(14, 0, 0, 0));
        south.add(outline("CANCEL", 130, 44, 13, d::dispose));
        south.add(gold("SAVE", 150, 44, () -> {
            putI("settings.volume", sl.getValue());
            putS("settings.language", String.valueOf(cb.getSelectedItem()));
            d.dispose();
        }));
        p.add(south, BorderLayout.SOUTH);
        d.setVisible(true);
    }

    // =====================================================================
    //  Hover effect: the wrapped button grows slightly and glows gold
    // =====================================================================
    static class HoverGlow extends JPanel {
        final AbstractButton button;
        float t;                 // 0 = idle ... 1 = fully hovered (animated)
        boolean hover;
        final javax.swing.Timer anim;

        HoverGlow(AbstractButton b) {
            super(new BorderLayout());
            this.button = b;
            setOpaque(false);
            add(b, BorderLayout.CENTER);
            anim = new javax.swing.Timer(15, e -> {
                float target = hover ? 1f : 0f;
                t += (target - t) * 0.28f;
                if (Math.abs(target - t) < 0.01f) { t = target; ((javax.swing.Timer) e.getSource()).stop(); }
                repaint();
            });
            b.addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; anim.start(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; anim.start(); }
            });
            b.getModel().addChangeListener(e -> repaint());   // keep pressed/rollover repaints inside the effect
        }

        void setPad(int x, int y) { setBorder(new EmptyBorder(y, x, y, x)); }

        /** Makes repaints of the button go through this panel, so the scaling is always applied. */
        @Override protected boolean isPaintingOrigin() { return true; }

        @Override public void paint(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            RoyalGambit.aa(g);
            Rectangle b = button.getBounds();
            double cx = b.getCenterX(), cy = b.getCenterY();
            double sc = 1 + 0.07 * t;                              // grows by up to 7%
            if (t > 0.01f) {                                       // soft golden glow behind the button
                double w = b.width * sc, h = b.height * sc;
                int r = Math.max(6, b.height / 6);
                for (int i = 9; i >= 1; i--) {
                    double e = i * 1.2;
                    g.setColor(new Color(GOLD_LIGHT.getRed(), GOLD_LIGHT.getGreen(), GOLD_LIGHT.getBlue(),
                            Math.min(255, Math.round(t * 11))));
                    g.fill(new RoundRectangle2D.Double(cx - w / 2 - e, cy - h / 2 - e, w + 2 * e, h + 2 * e, r + e, r + e));
                }
            }
            g.translate(cx, cy);
            g.scale(sc, sc);
            g.translate(-cx, -cy);
            super.paint(g);
            g.dispose();
        }
    }

    // =====================================================================
    //  Window
    // =====================================================================
    final String login;   // username used to log in (the nickname is saved under it)
    String player;        // current nickname (can be changed in Profile)
    final Bg root = new Bg();
    { root.scrim = 70; }
    final JLabel title = new JLabel("ROYAL GAMBIT", SwingConstants.CENTER);
    final JLabel welcome;
    final OutlineButton vsBot = new OutlineButton("PLAYER VS BOT");
    final OutlineButton vsPlayer = new OutlineButton("PLAYER VS PLAYER");
    final OutlineButton puzzle = new OutlineButton("CHESS PUZZLE");
    final HoverGlow glowBot = new HoverGlow(vsBot);
    final HoverGlow glowPlayer = new HoverGlow(vsPlayer);
    final HoverGlow glowPuzzle = new HoverGlow(puzzle);
    final AccountButton account;
    final JPanel topBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));

    Page(String player) {
        super("Royal Gambit");
        setUndecorated(true);                     // custom title bar: Close only (no minimize / maximize)
        this.login = player;
        this.player = nickname(login, player);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        welcome = new JLabel(welcomeText(this.player), SwingConstants.CENTER);
        welcome.setForeground(Color.WHITE);
        title.setForeground(GOLD);

        account = new AccountButton(this::showAccountMenu);
        account.label = this.player == null || this.player.isEmpty() ? "MENU" : this.player;
        topBar.setOpaque(false);
        topBar.add(account);

        vsBot.addActionListener(e -> onPlayVsBot(this, this.player));
        vsPlayer.addActionListener(e -> onPlayVsPlayer(this, this.player));
        puzzle.addActionListener(e -> onChessPuzzle(this));
        vsPlayer.setForeground(GOLD);
        puzzle.setForeground(GOLD);
        vsBot.setForeground(GOLD);
        vsBot.filled = true;
        vsPlayer.filled = true;
        puzzle.filled = true;

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        for (JComponent c : new JComponent[] { title, welcome, glowBot, glowPlayer, glowPuzzle }) {
            c.setAlignmentX(Component.CENTER_ALIGNMENT);
            content.add(c);
        }

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        center.add(content, new GridBagConstraints());

        root.add(topBar, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);

        root.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { relayout(); }
        });
        setContentPane(root);
        installTitleBar(getRootPane(), this, "Royal Gambit", false, false);
        setMinimumSize(new Dimension(420, 560));
        setSize(1100, 700);                       // size used when the window is restored down
        setLocationRelativeTo(null);
        setExtendedState(JFrame.NORMAL);          // starts as a normal window, not maximized

        // Re-stretch the menu every time the window is minimized/restored/maximized
        addWindowStateListener(e -> SwingUtilities.invokeLater(() -> {
            root.revalidate();
            relayout();
            root.repaint();
        }));
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { relayout(); }
        });
        JRootPane rp = getRootPane();
        rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0), "fs");
        rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "fsExit");
        rp.getActionMap().put("fs", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { setFullScreen(!fullScreen); }
        });
        rp.getActionMap().put("fsExit", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { if (fullScreen) setFullScreen(false); }
        });
        relayout();
    }

    boolean fullScreen = false;

    /** F11 toggles true full screen (no title bar / taskbar); ESC leaves it. */
    void setFullScreen(boolean on) {
        if (on == fullScreen) return;
        GraphicsDevice dev = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        fullScreen = on;
        getJMenuBar().setVisible(!on);                         // hide the custom title bar in full screen
        getRootPane().setBorder(on ? null : windowBorder());
        dev.setFullScreenWindow(on ? this : null);
        if (!on) setExtendedState(JFrame.NORMAL);
        revalidate();
        repaint();
        SwingUtilities.invokeLater(this::relayout);
    }

    static String welcomeText(String n) {
        String base = (n == null || n.isEmpty()) ? " " : "Welcome, " + n;
        return guest ? base + "  -  Guest mode: progress is not saved" : base;
    }

    /** Called when the nickname is saved in Profile: updates the top-right button and the welcome text. */
    void setPlayerName(String n) {
        player = n;
        putS(nameKey(login), n);
        welcome.setText(welcomeText(n));
        account.label = n;
        account.repaint();
        root.revalidate();
        root.repaint();
    }

    /** Opens the Menu drop-down just below the button, aligned to the right edge. */
    void showAccountMenu() {
        JPopupMenu pm = new JPopupMenu();
        pm.setOpaque(true);
        pm.setBackground(THEME_DARK);
        pm.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GOLD, 1, true), new EmptyBorder(6, 6, 6, 6)));
        pm.add(new SubItem("Profile",      0, () -> showProfile(this, player, this::setPlayerName)));
        pm.add(new SubItem("Stats",        1, () -> showStats(this, player)));
        pm.add(new SubItem("Achievements", 3, () -> showAchievements(this)));
        pm.add(new SubItem("Settings",     4, () -> showSettings(this)));
        pm.add(new SubItem(guest ? "Sign Up / Log In" : "Log Out", 5, this::logout));
        pm.addPopupMenuListener(new PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(PopupMenuEvent e) { }
            @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                account.open = false;
                account.closedAt = System.currentTimeMillis();
                account.repaint();
            }
            @Override public void popupMenuCanceled(PopupMenuEvent e) { }
        });
        account.open = true;
        account.repaint();
        int pw = pm.getPreferredSize().width;
        pm.show(account, account.getWidth() - pw, account.getHeight() + 6);
    }

    /** Asks for confirmation, closes the main menu and returns to the Welcome Back screen. */
    void logout() {
        int choice = JOptionPane.showConfirmDialog(this, guest
                ? "Leave guest mode? Your guest progress will be lost."
                : "Are you sure you want to log out?",
                "Log Out", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;
        for (Window w : Window.getWindows())      // close any open dialogs too
            if (w != this && w.isDisplayable() && w instanceof JDialog) w.dispose();
        dispose();
        RoyalGambit.showLogin();
    }

    void relayout() {
        int w = root.getWidth(), h = root.getHeight();
        if (w <= 0 || h <= 0) { w = 1280; h = 720; }

        int bh = RoyalGambit.clamp((int) (h * 0.075), 40, 60);
        int bw = Math.min(RoyalGambit.clamp((int) (w * 0.30), 260, 420), w - 48);
        float base = Math.max(13f, bh * 0.34f);

        int ah = RoyalGambit.clamp((int) (h * 0.055), 36, 46);
        int aw = RoyalGambit.clamp((int) (w * 0.15), 170, 210);
        RoyalGambit.fix(account, aw, ah);
        topBar.setBorder(new EmptyBorder(16, 16, 0, 20));

        int s = Math.max(24, Math.min((int) (bh * 1.6), (w - 32) / 9));

        int top = (int) (s * 0.25);
        title.setFont(new Font(RoyalGambit.SERIF, Font.PLAIN, s));
        title.setBorder(new EmptyBorder(top, 0, 4, 0));
        RoyalGambit.fix(title, Math.max(200, w - 32), (int) (s * 1.3) + top + 4);

        int wb = (int) (bh * 0.7);
        welcome.setFont(new Font(RoyalGambit.SANS, Font.BOLD, Math.round(base * 1.1f)));
        welcome.setBorder(new EmptyBorder(0, 0, wb, 0));
        RoyalGambit.fix(welcome, bw, (int) (base * 1.5) + wb);

        Font bf = new Font(RoyalGambit.SANS, Font.BOLD, Math.round(base * 1.1f));
        int padX = 20, padY = 12;                   // room around each button for the glow and the enlargement
        for (HoverGlow hg : new HoverGlow[] { glowBot, glowPlayer, glowPuzzle }) {
            AbstractButton b = hg.button;
            b.setFont(bf);
            b.setBorder(new EmptyBorder(0, 0, 0, 0));
            RoyalGambit.fix(b, bw, bh);
            hg.setPad(padX, padY);
            RoyalGambit.fix(hg, bw + 2 * padX, bh + 2 * padY);
        }

        root.revalidate();
        root.repaint();
    }

    /** Call this after a successful login. */
    static void open(String username) {
        guest = false;
        GUEST_DATA.clear();
        loadStars();
        Sfx.install();                     // button click sound effects
        SwingUtilities.invokeLater(() -> new Page(username).setVisible(true));
    }

    /** Play without an account: fresh progress, nothing is saved. */
    static void openGuest() {
        guest = true;
        GUEST_DATA.clear();
        loadStars();                       // all puzzle stages start locked
        Sfx.install();                     // button click sound effects
        SwingUtilities.invokeLater(() -> new Page("Guest").setVisible(true));
    }

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        open("Player");
    }
}
