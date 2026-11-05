import java.awt.*;
import java.awt.geom.*;

/**
 * ROYAL GAMBIT - vector chess pieces with clearly different silhouettes.
 *
 *   Pawn   : round head, collar, tapered body
 *   Rook   : battlemented tower with brick bands
 *   Knight : horse head with ears, mane, eye and nostril
 *   Bishop : mitre with a diagonal slit and a ball on top
 *   Queen  : five-point crown, each tip with a pearl
 *   King   : domed crown with a cross on top
 *
 * Draw with:  PieceArt.draw(g, "wK", x, y, size)    (same codes as GameBoard.code(): wP, bN, ...)
 *        or:  PieceArt.draw(g, isWhite, Chess.K, x, y, size)
 * Piece types follow the engine order: 1=P 2=N 3=B 4=R 5=Q 6=K.
 */
public final class PieceArt {
    private PieceArt() {}

    static final String LETTERS = " PNBRQK";

    public static void draw(Graphics2D g0, String code, int x, int y, int size) {
        if (code == null || code.length() < 2) return;
        draw(g0, code.charAt(0) == 'w', LETTERS.indexOf(code.charAt(1)), x, y, size);
    }

    public static void draw(Graphics2D g0, boolean white, int type, int x, int y, int size) {
        if (type < 1 || type > 6 || size <= 0) return;
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        double sc = size / 100.0 * 0.94;                       // pieces are designed in a 100 x 100 box
        g.translate(x + size * 0.03, y + size * 0.03);
        g.scale(sc, sc);

        g.setColor(new Color(0, 0, 0, 55));                    // soft ground shadow
        g.fill(new Ellipse2D.Double(18, 86, 64, 12));

        switch (type) {
            case 1: pawn(g, white);   break;
            case 2: knight(g, white); break;
            case 3: bishop(g, white); break;
            case 4: rook(g, white);   break;
            case 5: queen(g, white);  break;
            default: king(g, white);  break;
        }
        g.dispose();
    }

    // ------------------------------------------------------------------ pieces

    private static void pawn(Graphics2D g, boolean w) {
        Path2D body = new Path2D.Double();
        body.moveTo(43, 44);
        body.curveTo(43, 62, 40, 72, 31, 82);
        body.lineTo(69, 82);
        body.curveTo(60, 72, 57, 62, 57, 44);
        body.closePath();
        part(g, body, w);
        part(g, new RoundRectangle2D.Double(35, 41, 30, 7, 7, 7), w);       // collar
        part(g, new Ellipse2D.Double(38, 15, 24, 24), w);                   // head
        base(g, w);
    }

    private static void rook(Graphics2D g, boolean w) {
        Path2D t = new Path2D.Double();
        t.moveTo(29, 12);  t.lineTo(39, 12);  t.lineTo(39, 19);  t.lineTo(45, 19);  t.lineTo(45, 12);
        t.lineTo(55, 12);  t.lineTo(55, 19);  t.lineTo(61, 19);  t.lineTo(61, 12);  t.lineTo(71, 12);
        t.lineTo(71, 30);  t.lineTo(64, 35);  t.lineTo(64, 68);  t.lineTo(73, 82);  t.lineTo(27, 82);
        t.lineTo(36, 68);  t.lineTo(36, 35);  t.lineTo(29, 30);
        t.closePath();
        part(g, t, w);
        detail(g, w, new Line2D.Double(30, 30, 70, 30), 2f);                // brick bands
        detail(g, w, new Line2D.Double(37, 66, 63, 66), 2f);
        detail(g, w, new Line2D.Double(50, 40, 50, 60), 1.6f);              // gate slit
        base(g, w);
    }

    private static void knight(Graphics2D g, boolean w) {
        Path2D h = new Path2D.Double();                                     // horse head, facing left
        h.moveTo(27, 82);
        h.curveTo(29, 66, 43, 59, 46, 50);                                  // chest to throat
        h.curveTo(39, 52, 31, 57, 25, 54);                                  // jaw to chin
        h.curveTo(18, 52, 17, 44, 23, 38);                                  // muzzle
        h.curveTo(29, 32, 34, 24, 39, 14);                                  // nose bridge to forehead
        h.lineTo(46, 7);                                                    // ear
        h.lineTo(51, 16);
        h.curveTo(67, 20, 77, 44, 73, 66);                                  // mane / back of neck
        h.curveTo(72, 73, 73, 78, 73, 82);
        h.closePath();
        part(g, h, w);
        Color eye = w ? new Color(0x2B1F0E) : new Color(0xF2EAD2);
        g.setColor(eye);
        g.fill(new Ellipse2D.Double(36, 27, 5.5, 5.5));                     // eye
        g.fill(new Ellipse2D.Double(20.5, 42, 3.6, 3.6));                   // nostril
        Path2D mane = new Path2D.Double();
        mane.moveTo(52, 20);  mane.curveTo(62, 26, 66, 38, 64, 52);
        mane.moveTo(56, 22);  mane.curveTo(68, 30, 71, 44, 69, 58);
        detail(g, w, mane, 1.8f);
        detail(g, w, new Line2D.Double(30, 51, 38, 47), 1.6f);              // mouth line
        base(g, w);
    }

