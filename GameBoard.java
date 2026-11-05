import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * ROYAL GAMBIT - the chess board screen, shared by Player vs Bot and Player vs Player.
 * Needs Chess.java, Page.java, PieceArt.java and RoyalGambit.java in the same folder.
 *
 *   GameBoard.playBot(parent, player, bot, difficulty)   Player vs Bot
 *   GameBoard.playLocal(parent, white, black)            two players, same device
 *   GameBoard.playGlobal(parent, player, opponent)       online match (opponent is simulated for now)
 *
 * Click a piece, then click a highlighted square to move. Full rules: castling, en passant,
 * promotion, check, checkmate, stalemate, 50-move rule, threefold repetition, insufficient material.
 *
 * This version draws the pieces with PieceArt (distinct pawn / rook / knight / bishop / queen / king)
 * and has a MENU button (top of the side panel) with: New Game, Restart, Settings, Leave.
 */
public class GameBoard {

    enum Mode { BOT, LOCAL, GLOBAL }

    static final Color LIGHT = new Color(0xEBD9B0), DARK = new Color(0x9B7442);
    static final String LETTERS = " PNBRQK";
    static final int[] AV_BG   = { 0x3E7C4F, 0x2F5D8C, 0x7A3F8C, 0x8C5A2F, 0xA03040, 0x3A6B6B, 0xC9692B };
    static final int[] AV_SKIN = { 0xF2C9A0, 0xD9A066, 0xC68642, 0x8D5A3B, 0xFFDFC4 };
    static final int[] AV_HAIR = { 0x2B2B2B, 0x5A3A1E, 0x7B3F1D, 0x111111, 0xE0A030 };

    static final Color PANEL_BG  = new Color(0x0E1730);
    static final Color TEXT      = new Color(0xFFF4D6);
    static final Color TEXT_DIM  = new Color(0xC9BFA6);
    static final Color BTN_BLUE  = new Color(0x1E3566);
    static final Color BTN_RED   = new Color(0x8E2A22);

    // ----- board settings (kept for the whole session, shared by every game) -----
    static boolean setHints = true, setCoords = true, setLastMove = true, setAutoFlip = false;
    static int setTheme = 0;
    static final String[] THEME_NAMES = { "Classic Wood", "Forest Green", "Ocean Blue", "Royal Purple" };
    static final Color[][] THEMES = {
        { new Color(0xEBD9B0), new Color(0x9B7442) },
        { new Color(0xEEEED2), new Color(0x769656) },
        { new Color(0xDEE3E6), new Color(0x8CA2AD) },
        { new Color(0xE8DDF0), new Color(0x7B5EA7) } };
    static Color themeLight() { return THEMES[setTheme][0]; }
    static Color themeDark()  { return THEMES[setTheme][1]; }

    // =====================================================================
    //  Entry points
    // =====================================================================
    static void playBot(Component parent, String player, Page.Bot bot, String difficulty) {
        int lv = 0;
        for (int i = 0; i < Page.DIFFICULTIES.length; i++) if (Page.DIFFICULTIES[i].equals(difficulty)) lv = i;
        String you = player == null || player.isEmpty() ? "You" : player;
        new GameBoard(parent, Mode.BOT, "Player vs Bot - " + bot.name + " (" + difficulty + ")",
                you, human(you), bot.name, bot, lv).open();
    }

    static void playLocal(Component parent, String white, String black) {
        new GameBoard(parent, Mode.LOCAL, "Local match", white, human(white), black, human(black), 0).open();
    }

    /** Replace the simulated opponent (a medium-strength bot) with your real online connection. */
    static void playGlobal(Component parent, String player, String opponent) {
        String you = player == null || player.isEmpty() ? "You" : player;
        new GameBoard(parent, Mode.GLOBAL, "Global match", you, human(you), opponent, human(opponent), 1).open();
    }

    static Page.Bot human(String name) {
        int h = name.hashCode() & 0x7fffffff;
        int bg = AV_BG[h % AV_BG.length];
        return new Page.Bot(name, "", bg, AV_SKIN[(h / 7) % AV_SKIN.length], AV_HAIR[(h / 13) % AV_HAIR.length],
                (bg >> 1) & 0x7F7F7F, h % 5);
    }

