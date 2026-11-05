import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ROYAL GAMBIT - playable board for the CHESS PUZZLE stages.
 * Keep this file next to RoyalGambit.java and Page.java.
 *
 *   javac RoyalGambit.java Page.java PuzzleBoard.java GameBoard.java && java Page
 *
 * How it works:
 *   - Page.onStartPuzzle(parent, stage) calls PuzzleBoard.open(parent, stage).
 *   - Click a White piece, then click a highlighted square to move it.
 *   - Checkmate puzzles accept ANY move that really forces mate in the required number of moves
 *     (the engine checks it), and the Black bot answers with a legal defence.
 *   - Tactic puzzles (fork, pin, skewer, promotion) follow a scripted solution line.
 *   - Stars: 3 = no mistakes and no hints, 2 = up to 2 mistakes/hints combined, 1 = more than that.
 *   - Solving a stage calls Page.completePuzzle(stage, stars), which unlocks the next stage.
 */
public class PuzzleBoard {

    // =====================================================================
    //  Puzzle data (all positions have White to move, White plays up the board)
    // =====================================================================
    static class Puzzle {
        final String fen;
        final int mateIn;        // > 0: "checkmate in N" puzzle, solved by search. 0: scripted line.
        final String[] line;     // scripted line: White, Black, White, ... (e.g. "d5c7")
        Puzzle(String fen, int mateIn, String... line) { this.fen = fen; this.mateIn = mateIn; this.line = line; }
    }

    static final Puzzle[] PUZZLES = {
        // 1 The First Strike: Scholar's mate, Qxf7#
        new Puzzle("r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4", 1),
        // 2 Back-Rank Blunder: Rd8#
        new Puzzle("6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1", 1),
        // 3 Knight's Fork: Nc7+ forks king and queen, then Nxa8
        new Puzzle("q3k3/8/8/3N4/5B2/8/8/4K3 w - - 0 1", 0, "d5c7", "e8d8", "c7a8"),
        // 4 Pinned Down: d5 attacks the pinned knight, dxe6 wins it
        new Puzzle("4k3/8/4n3/8/3P4/8/8/4R1K1 w - - 0 1", 0, "d4d5", "e8f8", "d5e6"),
        // 5 Skewer: Rh4+ drives the king off the rank, Rxa4 wins the rook
        new Puzzle("8/8/8/8/r3k3/8/8/6KR w - - 0 1", 0, "h1h4", "e4d5", "h4a4"),
        // 6 Double Trouble: ladder mate in 2
        new Puzzle("6k1/8/8/8/8/8/R7/1R4K1 w - - 0 1", 2),
        // 7 Deflection: Rd8+! drags the rook away from the back rank, then Qxd8#
        new Puzzle("2r3k1/5ppp/1Q6/8/8/8/5PPP/3R2K1 w - - 0 1", 2),
        // 8 Sacrifice for Glory: Qg8+! Rxg8 Nf7# (smothered mate)
        new Puzzle("5r1k/6pp/7N/8/8/1Q6/8/6K1 w - - 0 1", 2),
        // 9 Endgame Master: bxa8=Q captures the rook and promotes
        new Puzzle("r6k/1P6/8/8/8/8/8/K7 w - - 0 1", 0, "b7a8"),
        // 10 Royal Finale: mate in 3
        new Puzzle("8/5k2/8/8/8/8/K1R1Q3/8 w - - 0 1", 3),
    };

    /** Entry point used by Page.onStartPuzzle. */
    static void open(Component parent, int stage) {
        new Game(parent, stage).show();
    }

    // =====================================================================
    //  Tiny chess engine: legal moves, check, checkmate (no castling / en passant needed here,
    //  en passant is supported; promotion is always to a queen)
    // =====================================================================
    static class Chess {
        final char[] b = new char[64];     // index = row*8+col, row 0 = rank 8; uppercase = White
        boolean white = true;
        int ep = -1;

        static final int[][] KN = { {1,2},{2,1},{-1,2},{-2,1},{1,-2},{2,-1},{-1,-2},{-2,-1} };
        static final int[][] KG = { {1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1} };
        static final int[][] ST = { {1,0},{-1,0},{0,1},{0,-1} };
        static final int[][] DI = { {1,1},{1,-1},{-1,1},{-1,-1} };