    private static void bishop(Graphics2D g, boolean w) {
        Path2D body = new Path2D.Double();
        body.moveTo(43, 62);
        body.curveTo(44, 72, 40, 77, 31, 82);
        body.lineTo(69, 82);
        body.curveTo(60, 77, 56, 72, 57, 62);
        body.closePath();
        part(g, body, w);
        part(g, new RoundRectangle2D.Double(35, 58, 30, 7, 7, 7), w);       // collar
        Path2D mitre = new Path2D.Double();
        mitre.moveTo(50, 19);
        mitre.curveTo(68, 30, 68, 50, 57, 58);
        mitre.lineTo(43, 58);
        mitre.curveTo(32, 50, 32, 30, 50, 19);
        mitre.closePath();
        part(g, mitre, w);
        detail(g, w, new Line2D.Double(45, 31, 56, 43), 2.4f);              // diagonal slit
        part(g, new Ellipse2D.Double(44.5, 9, 11, 11), w);                  // top ball
        base(g, w);
    }

    private static void queen(Graphics2D g, boolean w) {
        Path2D body = new Path2D.Double();
        body.moveTo(38, 72);
        body.lineTo(34, 80);  body.lineTo(66, 80);  body.lineTo(62, 72);
        body.closePath();
        part(g, body, w);
        Path2D crown = new Path2D.Double();                                 // five points
        crown.moveTo(34, 72);
        crown.lineTo(21, 26);  crown.lineTo(30, 49);
        crown.lineTo(35, 21);  crown.lineTo(43, 49);
        crown.lineTo(50, 14);  crown.lineTo(57, 49);
        crown.lineTo(65, 21);  crown.lineTo(70, 49);
        crown.lineTo(79, 26);  crown.lineTo(66, 72);
        crown.closePath();
        part(g, crown, w);
        part(g, new RoundRectangle2D.Double(32, 68, 36, 8, 8, 8), w);       // band
        double[][] tips = { { 21, 24 }, { 35, 19 }, { 50, 12 }, { 65, 19 }, { 79, 24 } };
        for (double[] p : tips) part(g, new Ellipse2D.Double(p[0] - 4.5, p[1] - 4.5, 9, 9), w);   // pearls
        base(g, w);
    }

    private static void king(Graphics2D g, boolean w) {
        Path2D body = new Path2D.Double();
        body.moveTo(38, 72);
        body.lineTo(34, 80);  body.lineTo(66, 80);  body.lineTo(62, 72);
        body.closePath();
        part(g, body, w);
        Path2D dome = new Path2D.Double();
        dome.moveTo(33, 72);
        dome.curveTo(23, 52, 31, 34, 50, 32);
        dome.curveTo(69, 34, 77, 52, 67, 72);
        dome.closePath();
        part(g, dome, w);
        detail(g, w, new Arc2D.Double(31, 38, 38, 26, 200, 140, Arc2D.OPEN), 1.8f);   // crown band
        part(g, new RoundRectangle2D.Double(32, 68, 36, 8, 8, 8), w);       // band
        Area cross = new Area(new RoundRectangle2D.Double(45.5, 5, 9, 30, 3, 3));
        cross.add(new Area(new RoundRectangle2D.Double(38, 13, 24, 9, 3, 3)));
        part(g, cross, w);                                                  // cross
        base(g, w);
    }

    // ------------------------------------------------------------------ helpers

    private static void base(Graphics2D g, boolean w) {
        part(g, new RoundRectangle2D.Double(27, 78, 46, 9, 8, 8), w);
        part(g, new RoundRectangle2D.Double(20, 85, 60, 9, 9, 9), w);
    }

    /** Fills a part with a cylindrical gradient and a clean outline. */
    private static void part(Graphics2D g, Shape s, boolean w) {
        Color light = w ? new Color(0xFFFCF0) : new Color(0x6E6E7C);
        Color mid   = w ? new Color(0xE8DAB4) : new Color(0x373742);
        Color dark  = w ? new Color(0xB59B64) : new Color(0x101016);
        g.setPaint(new LinearGradientPaint(new Point2D.Float(18, 0), new Point2D.Float(82, 0),
                new float[] { 0f, 0.38f, 1f }, new Color[] { mid, light, dark }));
        g.fill(s);
        g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(w ? new Color(0x3B2D17) : new Color(0x040406));
        g.draw(s);
        if (!w) {                                                           // thin light rim so black pieces pop on dark squares
            g.setStroke(new BasicStroke(0.9f));
            g.setColor(new Color(255, 255, 255, 95));
            g.draw(s);
        }
    }

    /** Interior detail lines (slit, mane, bands...). */
    private static void detail(Graphics2D g, boolean w, Shape s, float width) {
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(w ? new Color(0x3B2D17) : new Color(0xD9D1BC));
        g.draw(s);
    }
}