import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * ROYAL GAMBIT - chess rules and a small alpha-beta bot. No UI code in here.
 *
 * Board: int[64], index = row * 8 + col, row 0 = rank 8 (top), col 0 = file a.
 * Pieces: +1..+6 = white P N B R Q K, -1..-6 = black.
 */
public final class Chess {
    private Chess() {}

    static final int P = 1, N = 2, B = 3, R = 4, Q = 5, K = 6;
    static final int[] VALUE = { 0, 100, 320, 330, 500, 900, 0 };

    static final int[][] KN   = { {1,2},{2,1},{-1,2},{-2,1},{1,-2},{2,-1},{-1,-2},{-2,-1} };
    static final int[][] KG   = { {1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1} };
    static final int[][] DIAG = { {1,1},{1,-1},{-1,1},{-1,-1} };
    static final int[][] ORTH = { {1,0},{-1,0},{0,1},{0,-1} };

    static String sqName(int s) { return "" + (char) ('a' + (s & 7)) + (8 - (s >> 3)); }

    // =====================================================================
    //  Move
    // =====================================================================
    static final class Move {
        final int from, to, promo;      // promo = piece type (N,B,R,Q) or 0
        Move(int from, int to, int promo) { this.from = from; this.to = to; this.promo = promo; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Move)) return false;
            Move m = (Move) o;
            return m.from == from && m.to == to && m.promo == promo;
        }
        @Override public int hashCode() { return (from << 10) | (to << 4) | promo; }
        @Override public String toString() { return sqName(from) + sqName(to) + (promo == 0 ? "" : String.valueOf(" pnbrqk".charAt(promo))); }
    }

    // =====================================================================
    //  Position
    // =====================================================================
    static final class Position {
        final int[] b = new int[64];
        boolean white = true;
        int castle = 15;        // 1 = white O-O, 2 = white O-O-O, 4 = black O-O, 8 = black O-O-O
        int ep = -1;            // square a pawn may capture onto en passant, or -1
        int half = 0, full = 1; // halfmove clock (50-move rule), move number

        static Position start() {
            Position p = new Position();
            int[] back = { R, N, B, Q, K, B, N, R };
            for (int c = 0; c < 8; c++) {
                p.b[c] = -back[c];  p.b[8 + c] = -P;
                p.b[48 + c] = P;    p.b[56 + c] = back[c];
            }
            return p;
        }

        Position copy() {
            Position n = new Position();
            System.arraycopy(b, 0, n.b, 0, 64);
            n.white = white; n.castle = castle; n.ep = ep; n.half = half; n.full = full;
            return n;
        }

        String key() {
            char[] k = new char[67];
            for (int i = 0; i < 64; i++) k[i] = (char) ('a' + 6 + b[i]);
            k[64] = white ? 'w' : 'b';
            k[65] = (char) ('A' + castle);
            k[66] = (char) ('0' + ep + 1);
            return new String(k);
        }

        int kingSquare(boolean whiteSide) {
            int k = whiteSide ? K : -K;
            for (int i = 0; i < 64; i++) if (b[i] == k) return i;
            return -1;
        }

        boolean inCheck(boolean whiteSide) {
            int ks = kingSquare(whiteSide);
            return ks >= 0 && attacked(ks, !whiteSide);
        }

        /** Is square sq attacked by a piece of the given colour? */
        boolean attacked(int sq, boolean byWhite) {
            int r = sq >> 3, c = sq & 7, side = byWhite ? 1 : -1;
            int pr = byWhite ? r + 1 : r - 1;
            if (pr >= 0 && pr < 8)
                for (int dc = -1; dc <= 1; dc += 2) {
                    int pc = c + dc;
                    if (pc >= 0 && pc < 8 && b[pr * 8 + pc] == side * P) return true;
                }
            for (int[] v : KN) {
                int rr = r + v[0], cc = c + v[1];
                if (rr >= 0 && rr < 8 && cc >= 0 && cc < 8 && b[rr * 8 + cc] == side * N) return true;
            }
            for (int[] v : KG) {
                int rr = r + v[0], cc = c + v[1];
                if (rr >= 0 && rr < 8 && cc >= 0 && cc < 8 && b[rr * 8 + cc] == side * K) return true;
            }
            for (int[] v : DIAG) {
                int rr = r + v[0], cc = c + v[1];
                while (rr >= 0 && rr < 8 && cc >= 0 && cc < 8) {
                    int x = b[rr * 8 + cc];
                    if (x != 0) { if (x == side * B || x == side * Q) return true; break; }
                    rr += v[0]; cc += v[1];
                }
            }
            for (int[] v : ORTH) {
                int rr = r + v[0], cc = c + v[1];
                while (rr >= 0 && rr < 8 && cc >= 0 && cc < 8) {
                    int x = b[rr * 8 + cc];
                    if (x != 0) { if (x == side * R || x == side * Q) return true; break; }
                    rr += v[0]; cc += v[1];
                }
            }
            return false;
        }

        private static void promos(List<Move> out, int from, int to) {
            out.add(new Move(from, to, Q));
            out.add(new Move(from, to, R));
            out.add(new Move(from, to, B));
            out.add(new Move(from, to, N));
        }

        private void slide(List<Move> out, int s, int[][] dirs, int side, boolean capsOnly) {
            int r = s >> 3, c = s & 7;
            for (int[] v : dirs) {
                int rr = r + v[0], cc = c + v[1];
                while (rr >= 0 && rr < 8 && cc >= 0 && cc < 8) {
                    int to = rr * 8 + cc, t = b[to] * side;
                    if (t > 0) break;
                    if (t == 0) { if (!capsOnly) out.add(new Move(s, to, 0)); }
                    else { out.add(new Move(s, to, 0)); break; }
                    rr += v[0]; cc += v[1];
                }
            }
        }

        /** Pseudo-legal moves (may leave the own king in check). capsOnly = captures and promotions only. */
        void gen(List<Move> out, boolean capsOnly) {
            int side = white ? 1 : -1;
            for (int s = 0; s < 64; s++) {
                int p = b[s] * side;
                if (p <= 0) continue;
                int r = s >> 3, c = s & 7;
                if (p == P) {
                    int dir = white ? -1 : 1, startRow = white ? 6 : 1, lastRow = white ? 0 : 7, r1 = r + dir;
                    if (r1 < 0 || r1 > 7) continue;
                    int fwd = r1 * 8 + c;
                    if (b[fwd] == 0) {
                        if (r1 == lastRow) promos(out, s, fwd);
                        else if (!capsOnly) {
                            out.add(new Move(s, fwd, 0));
                            int f2 = (r + 2 * dir) * 8 + c;
                            if (r == startRow && b[f2] == 0) out.add(new Move(s, f2, 0));
                        }
                    }
                    for (int dc = -1; dc <= 1; dc += 2) {
                        int cc = c + dc;
                        if (cc < 0 || cc > 7) continue;
                        int to = r1 * 8 + cc;
                        if (b[to] * side < 0 || to == ep) {
                            if (r1 == lastRow) promos(out, s, to); else out.add(new Move(s, to, 0));
                        }
                    }
                } else if (p == N || p == K) {
                    for (int[] v : p == N ? KN : KG) {
                        int rr = r + v[0], cc = c + v[1];
                        if (rr < 0 || rr > 7 || cc < 0 || cc > 7) continue;
                        int to = rr * 8 + cc, t = b[to] * side;
                        if (t > 0) continue;
                        if (capsOnly && t == 0) continue;
                        out.add(new Move(s, to, 0));
                    }
                } else {
                    if (p != R) slide(out, s, DIAG, side, capsOnly);   // bishop or queen
                    if (p != B) slide(out, s, ORTH, side, capsOnly);   // rook or queen
                }
            }
            if (capsOnly) return;
            if (white && b[60] == K) {
                if ((castle & 1) != 0 && b[63] == R && b[61] == 0 && b[62] == 0
                        && !attacked(60, false) && !attacked(61, false) && !attacked(62, false))
                    out.add(new Move(60, 62, 0));
                if ((castle & 2) != 0 && b[56] == R && b[57] == 0 && b[58] == 0 && b[59] == 0
                        && !attacked(60, false) && !attacked(59, false) && !attacked(58, false))
                    out.add(new Move(60, 58, 0));
            } else if (!white && b[4] == -K) {
                if ((castle & 4) != 0 && b[7] == -R && b[5] == 0 && b[6] == 0
                        && !attacked(4, true) && !attacked(5, true) && !attacked(6, true))
                    out.add(new Move(4, 6, 0));
                if ((castle & 8) != 0 && b[0] == -R && b[1] == 0 && b[2] == 0 && b[3] == 0
                        && !attacked(4, true) && !attacked(3, true) && !attacked(2, true))
                    out.add(new Move(4, 2, 0));
            }
        }

        /** Returns the position after playing m (assumes m is pseudo-legal). */
        Position make(Move m) {
            Position n = copy();
            int p = n.b[m.from], t = Math.abs(p), side = p > 0 ? 1 : -1;
            int captured = n.b[m.to];
            n.ep = -1;
            boolean resets = t == P || captured != 0;
            if (t == P && m.to == ep && captured == 0 && (m.from & 7) != (m.to & 7)) {
                n.b[(m.from >> 3) * 8 + (m.to & 7)] = 0;     // en passant: remove the passed pawn
                resets = true;
            }
            n.b[m.to] = p;
            n.b[m.from] = 0;
            if (t == P) {
                if (Math.abs(m.to - m.from) == 16) n.ep = (m.from + m.to) / 2;
                if (m.promo != 0) n.b[m.to] = side * m.promo;
            }
            if (t == K) {
                if (m.to - m.from == 2)  { n.b[m.from + 1] = n.b[m.from + 3]; n.b[m.from + 3] = 0; }
                if (m.to - m.from == -2) { n.b[m.from - 1] = n.b[m.from - 4]; n.b[m.from - 4] = 0; }
            }
            if (m.from == 60 || m.to == 60) n.castle &= ~3;
            if (m.from == 63 || m.to == 63) n.castle &= ~1;
            if (m.from == 56 || m.to == 56) n.castle &= ~2;
            if (m.from == 4  || m.to == 4)  n.castle &= ~12;
            if (m.from == 7  || m.to == 7)  n.castle &= ~4;
            if (m.from == 0  || m.to == 0)  n.castle &= ~8;
            n.white = !white;
            n.half = resets ? 0 : half + 1;
            if (!white) n.full = full + 1;
            return n;
        }

        List<Move> legal() {
            List<Move> ps = new ArrayList<>(48);
            gen(ps, false);
            List<Move> out = new ArrayList<>(ps.size());
            for (Move m : ps) if (!make(m).inCheck(white)) out.add(m);
            return out;
        }

        boolean insufficient() {
            int minors = 0;
            for (int v : b) {
                int t = Math.abs(v);
                if (t == P || t == R || t == Q) return false;
                if (t == N || t == B) minors++;
            }
            return minors <= 1;
        }

        /** Standard algebraic notation of a legal move played from this position. */
        String san(Move m) {
            int p = b[m.from], t = Math.abs(p);
            String s;
            if (t == K && Math.abs(m.to - m.from) == 2) {
                s = m.to > m.from ? "O-O" : "O-O-O";
            } else {
                boolean cap = b[m.to] != 0 || (t == P && (m.from & 7) != (m.to & 7));
                StringBuilder sb = new StringBuilder();
                if (t == P) {
                    if (cap) sb.append((char) ('a' + (m.from & 7)));
                } else {
                    sb.append(" PNBRQK".charAt(t));
                    boolean amb = false, sameFile = false, sameRank = false;
                    for (Move o : legal())
                        if (o.to == m.to && o.from != m.from && b[o.from] == p) {
                            amb = true;
                            if ((o.from & 7) == (m.from & 7)) sameFile = true;
                            if ((o.from >> 3) == (m.from >> 3)) sameRank = true;
                        }
                    if (amb) {
                        if (!sameFile) sb.append((char) ('a' + (m.from & 7)));
                        else if (!sameRank) sb.append(8 - (m.from >> 3));
                        else sb.append(sqName(m.from));
                    }
                }
                if (cap) sb.append('x');
                sb.append(sqName(m.to));
                if (m.promo != 0) sb.append('=').append(" PNBRQK".charAt(m.promo));
                s = sb.toString();
            }
            Position n = make(m);
            if (n.inCheck(n.white)) s += n.legal().isEmpty() ? "#" : "+";
            return s;
        }
    }

    // =====================================================================
    //  Bot
    // =====================================================================
    static final class Ai {
        static final int INF = 1_000_000, MATE = 100_000;
        long deadline = Long.MAX_VALUE, nodes;
        boolean abort;

        /** level: 0 Easy, 1 Medium, 2 Hard, 3 Extreme. Returns null if there is no legal move. */
        static Move choose(Position pos, int level, Random rnd) {
            List<Move> legal = pos.legal();
            if (legal.isEmpty()) return null;
            if (legal.size() == 1) return legal.get(0);
            Ai ai = new Ai();

            if (level <= 1) {                       // shallow search + random noise = human-like mistakes
                int depth = level == 0 ? 1 : 2, noise = level == 0 ? 200 : 25;
                if (level == 0 && rnd.nextInt(100) < 15) return legal.get(rnd.nextInt(legal.size()));
                Move best = null;
                int bestScore = Integer.MIN_VALUE;
                for (Move m : legal) {
                    int sc = ai.score(pos, m, depth) + rnd.nextInt(noise * 2 + 1) - noise;
                    if (best == null || sc > bestScore) { bestScore = sc; best = m; }
                }
                return best;
            }

            long ms = level == 2 ? 1800 : 4000;     // iterative deepening with a time limit
            int maxDepth = level == 2 ? 4 : 9;
            ai.deadline = System.nanoTime() + ms * 1_000_000L;
            orderMoves(pos, legal);
            Move best = legal.get(0);
            for (int depth = 1; depth <= maxDepth; depth++) {
                int alpha = -INF, itScore = -INF;
                Move itBest = null;
                for (Move m : legal) {
                    int sc = -ai.negamax(pos.make(m), depth - 1, -INF, -alpha, 1);
                    if (ai.abort) break;
                    if (itBest == null || sc > itScore) { itScore = sc; itBest = m; }
                    if (sc > alpha) alpha = sc;
                }
                if (ai.abort || itBest == null) break;      // unfinished iteration is discarded
                best = itBest;
                legal.remove(best);
                legal.add(0, best);
                if (itScore >= MATE - 100) break;
            }
            return best;
        }

        int score(Position pos, Move m, int depth) {
            Position n = pos.make(m);
            if (n.legal().isEmpty()) return n.inCheck(n.white) ? MATE : 0;
            return -negamax(n, depth - 1, -INF, INF, 1);
        }

        int negamax(Position p, int depth, int alpha, int beta, int ply) {
            if ((++nodes & 2047) == 0 && System.nanoTime() > deadline) abort = true;
            if (abort) return 0;
            if (p.half >= 100) return 0;
            boolean inCheck = p.inCheck(p.white);
            if (inCheck && ply < 14) depth++;
            if (depth <= 0) return quiesce(p, alpha, beta, 0);
            List<Move> ms = new ArrayList<>(48);
            p.gen(ms, false);
            orderMoves(p, ms);
            int best = -INF, legal = 0;
            for (Move m : ms) {
                Position n = p.make(m);
                if (n.inCheck(p.white)) continue;
                legal++;
                int sc = -negamax(n, depth - 1, -beta, -alpha, ply + 1);
                if (abort) return 0;
                if (sc > best) best = sc;
                if (best > alpha) alpha = best;
                if (alpha >= beta) break;
            }
            if (legal == 0) return inCheck ? -MATE + ply : 0;
            return best;
        }

        int quiesce(Position p, int alpha, int beta, int qd) {
            if ((++nodes & 2047) == 0 && System.nanoTime() > deadline) abort = true;
            if (abort) return 0;
            int stand = eval(p);
            if (qd >= 8 || stand >= beta) return stand;
            if (stand > alpha) alpha = stand;
            List<Move> ms = new ArrayList<>(16);
            p.gen(ms, true);
            orderMoves(p, ms);
            for (Move m : ms) {
                Position n = p.make(m);
                if (n.inCheck(p.white)) continue;
                int sc = -quiesce(n, -beta, -alpha, qd + 1);
                if (abort) return 0;
                if (sc >= beta) return sc;
                if (sc > alpha) alpha = sc;
            }
            return alpha;
        }

        static int orderScore(Position p, Move m) {
            int victim = Math.abs(p.b[m.to]), att = Math.abs(p.b[m.from]), s = 0;
            if (victim != 0) s += 1000 + 10 * VALUE[victim] - VALUE[att] / 10;
            else if (att == P && m.to == p.ep) s += 2000;
            if (m.promo != 0) s += VALUE[m.promo];
            return s;
        }

        static void orderMoves(Position p, List<Move> ms) {
            ms.sort((x, y) -> orderScore(p, y) - orderScore(p, x));
        }

        /** Static evaluation in centipawns from the side to move's point of view. */
        static int eval(Position p) {
            int wm = 0, bm = 0, wb = 0, bb = 0;
            for (int v : p.b) {
                int t = Math.abs(v);
                if (t >= N && t <= Q) { if (v > 0) wm += VALUE[t]; else bm += VALUE[t]; }
                if (t == B) { if (v > 0) wb++; else bb++; }
            }
            boolean end = wm + bm <= 2600;
            int score = 0;
            for (int i = 0; i < 64; i++) {
                int v = p.b[i];
                if (v == 0) continue;
                int t = Math.abs(v);
                int sc = VALUE[t] + pst(t, i, v > 0, end);
                score += v > 0 ? sc : -sc;
            }
            if (wb >= 2) score += 30;
            if (bb >= 2) score -= 30;
            return p.white ? score : -score;
        }

        static int pst(int t, int s, boolean white, boolean end) {
            int row = s >> 3, c = s & 7;
            int adv = white ? 7 - row : row;                              // 0 = own back rank
            int cd = Math.min(c, 7 - c) + Math.min(row, 7 - row);         // 0 corner .. 6 centre
            switch (t) {
                case P: { int v = (adv - 1) * (end ? 14 : 8); if (c == 3 || c == 4) v += adv * 3; return v; }
                case N: return cd * 7 - (adv == 0 ? 15 : 0);
                case B: return cd * 4 - (adv == 0 ? 15 : 0);
                case R: return (adv == 6 ? 15 : 0) + (c == 3 || c == 4 ? 4 : 0);
                case Q: return cd * 2;
                default:
                    if (end) return cd * 8;
                    return adv == 0 ? (c <= 2 || c >= 6 ? 25 : 0) : -adv * 20;
            }
        }
    }
}