        static boolean in(int r, int c) { return r >= 0 && r < 8 && c >= 0 && c < 8; }
        static boolean isWhite(char p) { return Character.isUpperCase(p); }
        static String name(int sq) { return "" + (char) ('a' + sq % 8) + (8 - sq / 8); }

        static Chess fromFen(String fen) {
            Chess c = new Chess();
            String[] parts = fen.split(" ");
            int sq = 0;
            for (char ch : parts[0].toCharArray()) {
                if (ch == '/') continue;
                if (Character.isDigit(ch)) for (int i = 0; i < ch - '0'; i++) c.b[sq++] = '.';
                else c.b[sq++] = ch;
            }
            c.white = parts.length < 2 || parts[1].equals("w");
            return c;
        }

        Chess copy() {
            Chess c = new Chess();
            System.arraycopy(b, 0, c.b, 0, 64);
            c.white = white; c.ep = ep;
            return c;
        }

        int kingSq(boolean w) {
            char k = w ? 'K' : 'k';
            for (int i = 0; i < 64; i++) if (b[i] == k) return i;
            return -1;
        }

        boolean attacked(int sq, boolean byWhite) {
            int r = sq / 8, c = sq % 8;
            int pr = byWhite ? r + 1 : r - 1;
            char pawn = byWhite ? 'P' : 'p';
            for (int dc = -1; dc <= 1; dc += 2)
                if (in(pr, c + dc) && b[pr * 8 + c + dc] == pawn) return true;
            char kn = byWhite ? 'N' : 'n', ki = byWhite ? 'K' : 'k';
            for (int[] d : KN) if (in(r + d[0], c + d[1]) && b[(r + d[0]) * 8 + c + d[1]] == kn) return true;
            for (int[] d : KG) if (in(r + d[0], c + d[1]) && b[(r + d[0]) * 8 + c + d[1]] == ki) return true;
            char rk = byWhite ? 'R' : 'r', bi = byWhite ? 'B' : 'b', qu = byWhite ? 'Q' : 'q';
            for (int pass = 0; pass < 2; pass++) {
                int[][] dirs = pass == 0 ? ST : DI;
                char slider = pass == 0 ? rk : bi;
                for (int[] d : dirs) {
                    int rr = r + d[0], cc = c + d[1];
                    while (in(rr, cc)) {
                        char p = b[rr * 8 + cc];
                        if (p != '.') { if (p == slider || p == qu) return true; break; }
                        rr += d[0]; cc += d[1];
                    }
                }
            }
            return false;
        }

        boolean inCheck(boolean w) {
            int k = kingSq(w);
            return k >= 0 && attacked(k, !w);
        }

        List<int[]> pseudo() {
            List<int[]> out = new ArrayList<>();
            for (int s = 0; s < 64; s++) {
                char p = b[s];
                if (p == '.' || isWhite(p) != white) continue;
                int r = s / 8, c = s % 8;
                char t = Character.toUpperCase(p);
                if (t == 'P') {
                    int dir = white ? -1 : 1, start = white ? 6 : 1, r1 = r + dir;
                    if (in(r1, c) && b[r1 * 8 + c] == '.') {
                        out.add(new int[] { s, r1 * 8 + c });
                        int r2 = r + 2 * dir;
                        if (r == start && b[r2 * 8 + c] == '.') out.add(new int[] { s, r2 * 8 + c });
                    }
                    for (int dc = -1; dc <= 1; dc += 2) {
                        int cc = c + dc;
                        if (!in(r1, cc)) continue;
                        int to = r1 * 8 + cc;
                        if ((b[to] != '.' && isWhite(b[to]) != white) || to == ep) out.add(new int[] { s, to });
                    }
                } else if (t == 'N' || t == 'K') {
                    for (int[] d : t == 'N' ? KN : KG) {
                        int rr = r + d[0], cc = c + d[1];
                        if (in(rr, cc) && (b[rr * 8 + cc] == '.' || isWhite(b[rr * 8 + cc]) != white))
                            out.add(new int[] { s, rr * 8 + cc });
                    }
                } else {
                    for (int pass = 0; pass < 2; pass++) {
                        if (pass == 0 && t == 'B') continue;
                        if (pass == 1 && t == 'R') continue;
                        for (int[] d : pass == 0 ? ST : DI) {
                            int rr = r + d[0], cc = c + d[1];
                            while (in(rr, cc)) {
                                char q = b[rr * 8 + cc];
                                if (q == '.') out.add(new int[] { s, rr * 8 + cc });
                                else { if (isWhite(q) != white) out.add(new int[] { s, rr * 8 + cc }); break; }
                                rr += d[0]; cc += d[1];
                            }
                        }
                    }
                }
            }
            return out;
        }