    static Color alpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), a); }
    static String code(int v) { return (v > 0 ? "w" : "b") + LETTERS.charAt(Math.abs(v)); }

    /** Text with a soft dark shadow so it stays readable on any background. */
    static void shadowText(Graphics2D g, String s, int x, int y, Color c) {
        g.setColor(new Color(0, 0, 0, 170));
        g.drawString(s, x + 1, y + 1);
        g.setColor(c);
        g.drawString(s, x, y);
    }

    // =====================================================================
    //  State
    // =====================================================================
    private final Component parent;
    private final Mode mode;
    private final String title, p1Name, p2Name;
    private final Page.Bot p1Av, p2Av;
    private final int level;
    private boolean p1White = true;          // player 1 (you / first player) plays White

    private Chess.Position pos;
    private final List<Chess.Position> past = new ArrayList<>();
    private final List<Chess.Move> played = new ArrayList<>();
    private final List<String> sans = new ArrayList<>();
    private final List<String> keys = new ArrayList<>();
    private Chess.Move last, hintMove;
    private int sel = -1;
    private final List<Chess.Move> selMoves = new ArrayList<>();
    private boolean gameOver, busy, flipped, closed;
    private int epoch;                       // bumped to cancel a bot move that is still being calculated
    private String statusText = " ", endMessage;

    private JDialog dialog;
    private BoardView view;
    private PlayerCard topCard, bottomCard;
    private StatusBar statusBar;
    private JTextArea moveText;
    private Btn bHint, navBtn;
    private static final int[] HINTS_BY_LEVEL = { 8, 5, 3, 1 };   // Easy, Medium, Hard, Extreme
    private int hintsLeft;

    /** Hints allowed per game: depends on the bot difficulty; local matches get 5. */
    private int maxHints() {
        if (mode != Mode.BOT) return 5;
        return HINTS_BY_LEVEL[Math.max(0, Math.min(level, HINTS_BY_LEVEL.length - 1))];
    }
    private JPopupMenu navMenu;
    private JMenuItem miRestart, miMax;
    private boolean maximized;
    private Rectangle normalBounds;

    private GameBoard(Component parent, Mode mode, String title, String n1, Page.Bot a1,
                      String n2, Page.Bot a2, int level) {
        this.parent = parent; this.mode = mode; this.title = title;
        this.p1Name = n1; this.p1Av = a1; this.p2Name = n2; this.p2Av = a2; this.level = level;
    }

    private String whiteName() { return p1White ? p1Name : p2Name; }
    private String blackName() { return p1White ? p2Name : p1Name; }
    private Page.Bot avatar(boolean white) { return white == p1White ? p1Av : p2Av; }
    private String nameOf(boolean white) { return white ? whiteName() : blackName(); }
    private boolean humanTurn() { return mode == Mode.LOCAL || pos.white == p1White; }

    // =====================================================================
    //  Window
    // =====================================================================
    private void open() {
        dialog = Page.dialog(parent, title, 1020, 820);
        Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
        dialog.setSize(Math.min(1040, scr.width - 40), Math.min(820, scr.height - 80));
        dialog.setMinimumSize(new Dimension(860, 620));
        dialog.setResizable(true);
        dialog.setLocationRelativeTo(parent);

        JPanel p = (JPanel) dialog.getContentPane();
        view = new BoardView();
        view.setPreferredSize(new Dimension(640, 640));
        p.add(view, BorderLayout.CENTER);
        p.add(buildSide(), BorderLayout.EAST);

        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) { closed = true; epoch++; }
        });
        // F11 also maximizes / restores the window
        dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0), "toggleMax");
        dialog.getRootPane().getActionMap().put("toggleMax", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { toggleMaximize(); }
        });
        SoundFx.volume = Page.getI("settings.volume", 70) / 100f;   // same volume as Menu > Settings
        new Thread(SoundFx::preload).start();          // build the sound effects in the background
        reset();
        dialog.setVisible(true);
    }

    private JPanel buildSide() {
        JPanel side = new JPanel();
        side.setOpaque(false);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(new EmptyBorder(0, 20, 0, 0));
        side.setPreferredSize(new Dimension(335, 640));

        statusBar = new StatusBar();
        statusBar.setAlignmentX(Component.CENTER_ALIGNMENT);
        topCard = new PlayerCard();
        bottomCard = new PlayerCard();
        topCard.setAlignmentX(Component.CENTER_ALIGNMENT);
        bottomCard.setAlignmentX(Component.CENTER_ALIGNMENT);

        // ----- framed move history -----
        moveText = new JTextArea();
        moveText.setEditable(false);
        moveText.setOpaque(false);
        moveText.setForeground(TEXT);
        moveText.setFont(new Font(Font.MONOSPACED, Font.BOLD, 15));
        moveText.setBorder(new EmptyBorder(4, 12, 8, 10));
        JScrollPane scroll = new JScrollPane(moveText, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);

        JLabel hdr = new JLabel("MOVE HISTORY");
        hdr.setFont(new Font(RoyalGambit.SANS, Font.BOLD, 13));
        hdr.setForeground(Page.GOLD_LIGHT);
        hdr.setBorder(new EmptyBorder(8, 12, 2, 10));

        JPanel history = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                RoyalGambit.aa(g);
                Shape s = new RoundRectangle2D.Float(1, 1, getWidth() - 3, getHeight() - 3, 16, 16);
                g.setColor(alpha(PANEL_BG, 205));
                g.fill(s);
                g.setColor(alpha(Page.GOLD, 150));
                g.setStroke(new BasicStroke(1.5f));
                g.draw(s);
                g.dispose();
            }
        };
        history.setOpaque(false);
        history.add(hdr, BorderLayout.NORTH);
        history.add(scroll, BorderLayout.CENTER);
        history.setPreferredSize(new Dimension(300, 150));
        history.setAlignmentX(Component.CENTER_ALIGNMENT);

        // ----- main navigation: MENU button with a drop-down sub menu -----
        navBtn = new Btn("MENU", false, () -> showNav());
        navBtn.burger = true;
        navBtn.setPreferredSize(new Dimension(124, 40));          // just big enough for the icon + "MENU"
        navBtn.setMinimumSize(new Dimension(124, 40));
        JPanel navRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        navRow.setOpaque(false);
        navRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        navRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        navRow.add(navBtn);
        buildNavMenu();

        // ----- in-game buttons (undo / surrender / swap side removed) -----
        bHint      = new Btn("HINT", false, this::hint);
        Btn bFlip  = new Btn("FLIP BOARD", false, this::flip);

        JPanel btns = new JPanel(new GridLayout(0, 2, 10, 10));
        btns.setOpaque(false);
        btns.setAlignmentX(Component.CENTER_ALIGNMENT);
        if (mode != Mode.GLOBAL) btns.add(bHint);
        btns.add(bFlip);
        btns.setMaximumSize(new Dimension(Integer.MAX_VALUE, btns.getPreferredSize().height));

        side.add(navRow);
        side.add(Box.createVerticalStrut(10));
        side.add(statusBar);
        side.add(Box.createVerticalStrut(10));
        side.add(topCard);
        side.add(Box.createVerticalStrut(10));
        side.add(history);
        side.add(Box.createVerticalStrut(10));
        side.add(bottomCard);
        side.add(Box.createVerticalStrut(14));
        side.add(btns);
        return side;
    }

    private void refresh() {
        if (setAutoFlip && mode == Mode.LOCAL) flipped = !pos.white;     // settings: auto-flip on local games
        if (miRestart != null) miRestart.setEnabled(!played.isEmpty());
        topCard.whiteSide = flipped;
        bottomCard.whiteSide = !flipped;
        statusBar.set(statusText, gameOver ? 2 : pos.inCheck(pos.white) ? 1 : 0);
        bHint.setText("HINT (" + hintsLeft + ")");
        bHint.setEnabled(!gameOver && !busy && humanTurn() && hintsLeft > 0);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sans.size(); i += 2) {
            sb.append(String.format("%2d. %-9s", i / 2 + 1, sans.get(i)));
            if (i + 1 < sans.size()) sb.append(sans.get(i + 1));
            sb.append('\n');
        }
        moveText.setText(sb.toString());
        moveText.setCaretPosition(moveText.getDocument().getLength());
        topCard.repaint();
        bottomCard.repaint();
        view.repaint();
    }

    // =====================================================================
    //  Game flow
    // =====================================================================
    private void reset() {
        epoch++;
        busy = false;
        pos = Chess.Position.start();
        past.clear(); played.clear(); sans.clear(); keys.clear();
        keys.add(pos.key());
        last = null; hintMove = null; sel = -1; selMoves.clear();
        hintsLeft = maxHints();
        gameOver = false;
        flipped = mode != Mode.LOCAL && !p1White;     // you always start at the bottom
        updateStatus();
        refresh();
        maybeBotMove();
    }

    // =====================================================================
    //  Main navigation: New Game / Restart / Settings / Leave
    // =====================================================================
    private void buildNavMenu() {
        navMenu = new JPopupMenu();
        navMenu.setOpaque(true);
        navMenu.setBackground(PANEL_BG);
        navMenu.setBorder(new LineBorder(Page.GOLD, 2, true));
        if (mode != Mode.GLOBAL) {
            navMenu.add(new NavItem("New Game", Color.WHITE, this::newGame));
            miRestart = new NavItem("Restart", Color.WHITE, this::restart);
            navMenu.add(miRestart);
        }
        navMenu.add(new NavItem("Settings", Color.WHITE, this::openSettings));
        navMenu.add(new NavItem("Minimize", Color.WHITE, this::minimize));
        miMax = new NavItem("Maximize", Color.WHITE, this::toggleMaximize);
        navMenu.add(miMax);
        navMenu.add(new NavItem("Leave", new Color(0xFF8A80), this::leave));
    }

    private void showNav() {
        int w = 0;
        for (Component c : navMenu.getComponents()) w = Math.max(w, c.getPreferredSize().width);
        w = Math.max(w, navBtn.getWidth() - 4);
        for (Component c : navMenu.getComponents())
            if (c instanceof JComponent) ((JComponent) c).setPreferredSize(new Dimension(w, 38));
        navMenu.show(navBtn, navBtn.getWidth() - (w + 4), navBtn.getHeight() + 4);   // right edges line up
    }

    /** Minimize: a dialog has no minimize button of its own, so the whole game window (its owner) is iconified. */
    private void minimize() {
        Window w = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        if (w instanceof Frame) ((Frame) w).setState(Frame.ICONIFIED);
        else {
            statusText = "Minimize is not available for this window";
            refresh();
        }
    }

    /** Maximize fills the usable screen (taskbar stays visible); choosing it again restores the old size. */
    private void toggleMaximize() {
        if (!maximized) {
            normalBounds = dialog.getBounds();
            GraphicsConfiguration gc = dialog.getGraphicsConfiguration();
            Rectangle b = gc.getBounds();
            Insets in = Toolkit.getDefaultToolkit().getScreenInsets(gc);
            dialog.setBounds(b.x + in.left, b.y + in.top, b.width - in.left - in.right, b.height - in.top - in.bottom);
            maximized = true;
        } else {
            if (normalBounds != null) dialog.setBounds(normalBounds);
            maximized = false;
        }
        miMax.setText(maximized ? "Restore" : "Maximize");
        dialog.revalidate();
        view.repaint();
    }

    /** New game: against a bot you choose your colour; otherwise it just starts fresh. */
    private void newGame() {
        if (!gameOver && !played.isEmpty()) {
            int ok = JOptionPane.showConfirmDialog(dialog, "Abandon this game and start a new one?", "New game",
                    JOptionPane.YES_NO_OPTION);
            if (ok != JOptionPane.YES_OPTION) return;
        }
        if (mode == Mode.BOT) {
            String[] opts = { "White", "Black", "Random" };
            int pick = JOptionPane.showOptionDialog(dialog, "Play as:", "New game", JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE, null, opts, opts[0]);
            if (pick < 0) return;
            p1White = pick == 0 || (pick == 2 && new Random().nextBoolean());
        }
        reset();
    }

    /** Restart: same players, same colours, board back to the start. */
    private void restart() {
        if (played.isEmpty()) return;
        if (!gameOver) {
            int ok = JOptionPane.showConfirmDialog(dialog, "Restart this game with the same colours?", "Restart",
                    JOptionPane.YES_NO_OPTION);
            if (ok != JOptionPane.YES_OPTION) return;
        }
        reset();
    }

    private void leave() {
        if (!gameOver && !played.isEmpty()) {
            int ok = JOptionPane.showConfirmDialog(dialog, "Leave the game? The current game will be lost.", "Leave",
                    JOptionPane.YES_NO_OPTION);
            if (ok != JOptionPane.YES_OPTION) return;
        }
        epoch++;
        dialog.dispose();
    }

    private void openSettings() {
        JDialog d = new JDialog(dialog, "Settings", true);
        d.setUndecorated(true);
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(PANEL_BG);
        p.setBorder(new CompoundBorder(new LineBorder(Page.GOLD, 2, true), new EmptyBorder(18, 24, 18, 24)));

        JLabel t = new JLabel("SETTINGS");
        t.setFont(new Font(RoyalGambit.SANS, Font.BOLD, 20));
        t.setForeground(Page.GOLD_LIGHT);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        t.setBorder(new EmptyBorder(0, 0, 10, 0));
        p.add(t);
        p.add(Box.createRigidArea(new Dimension(320, 0)));

        p.add(check("Show legal move hints", setHints, v -> { setHints = v; view.repaint(); }));
        p.add(check("Show board coordinates", setCoords, v -> { setCoords = v; view.repaint(); }));
        p.add(check("Highlight last move", setLastMove, v -> { setLastMove = v; view.repaint(); }));
        if (mode == Mode.LOCAL)
            p.add(check("Auto-flip board each turn", setAutoFlip, v -> {
                setAutoFlip = v;
                if (v) flipped = !pos.white;
                refresh();
            }));

        p.add(check("Sound effects", SoundFx.enabled, v -> { SoundFx.enabled = v; if (v) SoundFx.play(SoundFx.Type.MOVE); }));
        JLabel vl = new JLabel("Sound volume");
        vl.setFont(new Font(RoyalGambit.SANS, Font.BOLD, 15));
        vl.setForeground(TEXT);
        vl.setAlignmentX(Component.LEFT_ALIGNMENT);
        vl.setBorder(new EmptyBorder(6, 0, 0, 0));
        p.add(vl);
        JSlider vol = new JSlider(0, 100, Math.round(SoundFx.volume * 100));
        vol.setOpaque(false);
        vol.setAlignmentX(Component.LEFT_ALIGNMENT);
        vol.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        vol.addChangeListener(e -> {
            SoundFx.volume = vol.getValue() / 100f;
            if (!vol.getValueIsAdjusting()) {
                Page.putI("settings.volume", vol.getValue());                // saved, shared with Menu > Settings
                SoundFx.play(SoundFx.Type.MOVE);                              // preview at the new volume
            }
        });
        p.add(vol);

        JLabel tl = new JLabel("Board colours");
        tl.setFont(new Font(RoyalGambit.SANS, Font.BOLD, 15));
        tl.setForeground(TEXT);
        tl.setAlignmentX(Component.LEFT_ALIGNMENT);
        tl.setBorder(new EmptyBorder(10, 0, 4, 0));
        p.add(tl);
        JComboBox<String> theme = new JComboBox<>(THEME_NAMES);
        theme.setSelectedIndex(setTheme);
        theme.setFont(new Font(RoyalGambit.SANS, Font.BOLD, 14));
        theme.setAlignmentX(Component.LEFT_ALIGNMENT);
        theme.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        theme.addActionListener(e -> { setTheme = theme.getSelectedIndex(); view.repaint(); });
        p.add(theme);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(18, 0, 0, 0));
        row.add(new Btn("DONE", false, d::dispose));
        p.add(row);

        d.setContentPane(p);
        d.pack();
        d.setLocationRelativeTo(dialog);
        d.setVisible(true);
    }

    private JCheckBox check(String text, boolean on, java.util.function.Consumer<Boolean> set) {
        JCheckBox c = new JCheckBox(text, on);
        c.setOpaque(false);
        c.setForeground(TEXT);
        c.setFont(new Font(RoyalGambit.SANS, Font.BOLD, 15));
        c.setFocusPainted(false);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setBorder(new EmptyBorder(6, 0, 6, 0));
        c.addActionListener(e -> set.accept(c.isSelected()));
        return c;
    }

    private void flip() { flipped = !flipped; refresh(); }

    /** Shows the engine's suggested move for the side to move (blue squares on the board). */
    private void hint() {
        if (gameOver || busy || !humanTurn() || hintMove != null) return;
        if (hintsLeft <= 0) {
            statusText = "No hints left (" + maxHints() + " per game)";
            refresh();
            return;
        }
        hintsLeft--;
        final Chess.Position snap = pos;
        final int myEpoch = epoch;
        statusText = "Looking for a good move...";
        refresh();
        new SwingWorker<Chess.Move, Void>() {
            @Override protected Chess.Move doInBackground() { return Chess.Ai.choose(snap, 1, new Random()); }
            @Override protected void done() {
                if (closed || myEpoch != epoch) return;
                if (pos != snap) { hintsLeft++; refresh(); return; }        // you moved first: the hint is not used up
                try {
                    Chess.Move m = get();
                    if (m != null) {
                        hintMove = m;
                        statusText = "Hint: " + snap.san(m) + "  (" + hintsLeft + " left)";
                    } else { hintsLeft++; updateStatus(); }
                } catch (Exception ex) { hintsLeft++; updateStatus(); }
                refresh();
            }
        }.execute();
    }

    /** Report the finished game to Page (Stats + Achievements). Result is from player 1's point of view. */
    private void recordResult(String result) {
        String m = mode == Mode.BOT ? "bot" : mode == Mode.LOCAL ? "local" : "global";
        Page.recordGame(m, result);
    }

    private void updateStatus() {
        List<Chess.Move> legal = pos.legal();
        boolean check = pos.inCheck(pos.white);
        String k = pos.key();
        int rep = 0;
        for (String s : keys) if (s.equals(k)) rep++;
        gameOver = true;
        if (legal.isEmpty()) {
            endMessage = check ? "Checkmate! " + (pos.white ? blackName() : whiteName()) + " wins."
                               : "Stalemate - the game is a draw.";
        } else if (pos.insufficient()) endMessage = "Draw - not enough material to checkmate.";
        else if (pos.half >= 100)      endMessage = "Draw by the fifty-move rule.";
        else if (rep >= 3)             endMessage = "Draw by threefold repetition.";
        else { gameOver = false; endMessage = null; }
        statusText = gameOver ? endMessage
                : (pos.white ? "White" : "Black") + " to move" + (check ? " - CHECK!" : "");
    }

    private void play(Chess.Move m) {
        int mover = pos.b[m.from];                      // for the sound effect
        boolean capture = pos.b[m.to] != 0 || (Math.abs(mover) == Chess.P && (m.from & 7) != (m.to & 7));
        boolean castle = Math.abs(mover) == 6 && Math.abs((m.to & 7) - (m.from & 7)) == 2;
        boolean promo = m.promo != 0;
        past.add(pos);
        played.add(m);
        sans.add(pos.san(m));
        pos = pos.make(m);
        keys.add(pos.key());
        last = m;
        hintMove = null;
        sel = -1; selMoves.clear();
        updateStatus();
        refresh();
        playMoveSound(capture, castle, promo);
        if (gameOver) {
            boolean mate = pos.legal().isEmpty() && pos.inCheck(pos.white);
            recordResult(mate ? ((!pos.white) == p1White ? "win" : "loss") : "draw");
            final String msg = endMessage;
            SwingUtilities.invokeLater(() -> Page.info(dialog, msg));
        } else maybeBotMove();
    }

    /** Picks the sound for the move that was just played (called after the new position is set). */
    private void playMoveSound(boolean capture, boolean castle, boolean promo) {
        if (gameOver) {
            boolean mate = pos.legal().isEmpty() && pos.inCheck(pos.white);
            if (!mate) SoundFx.play(SoundFx.Type.DRAW);
            else {
                boolean p1Won = (!pos.white) == p1White;
                SoundFx.play(mode == Mode.LOCAL || p1Won ? SoundFx.Type.WIN : SoundFx.Type.LOSE);
            }
        }
        else if (pos.inCheck(pos.white)) SoundFx.play(SoundFx.Type.CHECK);
        else if (promo)   SoundFx.play(SoundFx.Type.PROMOTE);
        else if (castle)  SoundFx.play(SoundFx.Type.CASTLE);
        else if (capture) SoundFx.play(SoundFx.Type.CAPTURE);
        else              SoundFx.play(SoundFx.Type.MOVE);
    }

    private void maybeBotMove() {
        if (gameOver || mode == Mode.LOCAL || humanTurn()) return;
        busy = true;
        statusText = p2Name + " is thinking...";
        refresh();
        final Chess.Position snap = pos;
        final int myEpoch = ++epoch;
        new SwingWorker<Chess.Move, Void>() {
            @Override protected Chess.Move doInBackground() throws Exception {
                long t0 = System.currentTimeMillis();
                Chess.Move m = Chess.Ai.choose(snap, level, new Random());
                long wait = 600 - (System.currentTimeMillis() - t0);      // small pause so it feels natural
                if (wait > 0) Thread.sleep(wait);
                return m;
            }
            @Override protected void done() {
                if (closed || myEpoch != epoch) return;                    // cancelled by undo / new game / resign
                busy = false;
                try {
                    Chess.Move m = get();
                    if (m != null) play(m);
                } catch (Exception ex) {
                    statusText = "The bot could not move.";
                    refresh();
                }
            }
        }.execute();
    }

    // =====================================================================
    //  Input
    // =====================================================================
    private void click(int s) {
        if (gameOver || busy || !humanTurn()) return;
        if (sel >= 0) {
            List<Chess.Move> cand = new ArrayList<>();
            for (Chess.Move m : selMoves) if (m.to == s) cand.add(m);
            if (!cand.isEmpty()) {
                Chess.Move m = cand.size() > 1 ? askPromotion(cand) : cand.get(0);
                if (m != null) play(m);
                return;
            }
        }
        int piece = pos.b[s];
        selMoves.clear();
        if (piece != 0 && (piece > 0) == pos.white) {
            sel = s;
            for (Chess.Move m : pos.legal()) if (m.from == s) selMoves.add(m);
        } else sel = -1;
        view.repaint();
    }

    private Chess.Move askPromotion(List<Chess.Move> cand) {
        String[] names = { "Queen", "Rook", "Bishop", "Knight" };
        int[] codes = { Chess.Q, Chess.R, Chess.B, Chess.N };
        int pick = JOptionPane.showOptionDialog(dialog, "Promote your pawn to:", "Promotion",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, names, names[0]);
        if (pick < 0) return null;
        for (Chess.Move m : cand) if (m.promo == codes[pick]) return m;
        return cand.get(0);
    }

    // =====================================================================
    //  Drop-down menu item (gold highlight on hover)
    // =====================================================================
    private static class NavItem extends JMenuItem {
        private final Color fg;

        NavItem(String text, Color fg, Runnable action) {
            super(text);
            this.fg = fg;
            setOpaque(false);
            setFont(new Font(RoyalGambit.SANS, Font.BOLD, 15));
            setBorder(new EmptyBorder(0, 16, 0, 14));
            setHorizontalAlignment(SwingConstants.LEFT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addActionListener(e -> action.run());
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            RoyalGambit.aa(g);
            if (isEnabled() && (isArmed() || getModel().isRollover())) {
                g.setColor(alpha(Page.GOLD, 80));
                g.fill(new RoundRectangle2D.Float(3, 2, getWidth() - 6, getHeight() - 4, 10, 10));
            }
            g.dispose();
            setForeground(isEnabled() ? fg : new Color(0x7C7668));
            super.paintComponent(g0);
        }
    }

    // =====================================================================
    //  Button: high-contrast text, hover / pressed / disabled states
    // =====================================================================
    private static class Btn extends JComponent {
        private String text;
        private final boolean danger;
        private boolean hover, press;
        boolean burger;                         // draws a three-line menu icon

        Btn(String text, boolean danger, Runnable action) {
            this.text = text;
            this.danger = danger;
            setPreferredSize(new Dimension(150, 46));
            setMinimumSize(new Dimension(100, 40));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; press = false; repaint(); }
                @Override public void mousePressed(MouseEvent e) { if (isEnabled()) { press = true; repaint(); } }
                @Override public void mouseReleased(MouseEvent e) {
                    boolean fire = press && isEnabled() && contains(e.getPoint());
                    press = false;
                    repaint();
                    if (fire) action.run();
                }
            });
        }

        void setText(String t) { text = t; repaint(); }

        @Override public void setEnabled(boolean on) {
            super.setEnabled(on);
            setCursor(Cursor.getPredefinedCursor(on ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            repaint();
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            RoyalGambit.aa(g);
            int w = getWidth(), h = getHeight();
            boolean on = isEnabled();

            Color base = danger ? BTN_RED : BTN_BLUE;
            if (!on) base = new Color(0x2A2F3F);
            else if (press) base = base.darker();
            else if (hover) base = base.brighter();

            Shape s = new RoundRectangle2D.Float(1, 1, w - 3, h - 3, 14, 14);
            g.setPaint(new GradientPaint(0, 0, base.brighter(), 0, h, base.darker()));
            g.fill(s);
            g.setColor(new Color(255, 255, 255, on ? 55 : 20));                 // soft top highlight
            g.fill(new RoundRectangle2D.Float(4, 3, w - 9, h / 2f - 3, 10, 10));
            g.setColor(!on ? alpha(Page.GOLD, 70) : hover ? Page.GOLD_LIGHT : Page.GOLD);
            g.setStroke(new BasicStroke(2f));
            g.draw(s);

            g.setFont(new Font(RoyalGambit.SANS, Font.BOLD, 15));
            FontMetrics fm = g.getFontMetrics();
            int group = burger ? 18 + 10 + fm.stringWidth(text) : fm.stringWidth(text);
            int start = (w - group) / 2;
            int tx = burger ? start + 28 : start;
            int ty = (h - fm.getHeight()) / 2 + fm.getAscent() + (press ? 1 : 0);
            if (burger) {
                g.setColor(on ? Color.WHITE : new Color(0x8A8272));
                g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int bx = start, by = h / 2;
                for (int i = -1; i <= 1; i++) g.drawLine(bx, by + i * 6, bx + 18, by + i * 6);
            }
            shadowText(g, text, tx, ty, on ? Color.WHITE : new Color(0x8A8272));
            g.dispose();
        }
    }

    // =====================================================================
    //  Status banner: normal / check (red) / game over (gold)
    // =====================================================================
    private static class StatusBar extends JComponent {
        private String text = " ";
        private int kind;

        StatusBar() {
            setPreferredSize(new Dimension(300, 46));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        }

        void set(String t, int k) { text = t == null ? " " : t; kind = k; repaint(); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            RoyalGambit.aa(g);
            int w = getWidth(), h = getHeight();
            Color fill = kind == 1 ? new Color(0x7A1F1F) : kind == 2 ? new Color(0x5E4A12) : PANEL_BG;
            Color edge = kind == 1 ? new Color(0xFF8A80) : Page.GOLD;
            Shape s = new RoundRectangle2D.Float(1, 1, w - 3, h - 3, 16, 16);
            g.setColor(alpha(fill, 225));
            g.fill(s);
            g.setColor(edge);
            g.setStroke(new BasicStroke(2f));
            g.draw(s);

            int size = 16;
            Font f = new Font(RoyalGambit.SANS, Font.BOLD, size);
            FontMetrics fm = g.getFontMetrics(f);
            while (size > 11 && fm.stringWidth(text) > w - 24) {                // shrink long messages to fit
                f = new Font(RoyalGambit.SANS, Font.BOLD, --size);
                fm = g.getFontMetrics(f);
            }
            g.setFont(f);
            shadowText(g, text, (w - fm.stringWidth(text)) / 2, (h - fm.getHeight()) / 2 + fm.getAscent(),
                    kind == 0 ? TEXT : Color.WHITE);
            g.dispose();
        }
    }

    // =====================================================================
    //  Board component
    // =====================================================================
    private class BoardView extends JComponent {
        int sq, ox, oy;

        BoardView() {
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    int s = squareAt(e.getX(), e.getY());
                    if (s >= 0) click(s);
                }
            });
        }

        void geom() {
            sq = Math.min(getWidth(), getHeight()) / 8;
            ox = (getWidth() - sq * 8) / 2;
            oy = (getHeight() - sq * 8) / 2;
        }

        int toSq(int dr, int dc) { return flipped ? (7 - dr) * 8 + (7 - dc) : dr * 8 + dc; }
        int dispRow(int s) { return flipped ? 7 - (s >> 3) : s >> 3; }
        int dispCol(int s) { return flipped ? 7 - (s & 7) : s & 7; }

        int squareAt(int x, int y) {
            geom();
            if (sq <= 0) return -1;
            int dc = Math.floorDiv(x - ox, sq), dr = Math.floorDiv(y - oy, sq);
            if (dc < 0 || dc > 7 || dr < 0 || dr > 7) return -1;
            return toSq(dr, dc);
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            RoyalGambit.aa(g);
            geom();
            if (sq <= 0) { g.dispose(); return; }
            int check = pos.inCheck(pos.white) ? pos.kingSquare(pos.white) : -1;

            for (int dr = 0; dr < 8; dr++)
                for (int dc = 0; dc < 8; dc++) {
                    int s = toSq(dr, dc), x = ox + dc * sq, y = oy + dr * sq;
                    g.setColor((dr + dc) % 2 == 0 ? themeLight() : themeDark());
                    g.fillRect(x, y, sq, sq);
                    if (setLastMove && last != null && (s == last.from || s == last.to)) {
                        g.setColor(alpha(Page.GOLD, 110));
                        g.fillRect(x, y, sq, sq);
                    }
                    if (hintMove != null && (s == hintMove.from || s == hintMove.to)) {
                        g.setColor(new Color(70, 150, 255, 150));
                        g.fillRect(x, y, sq, sq);
                    }
                    if (s == sel) {
                        g.setColor(alpha(Page.GOLD, 170));
                        g.fillRect(x, y, sq, sq);
                    }
                    if (s == check) {
                        g.setPaint(new RadialGradientPaint(new Point2D.Float(x + sq / 2f, y + sq / 2f), sq * 0.6f,
                                new float[] { 0f, 1f },
                                new Color[] { new Color(220, 40, 40, 210), new Color(220, 40, 40, 0) }));
                        g.fillRect(x, y, sq, sq);
                    }
                }

            // coordinates: larger and bold, in the opposite shade of the square they sit on
            g.setFont(new Font(RoyalGambit.SANS, Font.BOLD, Math.max(11, sq / 4)));
            FontMetrics fm = g.getFontMetrics();
            for (int i = 0; setCoords && i < 8; i++) {
                String file = String.valueOf((char) ('a' + (toSq(7, i) & 7)));
                g.setColor((7 + i) % 2 == 0 ? themeDark().darker() : themeLight().darker());
                g.drawString(file, ox + i * sq + sq - fm.stringWidth(file) - 4, oy + 8 * sq - 4);
                g.setColor(i % 2 == 0 ? themeDark().darker() : themeLight().darker());
                g.drawString(String.valueOf(8 - (toSq(i, 0) >> 3)), ox + 4, oy + i * sq + fm.getAscent() + 2);
            }

            // pieces - drawn by PieceArt (distinct silhouettes)
            for (int s = 0; s < 64; s++)
                if (pos.b[s] != 0)
                    PieceArt.draw(g, code(pos.b[s]), ox + dispCol(s) * sq, oy + dispRow(s) * sq, sq);

            boolean[] done = new boolean[64];
            for (Chess.Move m : selMoves) {
                if (!setHints) break;
                if (done[m.to]) continue;
                done[m.to] = true;
                boolean cap = pos.b[m.to] != 0
                        || (Math.abs(pos.b[m.from]) == Chess.P && (m.from & 7) != (m.to & 7));
                int x = ox + dispCol(m.to) * sq, y = oy + dispRow(m.to) * sq;
                if (cap) {
                    g.setColor(new Color(0xE05050));
                    g.setStroke(new BasicStroke(Math.max(3f, sq / 14f)));
                    g.draw(new Ellipse2D.Double(x + sq * 0.08, y + sq * 0.08, sq * 0.84, sq * 0.84));
                } else {
                    g.setColor(new Color(47, 168, 90, 220));
                    g.fill(new Ellipse2D.Double(x + sq * 0.34, y + sq * 0.34, sq * 0.32, sq * 0.32));
                }
            }

            g.setColor(Page.GOLD);
            g.setStroke(new BasicStroke(3f));
            g.drawRect(ox - 1, oy - 1, sq * 8 + 2, sq * 8 + 2);
            g.dispose();
        }
    }

    // =====================================================================
    //  Player card: avatar, name, colour + turn, captured pieces, material lead
    // =====================================================================
    private class PlayerCard extends JComponent {
        boolean whiteSide;

        PlayerCard() {
            setPreferredSize(new Dimension(300, 92));
            setMinimumSize(new Dimension(220, 92));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 92));
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            RoyalGambit.aa(g);
            int w = getWidth(), h = getHeight();
            boolean turn = !gameOver && pos.white == whiteSide;

            Shape s = new RoundRectangle2D.Float(1, 1, w - 3, h - 3, 16, 16);
            g.setColor(alpha(PANEL_BG, 215));
            g.fill(s);
            if (turn) { g.setColor(alpha(Page.GOLD, 45)); g.fill(s); }
            g.setColor(turn ? Page.GOLD_LIGHT : alpha(Page.GOLD, 130));
            g.setStroke(new BasicStroke(turn ? 2.4f : 1.4f));
            g.draw(s);

            int d = h - 22, tx = d + 26;
            Page.drawAvatar(g, avatar(whiteSide), 12, 11, d);

            // name
            g.setFont(new Font(RoyalGambit.SANS, Font.BOLD, 17));
            String name = nameOf(whiteSide);
            FontMetrics fm = g.getFontMetrics();
            int maxW = w - tx - 40;
            while (name.length() > 3 && fm.stringWidth(name) > maxW) name = name.substring(0, name.length() - 4) + "...";
            shadowText(g, name, tx, 30, TEXT);

            // colour chip
            Ellipse2D chip = new Ellipse2D.Double(w - 28, 16, 14, 14);
            g.setColor(whiteSide ? new Color(0xFFF8E6) : new Color(0x1E1913));
            g.fill(chip);
            g.setColor(Page.GOLD);
            g.setStroke(new BasicStroke(1.4f));
            g.draw(chip);

            // colour + turn line
            g.setFont(new Font(RoyalGambit.SANS, Font.BOLD, 13));
            String sub = (whiteSide ? "White" : "Black") + (turn ? "   \u25CF  to move" : "");
            shadowText(g, sub, tx, 50, turn ? Page.GOLD_LIGHT : TEXT_DIM);

            // captured pieces (opponent pieces that are missing) and material lead
            int[] init = { 0, 8, 2, 2, 2, 1 }, present = new int[6];
            int mine = 0, theirs = 0;
            for (int v : pos.b) {
                int t = Math.abs(v);
                if (v == 0 || t > Chess.Q) continue;
                if ((v > 0) == whiteSide) mine += Chess.VALUE[t]; else { theirs += Chess.VALUE[t]; present[t]++; }
            }
            int x = tx;
            for (int t = Chess.Q; t >= Chess.P; t--) {
                for (int k = 0; k < Math.max(0, init[t] - present[t]); k++) {
                    PieceArt.draw(g, !whiteSide, t, x, 57, 26);        // captured pieces are the opponent's colour
                    x += 16;
                }
                x += 3;
            }
            int lead = (mine - theirs) / 100;
            if (lead > 0) {
                String txt = "+" + lead;
                g.setFont(new Font(RoyalGambit.SANS, Font.BOLD, 14));
                FontMetrics f2 = g.getFontMetrics();
                int pw = f2.stringWidth(txt) + 14;
                g.setColor(alpha(Page.GOLD, 70));
                g.fill(new RoundRectangle2D.Float(x + 4, 62, pw, 22, 11, 11));
                g.setColor(Page.GOLD_LIGHT);
                g.drawString(txt, x + 11, 78);
            }
            g.dispose();
        }
    }
}