        List<int[]> legal() {
            List<int[]> out = new ArrayList<>();
            for (int[] m : pseudo()) {
                Chess q = copy();
                q.make(m);
                if (!q.inCheck(!q.white)) out.add(m);
            }
            return out;
        }

        void make(int[] m) {
            char p = b[m[0]];
            char t = Character.toUpperCase(p);
            int fr = m[0] / 8, fc = m[0] % 8, tr = m[1] / 8, tc = m[1] % 8;
            if (t == 'P' && m[1] == ep && fc != tc && b[m[1]] == '.') b[fr * 8 + tc] = '.';   // en passant
            int newEp = (t == 'P' && Math.abs(tr - fr) == 2) ? ((fr + tr) / 2) * 8 + fc : -1;
            b[m[1]] = p;
            b[m[0]] = '.';
            if (t == 'P' && (tr == 0 || tr == 7)) b[m[1]] = white ? 'Q' : 'q';                  // promotion
            ep = newEp;
            white = !white;
        }

        boolean isMate() { return inCheck(white) && legal().isEmpty(); }
    }

    // ----- forced-mate search -----
    /** Attacker to move in p: can he force mate within n of his own moves? */
    static boolean matesIn(Chess p, int n) {
        if (n < 1) return false;
        for (int[] m : p.legal()) {
            Chess q = p.copy();
            q.make(m);
            if (defenderLoses(q, n - 1)) return true;
        }
        return false;
    }
    /** Defender to move in q: is he mated now, or does every reply allow mate within k attacker moves? */
    static boolean defenderLoses(Chess q, int k) {
        List<int[]> replies = q.legal();
        if (replies.isEmpty()) return q.inCheck(q.white);
        if (k < 1) return false;
        for (int[] r : replies) {
            Chess z = q.copy();
            z.make(r);
            if (!matesIn(z, k)) return false;
        }
        return true;
    }

    static int sq(String s) { return (8 - (s.charAt(1) - '0')) * 8 + (s.charAt(0) - 'a'); }
    static int[] parse(String uci) { return new int[] { sq(uci.substring(0, 2)), sq(uci.substring(2, 4)) }; }

    static String notate(Chess before, int[] m) {
        char t = Character.toUpperCase(before.b[m[0]]);
        boolean cap = before.b[m[1]] != '.' || (t == 'P' && m[1] == before.ep && m[0] % 8 != m[1] % 8);
        Chess after = before.copy();
        after.make(m);
        String s = (t == 'P' ? "" : "" + t) + Chess.name(m[0]) + (cap ? "x" : "-") + Chess.name(m[1]);
        if (t == 'P' && (m[1] / 8 == 0 || m[1] / 8 == 7)) s += "=Q";
        if (after.inCheck(after.white)) s += after.legal().isEmpty() ? "#" : "+";
        return s;
    }

    // =====================================================================
    //  The puzzle window
    // =====================================================================
    static final Color OKC = new Color(0x7FCB8F), ERRC = new Color(0xE5736A);

    static class Game {
        final Component parent;
        final int stage;
        final Puzzle pz;
        final Page.Stage info;

        Chess pos;
        int idx, mistakes, hints, hintLevel;
        boolean busy, solved;
        final List<String> log = new ArrayList<>();
        javax.swing.Timer timer;
        JDialog dlg;

        final Board board = new Board();
        final JLabel status = new JLabel(" ");
        final JLabel counters = new JLabel(" ");
        final JLabel moves = new JLabel(" ");
        final Stars stars = new Stars();
        JLabel ratingLbl;
        Page.OutlineButton hintBtn, retryBtn;
        RoyalGambit.GoldButton nextBtn;

        Game(Component parent, int stage) {
            this.parent = parent;
            this.stage = stage;
            this.pz = PUZZLES[stage];
            this.info = Page.STAGES[stage];
            this.pos = Chess.fromFen(pz.fen);
        }

        void show() {
            dlg = Page.dialog(parent, "Chess Puzzle", 1000, 740);
            build((JPanel) dlg.getContentPane());
            dlg.addWindowListener(new WindowAdapter() {
                @Override public void windowClosed(WindowEvent e) { if (timer != null) timer.stop(); }
            });
            dlg.setVisible(true);
        }

        static String html(String s) { return "<html><div style='width:270px'>" + s + "</div></html>"; }

        void build(JPanel p) {
            p.add(Page.header("STAGE " + (stage + 1) + ": " + info.title.toUpperCase(), info.goal), BorderLayout.NORTH);

            // ----- board -----
            JPanel boardWrap = new JPanel(new BorderLayout());   // the board scales to the space available
            boardWrap.setOpaque(false);
            boardWrap.add(board, BorderLayout.CENTER);

            // ----- side panel -----
            JPanel side = new JPanel();
            side.setOpaque(false);
            side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
            side.setPreferredSize(new Dimension(320, 480));

            JLabel who = Page.lbl("WHITE TO MOVE", 15, Font.BOLD, Page.GOLD, RoyalGambit.SANS);
            JLabel goal = new JLabel(html(info.goal));
            goal.setFont(new Font(RoyalGambit.SANS, Font.PLAIN, 15));
            goal.setForeground(Page.CREAM);

            status.setFont(new Font(RoyalGambit.SANS, Font.BOLD, 15));
            counters.setFont(new Font(RoyalGambit.SANS, Font.PLAIN, 13));
            counters.setForeground(Page.MUTED);
            moves.setFont(new Font(RoyalGambit.SANS, Font.PLAIN, 13));
            moves.setForeground(Page.CREAM);
            ratingLbl = Page.lbl("RATING IF SOLVED NOW", 11, Font.BOLD, Page.MUTED, RoyalGambit.SANS);
            JLabel movesLbl = Page.lbl("MOVES", 11, Font.BOLD, Page.MUTED, RoyalGambit.SANS);

            for (JComponent c : new JComponent[] { who, goal, status, counters, ratingLbl, stars, movesLbl, moves }) {
                c.setAlignmentX(Component.LEFT_ALIGNMENT);
            }
            side.add(who);
            side.add(Box.createVerticalStrut(8));
            side.add(goal);
            side.add(Box.createVerticalStrut(18));
            side.add(status);
            side.add(Box.createVerticalStrut(10));
            side.add(counters);
            side.add(Box.createVerticalStrut(18));
            side.add(ratingLbl);
            side.add(Box.createVerticalStrut(6));
            side.add(stars);
            side.add(Box.createVerticalStrut(18));
            side.add(movesLbl);
            side.add(Box.createVerticalStrut(6));
            side.add(moves);
            side.add(Box.createVerticalGlue());

            JPanel center = new JPanel(new BorderLayout(20, 0));
            center.setOpaque(false);
            center.add(boardWrap, BorderLayout.CENTER);
            center.add(side, BorderLayout.EAST);
            p.add(center, BorderLayout.CENTER);

            // ----- buttons -----
            hintBtn = Page.outline("HINT", 110, 38, 13, this::hint);
            retryBtn = Page.outline("RETRY", 110, 38, 13, this::retry);
            nextBtn = Page.gold("NEXT STAGE", 170, 38, this::nextStage);
            nextBtn.setVisible(false);
            Page.OutlineButton close = Page.outline("CLOSE", 110, 38, 13, () -> { if (dlg != null) dlg.dispose(); });

            JPanel south = new JPanel(new BorderLayout());
            south.setOpaque(false);
            south.setBorder(new EmptyBorder(12, 0, 0, 0));
            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            left.setOpaque(false);
            left.add(hintBtn);
            left.add(retryBtn);
            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            right.setOpaque(false);
            right.add(nextBtn);
            right.add(close);
            south.add(left, BorderLayout.WEST);
            south.add(right, BorderLayout.EAST);
            p.add(south, BorderLayout.SOUTH);

            setStatus("Your move. Click a piece, then a highlighted square.", Page.CREAM);
            refresh();
        }

        // ----- state helpers -----
        int preview() {
            int penalty = mistakes + hints;
            return penalty == 0 ? 3 : penalty <= 2 ? 2 : 1;
        }

        void setStatus(String t, Color c) {
            status.setText(html(t));
            status.setForeground(c);
        }

        void refresh() {
            counters.setText("Mistakes: " + mistakes + "     Hints used: " + hints);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < log.size(); i++) {
                if (i % 2 == 0) sb.append(i / 2 + 1).append(". ");
                sb.append(log.get(i)).append("  ");
            }
            moves.setText(html(sb.length() == 0 ? "-" : sb.toString()));
            stars.repaint();
            board.repaint();
        }

        // ----- interaction -----
        void onClick(int s) {
            if (busy || solved) return;
            char p = pos.b[s];
            if (board.sel >= 0 && board.targets.contains(s)) { attempt(board.sel, s); return; }
            if (p != '.' && Chess.isWhite(p) == pos.white) board.select(s);
            else board.select(-1);
        }

        void attempt(int from, int to) {
            int[] m = { from, to };
            Chess q = pos.copy();
            q.make(m);
            boolean ok;
            if (pz.mateIn == 0) {
                int[] e = parse(pz.line[idx * 2]);
                ok = e[0] == from && e[1] == to;
            } else {
                ok = defenderLoses(q, pz.mateIn - idx - 1);
            }
            board.select(-1);
            if (!ok) {
                mistakes++;
                board.flashWrong(from, to);
                setStatus("Not quite - that move does not solve it. Try again!", ERRC);
                refresh();
                return;
            }
            log.add(notate(pos, m));
            board.lastFrom = from; board.lastTo = to;
            board.hintFrom = board.hintTo = -1;
            pos = q;
            int i = idx;
            idx++;
            hintLevel = 0;
            boolean done = pz.mateIn == 0 ? (2 * i + 1 >= pz.line.length) : q.isMate();
            if (done) { finish(); return; }
            busy = true;
            setStatus("Good move! Black replies...", OKC);
            refresh();
            timer = new javax.swing.Timer(650, e -> opponentReply());
            timer.setRepeats(false);
            timer.start();
        }

        void opponentReply() {
            int[] r;
            if (pz.mateIn == 0) {
                r = parse(pz.line[2 * (idx - 1) + 1]);
            } else {
                r = chooseDefence();
            }
            log.add(notate(pos, r));
            pos.make(r);
            board.lastFrom = r[0]; board.lastTo = r[1];
            busy = false;
            setStatus(pos.inCheck(pos.white) ? "Check! Find the best continuation." : "Your move.", Page.CREAM);
            refresh();
        }

        /** Black picks the toughest legal defence (the one that delays mate longest). */
        int[] chooseDefence() {
            int rem = pz.mateIn - idx;
            List<int[]> replies = pos.legal();
            for (int[] r : replies) {
                Chess z = pos.copy();
                z.make(r);
                if (rem - 1 >= 1 && !matesIn(z, rem - 1)) return r;
            }
            return replies.get(0);
        }

        void finish() {
            solved = true;
            busy = false;
            boolean allBefore = Page.completedCount() == Page.STAGES.length;
            int s = preview();
            Page.completePuzzle(stage, s);
            refresh();
            hintBtn.setEnabled(false);
            hintBtn.repaint();
            ratingLbl.setText("YOUR RATING");
            String praise = s == 3 ? "Perfect!" : s == 2 ? "Well done!" : "Solved!";
            setStatus(praise + " You earned " + s + (s == 1 ? " star." : " stars."), OKC);
            boolean last = stage + 1 >= Page.STAGES.length;
            nextBtn.setVisible(!last);
            if (!allBefore && Page.completedCount() == Page.STAGES.length) {
                final Component owner = dlg != null ? dlg : parent;
                SwingUtilities.invokeLater(() -> Page.info(owner, "Congratulations! You completed all 10 stages!"));
            }
        }

        int[] hintMove() {
            if (pz.mateIn == 0) return parse(pz.line[idx * 2]);
            int rem = pz.mateIn - idx;
            for (int[] m : pos.legal()) {
                Chess q = pos.copy();
                q.make(m);
                if (defenderLoses(q, rem - 1)) return m;
            }
            return null;
        }

        void hint() {
            if (busy || solved) return;
            int[] m = hintMove();
            if (m == null) return;
            if (hintLevel < 2) { hintLevel++; hints++; }
            board.hintFrom = m[0];
            board.hintTo = hintLevel >= 2 ? m[1] : -1;
            setStatus(hintLevel >= 2 ? "Hint: move the marked piece to the marked square."
                                     : "Hint: " + info.hint + " The piece to move is marked. Press HINT again to see the square.",
                      Page.GOLD_LIGHT);
            refresh();
        }

        void retry() {
            if (timer != null) timer.stop();
            pos = Chess.fromFen(pz.fen);
            idx = mistakes = hints = hintLevel = 0;
            busy = solved = false;
            log.clear();
            board.reset();
            ratingLbl.setText("RATING IF SOLVED NOW");
            nextBtn.setVisible(false);
            hintBtn.setEnabled(true);
            hintBtn.repaint();
            setStatus("Your move. Click a piece, then a highlighted square.", Page.CREAM);
            refresh();
        }

        void nextStage() {
            if (dlg != null) dlg.dispose();
            final int n = stage + 1;
            if (n < Page.STAGES.length) SwingUtilities.invokeLater(() -> Page.onStartPuzzle(parent, n));
        }

        // =================================================================
        //  Star rating
        // =================================================================
        class Stars extends JComponent {
            Stars() { RoyalGambit.fix(this, 130, 28); }
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                RoyalGambit.aa(g);
                int n = preview();
                for (int i = 0; i < 3; i++) {
                    Shape st = Page.starShape(14 + i * 38, 14, 13);
                    if (i < n) { g.setColor(Page.GOLD); g.fill(st); }
                    else {
                        g.setColor(new Color(Page.MUTED.getRed(), Page.MUTED.getGreen(), Page.MUTED.getBlue(), 160));
                        g.setStroke(new BasicStroke(1.3f));
                        g.draw(st);
                    }
                }
                g.dispose();
            }
        }

        // =================================================================
        //  The board
        // =================================================================
        class Board extends JComponent {
            int sel = -1, lastFrom = -1, lastTo = -1, hintFrom = -1, hintTo = -1, wrongFrom = -1, wrongTo = -1;
            final Set<Integer> targets = new HashSet<>();

            Board() {
                setPreferredSize(new Dimension(520, 520));
                setMinimumSize(new Dimension(240, 240));
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) {
                        int s = hit(e.getX(), e.getY());
                        if (s >= 0) onClick(s);
                    }
                });
                addMouseMotionListener(new MouseMotionAdapter() {
                    @Override public void mouseMoved(MouseEvent e) {
                        int s = hit(e.getX(), e.getY());
                        boolean hand = !busy && !solved && s >= 0 && (targets.contains(s)
                                || (pos.b[s] != '.' && Chess.isWhite(pos.b[s]) == pos.white));
                        setCursor(Cursor.getPredefinedCursor(hand ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                    }
                });
            }

            void reset() {
                sel = lastFrom = lastTo = hintFrom = hintTo = wrongFrom = wrongTo = -1;
                targets.clear();
                repaint();
            }

            void select(int s) {
                sel = s;
                targets.clear();
                if (s >= 0) for (int[] m : pos.legal()) if (m[0] == s) targets.add(m[1]);
                if (targets.isEmpty()) sel = s >= 0 && pos.b[s] != '.' ? s : -1;
                repaint();
            }

            void flashWrong(int f, int t) {
                wrongFrom = f; wrongTo = t;
                repaint();
                javax.swing.Timer w = new javax.swing.Timer(700, e -> { wrongFrom = wrongTo = -1; repaint(); });
                w.setRepeats(false);
                w.start();
            }

            int[] geom() {
                int s = Math.min(getWidth(), getHeight()) / 8;
                return new int[] { s, (getWidth() - s * 8) / 2, (getHeight() - s * 8) / 2 };
            }

            int hit(int mx, int my) {
                int[] gm = geom();
                if (mx < gm[1] || my < gm[2]) return -1;
                int c = (mx - gm[1]) / gm[0], r = (my - gm[2]) / gm[0];
                return c > 7 || r > 7 ? -1 : r * 8 + c;
            }

            void tint(Graphics2D g, int s, Color c, int ox, int oy, int sz) {
                if (s < 0) return;
                g.setColor(c);
                g.fillRect(ox + (s % 8) * sz, oy + (s / 8) * sz, sz, sz);
            }

            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                RoyalGambit.aa(g);
                int[] gm = geom();
                int sz = gm[0], ox = gm[1], oy = gm[2];

                for (int r = 0; r < 8; r++)
                    for (int c = 0; c < 8; c++) {
                        g.setColor((r + c) % 2 == 0 ? Page.TutorialBoard.LIGHT : Page.TutorialBoard.DARK);
                        g.fillRect(ox + c * sz, oy + r * sz, sz, sz);
                    }

                // coordinates
                g.setFont(new Font(RoyalGambit.SANS, Font.BOLD, Math.max(9, sz / 5)));
                FontMetrics fm = g.getFontMetrics();
                for (int i = 0; i < 8; i++) {
                    g.setColor((7 + i) % 2 == 0 ? Page.TutorialBoard.DARK : Page.TutorialBoard.LIGHT);
                    String f = String.valueOf((char) ('a' + i));
                    g.drawString(f, ox + i * sz + sz - fm.stringWidth(f) - 3, oy + 8 * sz - 3);
                    g.setColor(i % 2 == 0 ? Page.TutorialBoard.DARK : Page.TutorialBoard.LIGHT);
                    g.drawString(String.valueOf(8 - i), ox + 3, oy + i * sz + fm.getAscent() + 2);
                }

                // highlights under the pieces
                Color gold = Page.GOLD;
                tint(g, lastFrom, new Color(gold.getRed(), gold.getGreen(), gold.getBlue(), 85), ox, oy, sz);
                tint(g, lastTo, new Color(gold.getRed(), gold.getGreen(), gold.getBlue(), 110), ox, oy, sz);
                tint(g, sel, new Color(gold.getRed(), gold.getGreen(), gold.getBlue(), 150), ox, oy, sz);
                tint(g, hintFrom, new Color(70, 150, 255, 150), ox, oy, sz);
                tint(g, hintTo, new Color(70, 150, 255, 150), ox, oy, sz);
                tint(g, wrongFrom, new Color(224, 80, 80, 150), ox, oy, sz);
                tint(g, wrongTo, new Color(224, 80, 80, 150), ox, oy, sz);
                if (pos.inCheck(pos.white)) {
                    int k = pos.kingSq(pos.white);
                    if (k >= 0) {
                        int cx = ox + (k % 8) * sz + sz / 2, cy = oy + (k / 8) * sz + sz / 2;
                        g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), sz * 0.65f,
                                new float[] { 0f, 1f }, new Color[] { new Color(224, 60, 60, 190), new Color(224, 60, 60, 0) }));
                        g.fillRect(ox + (k % 8) * sz, oy + (k / 8) * sz, sz, sz);
                    }
                }

                // pieces
                for (int s = 0; s < 64; s++) {
                    char p = pos.b[s];
                    if (p == '.') continue;
                    Page.drawPiece(g, (Chess.isWhite(p) ? "w" : "b") + Character.toUpperCase(p),
                            ox + (s % 8) * sz, oy + (s / 8) * sz, sz);
                }

                // move markers
                for (int t : targets) {
                    int x = ox + (t % 8) * sz, y = oy + (t / 8) * sz;
                    boolean capture = pos.b[t] != '.' || (t == pos.ep && pos.b[sel] != '.'
                            && Character.toUpperCase(pos.b[sel]) == 'P' && t % 8 != sel % 8);
                    if (capture) {
                        g.setColor(new Color(0xE05050));
                        g.setStroke(new BasicStroke(Math.max(3f, sz / 14f)));
                        g.draw(new Ellipse2D.Double(x + sz * 0.08, y + sz * 0.08, sz * 0.84, sz * 0.84));
                    } else {
                        g.setColor(new Color(47, 168, 90, 200));
                        g.fill(new Ellipse2D.Double(x + sz * 0.35, y + sz * 0.35, sz * 0.30, sz * 0.30));
                    }
                }
                g.setColor(Page.GOLD);
                g.setStroke(new BasicStroke(3f));
                g.drawRect(ox - 1, oy - 1, sz * 8 + 2, sz * 8 + 2);
                g.dispose();
            }
        }
    }
}