import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.TextAttribute;
import java.awt.geom.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.prefs.Preferences;

/**
 * ROYAL GAMBIT - Create Account / Welcome Back screen + main menu (Java Swing, no extra libraries).
 * Run in VS Code:  javac *.java  &&  java RoyalGambit
 */
public class RoyalGambit {

    // ---------- Palette (matches the reference) ----------
    static final Color BG_DARK      = new Color(0x15171A);
    static final Color BG_LIGHT     = new Color(0x2B2E33);
    static final Color CARD_TOP     = new Color(0x2A2D32);
    static final Color CARD_BOTTOM  = new Color(0x24272B);
    static final Color PANEL        = new Color(0x212428);
    static final Color FIELD        = new Color(0x1C1E22);
    static final Color TILE_LIGHT   = new Color(0x3A3D42);
    static final Color TILE_DARK    = new Color(0x24272B);
    static final Color PIECE        = new Color(0x5C6066);
    static final Color GOLD         = new Color(0xC9A95C);
    static final Color GOLD_LIGHT   = new Color(0xDEC074);
    static final Color GOLD_DARK    = new Color(0xB89849);
    static final Color GOLD_DEEP    = new Color(0x9A7B2F);   // gold that stays readable on the light background image
    static final Color NAVY         = new Color(0x2A3350);
    static final Color CREAM        = new Color(0xEDE5D0);
    static final Color MUTED        = new Color(0x8C8F94);
    static final Color INK          = new Color(0x22252A);
    static final Color ERROR        = new Color(0xE5736A);
    static final Color OK           = new Color(0x7FCB8F);

    // ---------- Fonts (first one installed wins) ----------
    static String pick(String... names) {
        Set<String> avail = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String n : names) if (avail.contains(n)) return n;
        return names[names.length - 1];
    }
    static final String SERIF  = pick("Cinzel", "Trajan Pro", "Cormorant Garamond", "Georgia", "Times New Roman", "Serif");
    static final String SANS   = pick("Roboto", "Segoe UI", "Helvetica Neue", "Arial", "SansSerif");
    static final String SYMBOL = pick("Segoe UI Symbol", "Apple Symbols", "DejaVu Sans", "SansSerif");

    // ---------- Background image (put background.png next to RoyalGambit.java) ----------
    static final BufferedImage BG_IMAGE = loadBackground();
    static BufferedImage loadBackground() {
        try {
            File f = new File("background.png");
            if (f.exists()) return ImageIO.read(f);
            java.net.URL u = RoyalGambit.class.getResource("/background.png");
            if (u != null) return ImageIO.read(u);
        } catch (IOException ignored) { }
        return null;
    }
    /** Draws the image scaled to cover the whole area (like CSS background-size: cover). Returns false if no image. */
    static boolean paintCover(Graphics2D g, int w, int h) {
        if (BG_IMAGE == null) return false;
        double sc = Math.max((double) w / BG_IMAGE.getWidth(), (double) h / BG_IMAGE.getHeight());
        int dw = (int) Math.ceil(BG_IMAGE.getWidth() * sc), dh = (int) Math.ceil(BG_IMAGE.getHeight() * sc);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(BG_IMAGE, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
        return true;
    }

    // ---------- Helpers ----------
    static void aa(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }
    static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    static void fix(JComponent c, int w, int h) {
        Dimension d = new Dimension(w, h);
        c.setPreferredSize(d); c.setMinimumSize(d); c.setMaximumSize(d);
    }
    static JLabel lbl(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(CREAM);
        return l;
    }
    static void hint(JTextComponent c, Graphics g0, String text) {
        if (!c.getText().isEmpty()) return;
        Graphics2D g = (Graphics2D) g0.create();
        aa(g);
        g.setColor(MUTED);
        g.setFont(c.getFont());
        FontMetrics fm = g.getFontMetrics();
        Insets in = c.getInsets();
        g.drawString(text, in.left, (c.getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        g.dispose();
    }

    // =====================================================================
    //  Background: gradient, rounded card, checkerboards, corner pieces
    // =====================================================================
    static class Backdrop extends JPanel {
        Backdrop() { super(new GridBagLayout()); setOpaque(true); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            int w = getWidth(), h = getHeight();
            if (paintCover(g, w, h)) { g.dispose(); return; }   // image replaces card + decorations
            g.setPaint(new GradientPaint(0, 0, BG_DARK, w, h, BG_LIGHT));
            g.fillRect(0, 0, w, h);

            boolean wide = w >= 900;
            int mx = wide ? (int) (w * 0.08) : 12;
            int my = wide ? (int) (h * 0.063) : 12;
            Shape card = new RoundRectangle2D.Float(mx, my, w - 2 * mx, h - 2 * my, 32, 32);
            g.setPaint(new GradientPaint(0, my, CARD_TOP, 0, h - my, CARD_BOTTOM));
            g.fill(card);
            g.setClip(card);

            if (wide) {
                int formW = clamp((int) (w * 0.36), 300, 520);
                int cell = Math.max(28, (int) (h * 0.08));
                int free = (w - formW) / 2 - mx - 16;
                int cols = Math.min(4, free / cell);
                int rows = 6, y0 = (int) (h * 0.26);
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        g.setColor(((r + c) % 2 == 0) ? TILE_LIGHT : TILE_DARK);
                        g.fillRect(mx + c * cell, y0 + r * cell, cell, cell);
                        g.setColor(((r + c) % 2 == 1) ? TILE_LIGHT : TILE_DARK);
                        g.fillRect(w - mx - (c + 1) * cell, y0 + r * cell, cell, cell);
                    }
                }
                int ps = Math.max(30, (int) (h * 0.07));
                g.setFont(new Font(SYMBOL, Font.PLAIN, ps));
                g.setColor(PIECE);
                g.drawString("\u2659", mx + ps / 2, my + ps + ps / 3);                    // pawn
                g.drawString("\u2658", w - mx - ps * 3 / 2, my + ps + ps / 3);            // knight
                g.drawString("\u2658", mx + ps / 3, h - my - ps / 3);                     // knight
                g.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 190));
                g.drawString("\u2655", w - mx - ps * 3 / 2, h - my - ps / 3);             // queen
            }
            g.dispose();
        }
    }

    // =====================================================================
    //  Rounded text input (placeholder, gold focus border, optional eye)
    // =====================================================================
    static class Input extends JPanel {
        final JTextField field;
        JComponent eye;
        boolean revealed, error;
        int radius = 8;

        Input(String placeholder, boolean password) {
            super(new BorderLayout());
            setOpaque(false);
            field = password
                    ? new JPasswordField() {
                        @Override protected void paintComponent(Graphics g) { super.paintComponent(g); hint(this, g, placeholder); }
                    }
                    : new JTextField() {
                        @Override protected void paintComponent(Graphics g) { super.paintComponent(g); hint(this, g, placeholder); }
                    };
            field.setOpaque(false);
            field.setForeground(CREAM);
            field.setCaretColor(GOLD);
            field.setSelectionColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 120));
            field.setSelectedTextColor(Color.WHITE);
            field.addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { repaint(); }
                @Override public void focusLost(FocusEvent e) { repaint(); }
            });
            add(field, BorderLayout.CENTER);

            if (password) {
                ((JPasswordField) field).setEchoChar('\u2022');
                eye = new JComponent() {
                    boolean hover;
                    { setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                      addMouseListener(new MouseAdapter() {
                          @Override public void mouseClicked(MouseEvent e) {
                              revealed = !revealed;
                              ((JPasswordField) field).setEchoChar(revealed ? (char) 0 : '\u2022');
                              repaint();
                          }
                          @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                          @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                      }); }
                    @Override protected void paintComponent(Graphics g0) {
                        Graphics2D g = (Graphics2D) g0.create();
                        aa(g);
                        double cx = getWidth() / 2.0, cy = getHeight() / 2.0, r = Math.min(getWidth(), getHeight()) * 0.28;
                        g.setColor(hover ? GOLD : MUTED);
                        g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        Path2D eyeShape = new Path2D.Double();
                        eyeShape.moveTo(cx - r, cy);
                        eyeShape.quadTo(cx, cy - r * 1.25, cx + r, cy);
                        eyeShape.quadTo(cx, cy + r * 1.25, cx - r, cy);
                        g.draw(eyeShape);
                        g.fill(new Ellipse2D.Double(cx - r * 0.3, cy - r * 0.3, r * 0.6, r * 0.6));
                        if (!revealed) g.draw(new Line2D.Double(cx - r, cy + r * 0.8, cx + r, cy - r * 0.8));
                        g.dispose();
                    }
                };
                add(eye, BorderLayout.EAST);
            }
        }

        String text() {
            return field instanceof JPasswordField ? new String(((JPasswordField) field).getPassword()) : field.getText();
        }
        void set(String s) { field.setText(s); }
        void setError(boolean e) { error = e; repaint(); }

        void scale(int w, int fh, int gap, float px) {
            setBorder(new EmptyBorder(0, 0, gap, 0));
            fix(this, w, fh + gap);
            field.setFont(new Font(SANS, Font.PLAIN, Math.round(px)));
            int side = (int) (fh * 0.32);
            field.setBorder(new EmptyBorder(0, side, 0, side));
            if (eye != null) fix(eye, (int) (fh * 0.9), fh);
            radius = Math.max(6, fh / 5);
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            int w = getWidth(), h = getHeight() - getInsets().bottom;
            g.setColor(FIELD);
            g.fill(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, radius, radius));
            boolean f = field.isFocusOwner();
            g.setColor(error ? new Color(0xE5736A) : f ? GOLD : CREAM);
            g.setStroke(new BasicStroke(f ? 2.2f : 1.6f));
            g.draw(new RoundRectangle2D.Float(1.5f, 1.5f, w - 3, h - 3, radius, radius));
            g.dispose();
        }
    }

    // =====================================================================
    //  Gold button
    // =====================================================================
    static class GoldButton extends JButton {
        boolean hover;
        GoldButton(String t) {
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
            aa(g);
            int w = getWidth(), h = getHeight() - getInsets().bottom;
            Color a = hover ? GOLD_LIGHT : GOLD, b = hover ? GOLD : GOLD_DARK;
            if (getModel().isPressed()) { a = GOLD_DARK; b = GOLD_DARK; }
            g.setPaint(new GradientPaint(0, 0, a, 0, h, b));
            int r = Math.max(6, h / 6);
            g.fill(new RoundRectangle2D.Float(0, 0, w, h, r, r));
            g.setFont(getFont());
            g.setColor(INK);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(getText(), (w - fm.stringWidth(getText())) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
        }
    }

    // =====================================================================
    //  Underlined gold link
    // =====================================================================
    static class Link extends JLabel {
        Link(String text, Runnable action) {
            super(text);
            setForeground(GOLD);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { action.run(); }
                @Override public void mouseEntered(MouseEvent e) { setForeground(GOLD_LIGHT); }
                @Override public void mouseExited(MouseEvent e) { setForeground(GOLD); }
            });
        }
        void size(float px) {
            Map<TextAttribute, Object> m = new HashMap<>();
            m.put(TextAttribute.FAMILY, SANS);
            m.put(TextAttribute.SIZE, px);
            m.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
            setFont(new Font(m));
        }
    }

    // =====================================================================
    //  Gold checkbox
    // =====================================================================
    static class GoldCheck extends JComponent {
        boolean selected;
        GoldCheck() {
            setFocusable(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { toggle(); }
            });
            addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent e) { if (e.getKeyCode() == KeyEvent.VK_SPACE) toggle(); }
            });
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { repaint(); }
                @Override public void focusLost(FocusEvent e) { repaint(); }
            });
        }
        void toggle() { selected = !selected; repaint(); }
        void setSelected(boolean s) { selected = s; repaint(); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            int s = Math.min(getWidth(), getHeight()) - 3;
            g.setColor(isFocusOwner() ? GOLD_LIGHT : GOLD);
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new RoundRectangle2D.Float(1.5f, 1.5f, s, s, 5, 5));
            if (selected) {
                Path2D p = new Path2D.Float();
                p.moveTo(s * 0.22, s * 0.54); p.lineTo(s * 0.43, s * 0.74); p.lineTo(s * 0.80, s * 0.28);
                g.translate(1.5, 1.5);
                g.draw(p);
            }
            g.dispose();
        }
    }

    // =====================================================================
    //  Loading dialog: only appears if the work is slow (weak signal)
    // =====================================================================
    static class LoadingDialog {
        static final int DELAY_MS = 600;          // operations faster than this never show the dialog
        private final JDialog dialog;
        private final Timer showTimer;

        LoadingDialog(Component parent, String text) {
            Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
            if (owner == null && parent instanceof Window) owner = (Window) parent;
            dialog = new JDialog(owner, "Please wait", Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            dialog.setResizable(false);
            JProgressBar bar = new JProgressBar();
            bar.setIndeterminate(true);
            JLabel label = new JLabel(text, SwingConstants.CENTER);
            JPanel box = new JPanel(new BorderLayout(0, 12));
            box.setBorder(new EmptyBorder(18, 24, 18, 24));
            box.add(label, BorderLayout.NORTH);
            box.add(bar, BorderLayout.CENTER);
            dialog.getContentPane().add(box);
            dialog.pack();
            dialog.setSize(Math.max(dialog.getWidth(), 300), dialog.getHeight());
            dialog.setLocationRelativeTo(owner);
            showTimer = new Timer(DELAY_MS, ev -> dialog.setVisible(true));   // blocks until close()
            showTimer.setRepeats(false);
        }
        /** Call before starting the background work. */
        void start() { showTimer.start(); }
        /** Call from done(): cancels the pending dialog or closes the visible one. */
        void close() { showTimer.stop(); dialog.setVisible(false); dialog.dispose(); }
    }

    // =====================================================================
    //  Mailer: minimal SMTP-over-SSL client (no libraries)
    //  Reads ~/.royalgambit-mail.properties  (host, port, user, password, from)
    // =====================================================================
    static class Mailer {
        static final File CONFIG = new File(System.getProperty("user.home"), ".royalgambit-mail.properties");
        static boolean configured() { return CONFIG.exists(); }

        static void send(String to, String subject, String body) throws Exception {
            Properties c = new Properties();
            try (InputStream in = new FileInputStream(CONFIG)) { c.load(in); }
            String host = c.getProperty("host"), user = c.getProperty("user"), pass = c.getProperty("password");
            int port = Integer.parseInt(c.getProperty("port", "465"));
            String from = c.getProperty("from", user);

            try (SSLSocket s = (SSLSocket) SSLSocketFactory.getDefault().createSocket()) {
                s.setSoTimeout(15000);
                s.connect(new java.net.InetSocketAddress(host, port), 15000);   // don't hang forever on a bad signal
                s.startHandshake();
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
                expect(in, "220");
                cmd(in, out, "EHLO royalgambit", "250");
                cmd(in, out, "AUTH LOGIN", "334");
                cmd(in, out, b64(user), "334");
                cmd(in, out, b64(pass), "235");
                cmd(in, out, "MAIL FROM:<" + from + ">", "250");
                cmd(in, out, "RCPT TO:<" + to + ">", "250");
                cmd(in, out, "DATA", "354");
                String msg = "From: Royal Gambit <" + from + ">\r\n"
                        + "To: " + to + "\r\n"
                        + "Subject: " + subject + "\r\n"
                        + "MIME-Version: 1.0\r\n"
                        + "Content-Type: text/plain; charset=UTF-8\r\n\r\n"
                        + body.replace("\n", "\r\n") + "\r\n.\r\n";
                out.write(msg); out.flush();
                expect(in, "250");
                try { cmd(in, out, "QUIT", "221"); } catch (Exception ignored) { }
            }
        }
        /** Turns network exceptions into a message a player can understand. */
        static String friendly(Exception ex) {
            if (ex instanceof java.net.SocketTimeoutException)
                return "Connection timed out. Your signal may be weak - please try again.";
            if (ex instanceof java.net.UnknownHostException || ex instanceof java.net.ConnectException
                    || ex instanceof java.net.NoRouteToHostException)
                return "No internet connection. Please check your signal and try again.";
            return ex.getMessage();
        }
        static String b64(String s) { return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8)); }
        static void cmd(BufferedReader in, BufferedWriter out, String line, String code) throws IOException {
            out.write(line + "\r\n"); out.flush();
            expect(in, code);
        }
        /** Reads a (possibly multi-line) SMTP reply and checks its status code. */
        static void expect(BufferedReader in, String code) throws IOException {
            String line;
            do {
                line = in.readLine();
                if (line == null) throw new IOException("Mail server closed the connection");
            } while (line.length() > 3 && line.charAt(3) == '-');
            if (!line.startsWith(code)) throw new IOException("Mail server said: " + line);
        }
    }

    // =====================================================================
    //  Google sign-in (OAuth 2.0 for desktop apps: system browser + PKCE + loopback redirect)
    //  Reads ~/.royalgambit-google.properties  (client_id, client_secret)
    // =====================================================================
    static class GoogleAuth {
        static final File CONFIG = new File(System.getProperty("user.home"), ".royalgambit-google.properties");
        static boolean configured() {
            if (!CONFIG.exists()) return false;
            try (InputStream in = new FileInputStream(CONFIG)) {
                Properties c = new Properties(); c.load(in);
                return !c.getProperty("client_id", "").isBlank();
            } catch (IOException e) { return false; }
        }

        static class Cancelled extends Exception { Cancelled() { super("Cancelled"); } }

        /** Lets the UI abort a sign-in that is waiting for the browser. */
        static class Session {
            final java.util.concurrent.CompletableFuture<java.util.Map<String, String>> result =
                    new java.util.concurrent.CompletableFuture<>();
            void cancel() { result.completeExceptionally(new Cancelled()); }
        }

        /** Opens the browser, waits for the player to sign in. @return {email, displayName} */
        static String[] signIn(Session session) throws Exception {
            Properties c = new Properties();
            try (InputStream in = new FileInputStream(CONFIG)) { c.load(in); }
            String clientId = c.getProperty("client_id", "").trim();
            String secret = c.getProperty("client_secret", "").trim();

            SecureRandom rnd = new SecureRandom();
            byte[] vb = new byte[48]; rnd.nextBytes(vb);
            String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(vb);
            String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
            byte[] sb = new byte[16]; rnd.nextBytes(sb);
            String state = Base64.getUrlEncoder().withoutPadding().encodeToString(sb);

            com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                    new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0), 0);
            try {
                server.createContext("/", ex -> {
                    java.util.Map<String, String> q = new java.util.HashMap<>();
                    String raw = ex.getRequestURI().getRawQuery();
                    if (raw != null) for (String kv : raw.split("&")) {
                        int i = kv.indexOf('=');
                        if (i > 0) q.put(kv.substring(0, i), java.net.URLDecoder.decode(kv.substring(i + 1), "UTF-8"));
                    }
                    boolean relevant = q.containsKey("code") || q.containsKey("error");
                    String page = "<html><body style='font-family:sans-serif;text-align:center;margin-top:15%'>"
                            + "<h2>" + (relevant ? "You can close this tab and return to Royal Gambit." : "Royal Gambit") + "</h2></body></html>";
                    byte[] b = page.getBytes(StandardCharsets.UTF_8);
                    ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                    ex.sendResponseHeaders(200, b.length);
                    try (OutputStream o = ex.getResponseBody()) { o.write(b); }
                    if (relevant) session.result.complete(q);       // ignore stray requests such as /favicon.ico
                });
                server.start();
                String redirect = "http://127.0.0.1:" + server.getAddress().getPort();

                String url = "https://accounts.google.com/o/oauth2/v2/auth"
                        + "?client_id=" + enc(clientId)
                        + "&redirect_uri=" + enc(redirect)
                        + "&response_type=code"
                        + "&scope=" + enc("openid email profile")
                        + "&state=" + state
                        + "&code_challenge=" + challenge
                        + "&code_challenge_method=S256"
                        + "&prompt=select_account";
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                    throw new IOException("Cannot open a web browser on this computer");
                Desktop.getDesktop().browse(new java.net.URI(url));

                java.util.Map<String, String> q;
                try { q = session.result.get(3, java.util.concurrent.TimeUnit.MINUTES); }
                catch (java.util.concurrent.ExecutionException ee) {
                    if (ee.getCause() instanceof Cancelled) throw new Cancelled();
                    throw ee;
                }
                catch (java.util.concurrent.TimeoutException te) { throw new IOException("Google sign-in timed out. Please try again."); }

                if (q.containsKey("error")) throw new Cancelled();             // player pressed Cancel/Deny in the browser
                if (!state.equals(q.get("state"))) throw new IOException("Sign-in response did not match. Please try again.");

                String form = "code=" + enc(q.get("code"))
                        + "&client_id=" + enc(clientId)
                        + (secret.isEmpty() ? "" : "&client_secret=" + enc(secret))
                        + "&redirect_uri=" + enc(redirect)
                        + "&grant_type=authorization_code"
                        + "&code_verifier=" + enc(verifier);
                String json = post("https://oauth2.googleapis.com/token", form);
                String idToken = jsonStr(json, "id_token");
                if (idToken == null) throw new IOException("Google did not return an ID token");

                // The token came straight from Google over HTTPS, so its signature need not be checked again.
                String[] parts = idToken.split("\\.");
                if (parts.length < 2) throw new IOException("Invalid ID token");
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                if (!clientId.equals(jsonStr(payload, "aud"))) throw new IOException("ID token was issued for another app");
                String email = jsonStr(payload, "email");
                String verified = jsonRaw(payload, "email_verified");
                if (email == null || !"true".equals(verified)) throw new IOException("Your Google email is not verified");
                String name = jsonStr(payload, "name");
                return new String[] { email, name == null ? "" : name };
            } finally {
                server.stop(0);
            }
        }

        static String enc(String v) throws IOException { return java.net.URLEncoder.encode(v, "UTF-8"); }

        static String post(String url, String form) throws IOException {
            java.net.HttpURLConnection h = (java.net.HttpURLConnection) java.net.URI.create(url).toURL().openConnection();
            h.setConnectTimeout(15000);
            h.setReadTimeout(15000);
            h.setRequestMethod("POST");
            h.setDoOutput(true);
            h.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (OutputStream o = h.getOutputStream()) { o.write(form.getBytes(StandardCharsets.UTF_8)); }
            int code = h.getResponseCode();
            InputStream is = code >= 400 ? h.getErrorStream() : h.getInputStream();
            String body = "";
            if (is != null) try (InputStream in = is) { body = new String(in.readAllBytes(), StandardCharsets.UTF_8); }
            if (code >= 400) {
                String d = jsonStr(body, "error_description");
                if (d == null) d = jsonStr(body, "error");
                throw new IOException("Google said: " + (d == null ? "HTTP " + code : d));
            }
            return body;
        }

        // ----- tiny JSON helpers (the payloads are flat, so no library is needed) -----
        static String jsonStr(String json, String key) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
            if (!m.find()) return null;
            String v = m.group(1);
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < v.length(); i++) {
                char ch = v.charAt(i);
                if (ch != '\\' || i + 1 >= v.length()) { out.append(ch); continue; }
                char n = v.charAt(++i);
                switch (n) {
                    case 'n': out.append('\n'); break;
                    case 't': out.append('\t'); break;
                    case 'u': out.append((char) Integer.parseInt(v.substring(i + 1, i + 5), 16)); i += 4; break;
                    default: out.append(n);
                }
            }
            return out.toString();
        }
        static String jsonRaw(String json, String key) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\"" + key + "\"\\s*:\\s*\"?([A-Za-z0-9]+)\"?").matcher(json);
            return m.find() ? m.group(1) : null;
        }
    }

    // =====================================================================
    //  "Continue with Google" button
    // =====================================================================
    static class GoogleButton extends JButton {
        boolean hover;
        GoogleButton(String t) {
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
            aa(g);
            int w = getWidth(), h = getHeight() - getInsets().bottom;
            int r = Math.max(6, h / 6);
            g.setColor(getModel().isPressed() ? new Color(0xE3E3E3) : hover ? new Color(0xF4F4F4) : Color.WHITE);
            g.fill(new RoundRectangle2D.Float(0, 0, w, h, r, r));
            Font f = getFont();
            FontMetrics fm = g.getFontMetrics(f);
            Font gf = f.deriveFont(Font.BOLD, f.getSize2D() * 1.25f);
            FontMetrics gm = g.getFontMetrics(gf);
            int gap = Math.max(8, h / 4);
            int total = gm.stringWidth("G") + gap + fm.stringWidth(getText());
            int x = (w - total) / 2;
            g.setFont(gf);
            g.setColor(new Color(0x4285F4));
            g.drawString("G", x, (h - gm.getHeight()) / 2 + gm.getAscent());
            g.setFont(f);
            g.setColor(new Color(0x3C4043));
            g.drawString(getText(), x + gm.stringWidth("G") + gap, (h - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
        }
    }

    // =====================================================================
    //  Simple local user store (salted PBKDF2 hashes - replace with a server later)
    // =====================================================================
    static class UserStore {
        private final File file = new File(System.getProperty("user.home"), ".royalgambit-users.properties");
        private final Properties p = new Properties();

        UserStore() {
            if (file.exists()) try (InputStream in = new FileInputStream(file)) { p.load(in); } catch (IOException ignored) { }
        }
        static final int SUCCESS = 0, USERNAME_TAKEN = 1, EMAIL_TAKEN = 2;
        static final long RESET_TTL_MS = 5 * 60 * 1000L;      // reset codes are valid for 5 minutes

        /** @return true if any existing account already uses this email (case-insensitive) */
        synchronized boolean emailTaken(String email) {
            for (String key : p.stringPropertyNames())
                if (key.endsWith(".email") && p.getProperty(key).trim().equalsIgnoreCase(email.trim())) return true;
            return false;
        }
        /** @return SUCCESS, USERNAME_TAKEN or EMAIL_TAKEN */
        synchronized int register(String user, String email, String pw) throws Exception {
            String k = user.toLowerCase(Locale.ROOT);
            if (p.containsKey(k + ".hash") || p.containsKey(k + ".name")) return USERNAME_TAKEN;
            if (emailTaken(email)) return EMAIL_TAKEN;
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            p.setProperty(k + ".name", user);
            p.setProperty(k + ".email", email);
            p.setProperty(k + ".salt", Base64.getEncoder().encodeToString(salt));
            p.setProperty(k + ".hash", Base64.getEncoder().encodeToString(hash(pw.toCharArray(), salt)));
            save();
            return SUCCESS;
        }
        /**
         * Signs in (or creates) the account that belongs to a verified Google email.
         * Google accounts have no password of their own.
         * @return the Royal Gambit username
         */
        synchronized String loginWithGoogle(String email, String googleName) throws Exception {
            String existing = keyForEmail(email);
            if (existing != null) return p.getProperty(existing + ".name");

            String source = googleName == null || googleName.isBlank() ? email.substring(0, email.indexOf('@')) : googleName;
            String base = source.replaceAll("[^A-Za-z0-9_]", "");
            if (base.length() < 3) base = "Player" + base;
            if (base.length() > 12) base = base.substring(0, 12);
            String name = base;
            SecureRandom rnd = new SecureRandom();
            while (p.containsKey(name.toLowerCase(Locale.ROOT) + ".name"))
                name = base + String.format("%03d", rnd.nextInt(1000));
            String k = name.toLowerCase(Locale.ROOT);
            p.setProperty(k + ".name", name);
            p.setProperty(k + ".email", email);
            p.setProperty(k + ".google", "true");
            save();
            return name;
        }
        /** @return the account's display name on success, otherwise null */
        synchronized String login(String user, String pw) throws Exception {
            String k = user.toLowerCase(Locale.ROOT);
            String salt = p.getProperty(k + ".salt");
            if (salt == null) return null;
            byte[] expected = Base64.getDecoder().decode(p.getProperty(k + ".hash"));
            byte[] actual = hash(pw.toCharArray(), Base64.getDecoder().decode(salt));
            return MessageDigest.isEqual(expected, actual) ? p.getProperty(k + ".name") : null;
        }
        static byte[] hash(char[] pw, byte[] salt) throws Exception {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(new PBEKeySpec(pw, salt, 120_000, 256)).getEncoded();
        }

        // ----- password reset -----
        private void save() throws IOException {
            try (OutputStream out = new FileOutputStream(file)) { p.store(out, "Royal Gambit users"); }
        }
        private String keyForEmail(String email) {
            for (String key : p.stringPropertyNames())
                if (key.endsWith(".email") && p.getProperty(key).trim().equalsIgnoreCase(email.trim()))
                    return key.substring(0, key.length() - ".email".length());
            return null;
        }
        private void clearReset(String k) {
            p.remove(k + ".resetSalt"); p.remove(k + ".resetHash");
            p.remove(k + ".resetExp");  p.remove(k + ".resetTries");
        }

        /** @return {displayName, code} or null if no account uses this email */
        synchronized String[] createResetCode(String email) throws Exception {
            String k = keyForEmail(email);
            if (k == null) return null;
            SecureRandom rnd = new SecureRandom();
            String code = String.format("%06d", rnd.nextInt(1_000_000));
            byte[] salt = new byte[16];
            rnd.nextBytes(salt);
            p.setProperty(k + ".resetSalt", Base64.getEncoder().encodeToString(salt));
            p.setProperty(k + ".resetHash", Base64.getEncoder().encodeToString(hash(code.toCharArray(), salt)));
            p.setProperty(k + ".resetExp", String.valueOf(System.currentTimeMillis() + RESET_TTL_MS));
            p.setProperty(k + ".resetTries", "0");
            save();
            return new String[] { p.getProperty(k + ".name"), code };
        }

        /** @return null on success, otherwise an error message */
        synchronized String resetPassword(String email, String code, String newPw) throws Exception {
            String k = keyForEmail(email);
            if (k == null || p.getProperty(k + ".resetHash") == null) return "Invalid or expired code";
            int tries = Integer.parseInt(p.getProperty(k + ".resetTries", "0"));
            long exp = Long.parseLong(p.getProperty(k + ".resetExp", "0"));
            if (System.currentTimeMillis() > exp || tries >= 5) {
                clearReset(k); save();
                return "Code expired - please request a new one";
            }
            byte[] expected = Base64.getDecoder().decode(p.getProperty(k + ".resetHash"));
            byte[] actual = hash(code.toCharArray(), Base64.getDecoder().decode(p.getProperty(k + ".resetSalt")));
            if (!MessageDigest.isEqual(expected, actual)) {
                p.setProperty(k + ".resetTries", String.valueOf(tries + 1));
                save();
                return "Wrong code";
            }
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            p.setProperty(k + ".salt", Base64.getEncoder().encodeToString(salt));
            p.setProperty(k + ".hash", Base64.getEncoder().encodeToString(hash(newPw.toCharArray(), salt)));
            clearReset(k);
            save();
            return null;
        }
    }

    // =====================================================================
    //  The form card (Create Account <-> Welcome Back)
    // =====================================================================
    static class AuthForm extends JPanel {
        final UserStore store = new UserStore();
        final Preferences prefs = Preferences.userNodeForPackage(RoyalGambit.class);
        boolean registerMode = true;
        int fh = 44;

        final JLabel titleLbl = new JLabel("Create Account", SwingConstants.CENTER);
        final Input user = new Input("Username", false);
        final Input email = new Input("Email", false);
        final Input pass = new Input("Password", true);
        final GoldCheck terms = new GoldCheck(), remember = new GoldCheck();
        final JLabel termsText = lbl("I agree to the"), rememberText = lbl("Remember me");
        final JLabel msg = lbl(" "), switchText = lbl("Already have an account?");
        final Link termsLink = new Link("Terms of Service", this::showTerms);
        final Link forgot = new Link("Forgot password?", this::forgotPassword);
        final Link switchLink = new Link("Log In", () -> setMode(!registerMode));
        final GoldButton submit = new GoldButton("REGISTER");
        final GoogleButton googleBtn = new GoogleButton("Continue with Google");
        final JPanel termsRow = row(), loginRow = row(), switchRow = row(), guestRow = row();
        final Link guestLink = new Link("Play as Guest", () -> onGuest(this));

        static JPanel row() {
            JPanel p = new JPanel();
            p.setOpaque(false);
            p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
            return p;
        }

        AuthForm() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            titleLbl.setForeground(GOLD);
            msg.setHorizontalAlignment(SwingConstants.CENTER);

            termsRow.add(terms);
            termsRow.add(Box.createHorizontalStrut(10));
            termsRow.add(termsText);
            termsRow.add(Box.createHorizontalStrut(5));
            termsRow.add(termsLink);
            termsRow.add(Box.createHorizontalGlue());

            loginRow.add(remember);
            loginRow.add(Box.createHorizontalStrut(10));
            loginRow.add(rememberText);
            loginRow.add(Box.createHorizontalGlue());
            loginRow.add(forgot);
            rememberText.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { remember.toggle(); }
            });

            switchRow.add(Box.createHorizontalGlue());
            switchRow.add(switchText);
            switchRow.add(Box.createHorizontalStrut(6));
            switchRow.add(switchLink);
            switchRow.add(Box.createHorizontalGlue());

            guestRow.add(Box.createHorizontalGlue());
            guestRow.add(guestLink);
            guestRow.add(Box.createHorizontalGlue());

            JComponent[] all = { titleLbl, user, email, pass, termsRow, loginRow, msg, submit, googleBtn, switchRow, guestRow };
            for (JComponent c : all) { c.setAlignmentX(Component.CENTER_ALIGNMENT); add(c); }
            loginRow.setVisible(false);

            submit.addActionListener(e -> submit());
            googleBtn.addActionListener(e -> googleSignIn());
            for (Input in : new Input[] { user, email, pass }) {
                in.field.addActionListener(e -> submit.doClick());
                in.field.addKeyListener(new KeyAdapter() {
                    @Override public void keyTyped(KeyEvent e) { clearErrors(); }
                });
            }
        }

        // ----- responsive sizing -----
        void applyScale(int rootW, int rootH) {
            fh = clamp((int) (rootH * 0.058), 34, 56);
            int formW = Math.min(clamp((int) (rootW * 0.36), 300, 520), rootW - 32);
            int pad = Math.max(16, (int) (formW * 0.11));
            int innerW = formW - 2 * pad;
            int gap = Math.max(8, (int) (fh * 0.28));
            float base = Math.max(12f, fh * 0.36f);

            setBorder(new EmptyBorder((int) (pad * 0.85), pad, (int) (pad * 0.75), pad));
            fix(this, formW, 10); // width fixed, height comes from the layout
            setMaximumSize(new Dimension(formW, Short.MAX_VALUE));
            setPreferredSize(null);
            setMinimumSize(new Dimension(formW, 0));

            int titlePx = Math.round(base * 1.9f);
            titleLbl.setFont(new Font(SANS, Font.PLAIN, titlePx));
            int titleBottom = (int) (gap * 1.5);
            titleLbl.setBorder(new EmptyBorder(0, 0, titleBottom, 0));
            fix(titleLbl, innerW, (int) (titlePx * 1.3) + titleBottom);

            user.scale(innerW, fh, gap, base * 1.1f);
            email.scale(innerW, fh, gap, base * 1.1f);
            pass.scale(innerW, fh, gap, base * 1.1f);

            Font small = new Font(SANS, Font.PLAIN, Math.round(base));
            for (JLabel l : new JLabel[] { termsText, rememberText, switchText }) l.setFont(small);
            termsLink.size(base); forgot.size(base); switchLink.size(base); guestLink.size(base);
            msg.setFont(new Font(SANS, Font.PLAIN, Math.round(base * 0.95f)));

            int cb = Math.round(base * 1.5f);
            fix(terms, cb, cb); fix(remember, cb, cb);
            int rowH = cb + 4;
            for (JPanel r : new JPanel[] { termsRow, loginRow }) {
                r.setBorder(new EmptyBorder(0, 0, gap, 0));
                fix(r, innerW, rowH + gap);
            }
            int msgH = (int) (base * 1.5);
            msg.setBorder(new EmptyBorder(0, 0, gap / 2, 0));
            fix(msg, innerW, msgH + gap / 2);

            int btnGap = (int) (gap * 1.2);
            submit.setBorder(new EmptyBorder(0, 0, btnGap, 0));
            submit.setFont(new Font(SANS, Font.BOLD, Math.round(base * 1.15f)));
            fix(submit, innerW, fh + btnGap);
            googleBtn.setBorder(new EmptyBorder(0, 0, btnGap, 0));
            googleBtn.setFont(new Font(SANS, Font.BOLD, Math.round(base * 1.05f)));
            fix(googleBtn, innerW, fh + btnGap);

            fix(switchRow, innerW, (int) (base * 1.8));
            guestRow.setBorder(new EmptyBorder((int) (gap * 0.6), 0, 0, 0));
            fix(guestRow, innerW, (int) (base * 1.8) + (int) (gap * 0.6));
            revalidate();
            repaint();
        }

        // ----- mode switch -----
        void setMode(boolean register) {
            registerMode = register;
            titleLbl.setText(register ? "Create Account" : "Welcome Back");
            email.setVisible(register);
            termsRow.setVisible(register);
            loginRow.setVisible(!register);
            submit.setText(register ? "REGISTER" : "LOG IN");
            switchText.setText(register ? "Already have an account?" : "New to Royal Gambit?");
            switchLink.setText(register ? "Log In" : "Create Account");
            email.set(""); pass.set("");
            clearErrors();
            String saved = prefs.get("lastUser", "");
            user.set(register ? "" : saved);
            remember.setSelected(!register && !saved.isEmpty());
            revalidate();
            repaint();
            SwingUtilities.invokeLater(() -> (user.text().isEmpty() ? user : pass).field.requestFocusInWindow());
        }

        void clearErrors() {
            user.setError(false); email.setError(false); pass.setError(false);
            msg.setText(" ");
        }
        void fail(Input in, String text) {
            clearErrors();
            if (in != null) { in.setError(true); in.field.requestFocusInWindow(); }
            msg.setForeground(new Color(0xE5736A));
            msg.setText(text);
        }

        // ----- actions -----
        void submit() {
            String u = user.text().trim(), p = pass.text();
            try {
                if (registerMode) {
                    String e = email.text().trim();
                    if (!u.matches("[A-Za-z0-9_]{3,16}")) { fail(user, "Username: 3-16 letters, numbers or _"); return; }
                    if (!e.matches("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$")) { fail(email, "Please enter a valid email"); return; }
                    if (p.length() < 6) { fail(pass, "Password must be at least 6 characters"); return; }
                    if (!terms.selected) { fail(null, "Please accept the Terms of Service"); return; }
                    int result = store.register(u, e, p);
                    if (result == UserStore.USERNAME_TAKEN) { fail(user, "That username is already taken"); return; }
                    if (result == UserStore.EMAIL_TAKEN) { fail(email, "This email account is already taken"); return; }
                    setMode(false);
                    user.set(u);
                    msg.setForeground(OK);
                    msg.setText("Account created - please log in");
                    pass.field.requestFocusInWindow();
                } else {
                    if (u.isEmpty()) { fail(user, "Enter your username"); return; }
                    if (p.isEmpty()) { fail(pass, "Enter your password"); return; }
                    String name = store.login(u, p);
                    if (name == null) { fail(pass, "Wrong username or password"); return; }
                    if (remember.selected) prefs.put("lastUser", name); else prefs.remove("lastUser");
                    onLoginSuccess(this, name);
                }
            } catch (Exception ex) {
                fail(null, "Something went wrong: " + ex.getMessage());
            }
        }

        void googleSignIn() {
            if (registerMode && !terms.selected) { fail(null, "Please accept the Terms of Service"); return; }
            if (!GoogleAuth.configured()) {
                JOptionPane.showMessageDialog(this,
                        "Google sign-in is not set up on this computer yet.\n\n"
                        + "Create the file:\n" + GoogleAuth.CONFIG.getAbsolutePath() + "\n"
                        + "with these two lines:\nclient_id=YOUR_CLIENT_ID\nclient_secret=YOUR_CLIENT_SECRET",
                        "Google sign-in", JOptionPane.WARNING_MESSAGE);
                return;
            }
            clearErrors();
            final GoogleAuth.Session session = new GoogleAuth.Session();

            final JDialog wait = new JDialog(SwingUtilities.getWindowAncestor(this), "Google sign-in",
                    Dialog.ModalityType.APPLICATION_MODAL);
            wait.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            wait.setResizable(false);
            JProgressBar bar = new JProgressBar();
            bar.setIndeterminate(true);
            JButton cancel = new JButton("Cancel");
            cancel.addActionListener(ev -> session.cancel());
            wait.addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent we) { session.cancel(); }
            });
            JPanel box = new JPanel(new BorderLayout(0, 12));
            box.setBorder(new EmptyBorder(18, 24, 14, 24));
            box.add(new JLabel("Finish signing in with Google in your browser...", SwingConstants.CENTER), BorderLayout.NORTH);
            box.add(bar, BorderLayout.CENTER);
            JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            south.add(cancel);
            box.add(south, BorderLayout.SOUTH);
            wait.getContentPane().add(box);
            wait.pack();
            wait.setSize(Math.max(wait.getWidth(), 360), wait.getHeight());
            wait.setLocationRelativeTo(this);

            new SwingWorker<String[], Void>() {
                @Override protected String[] doInBackground() throws Exception { return GoogleAuth.signIn(session); }
                @Override protected void done() {
                    wait.dispose();
                    try {
                        String[] g = get();
                        String name = store.loginWithGoogle(g[0], g[1]);
                        prefs.remove("lastUser");
                        onLoginSuccess(AuthForm.this, name);
                    } catch (java.util.concurrent.ExecutionException ee) {
                        Throwable c = ee.getCause();
                        if (c instanceof GoogleAuth.Cancelled) { msg.setText(" "); return; }
                        fail(null, "Google sign-in failed: " + (c instanceof Exception ? Mailer.friendly((Exception) c) : c));
                    } catch (Exception ex) {
                        fail(null, "Google sign-in failed: " + ex.getMessage());
                    }
                }
            }.execute();
            wait.setVisible(true);          // blocks until done() closes it (or the player cancels)
        }

        /** Creates a fresh code (replacing any old one) and emails it. @return null if the email is unknown. */
        String[] issueAndSendCode(String email) throws Exception {
            String[] r = store.createResetCode(email);
            if (r == null) return null;                           // unknown email: say nothing
            String body = "Hi " + r[0] + ",\n\n"
                    + "We received a request to reset your Royal Gambit password.\n"
                    + "Your reset code is: " + r[1] + "\n\n"
                    + "It expires in 5 minutes. If you didn't ask for this, you can ignore this email.\n\n"
                    + "- Royal Gambit";
            if (Mailer.configured()) Mailer.send(email, "Royal Gambit - password reset code", body);
            else System.out.println("[DEV] No mail config found. Reset code for " + email + ": " + r[1]);
            return r;
        }

        void forgotPassword() {
            String e = JOptionPane.showInputDialog(this, "Enter the email linked to your account:",
                    "Reset password", JOptionPane.PLAIN_MESSAGE);
            if (e == null || e.isBlank()) return;
            final String email = e.trim();
            if (!email.matches("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$")) {
                JOptionPane.showMessageDialog(this, "Please enter a valid email account.",
                        "Invalid email", JOptionPane.ERROR_MESSAGE);
                return;
            }

            msg.setForeground(MUTED);
            msg.setText("Sending reset code...");
            final LoadingDialog loading = new LoadingDialog(this, "Sending reset code...");
            loading.start();
            new SwingWorker<String, Void>() {
                @Override protected String doInBackground() {
                    try {
                        // null result = code sent; otherwise an error message
                        return issueAndSendCode(email) == null ? "NOT_FOUND" : null;
                    } catch (Exception ex) { return Mailer.friendly(ex); }
                }
                @Override protected void done() {
                    loading.close();
                    String err;
                    try { err = get(); } catch (Exception ex) { err = ex.getMessage(); }
                    if ("NOT_FOUND".equals(err)) {
                        msg.setText(" ");
                        JOptionPane.showMessageDialog(AuthForm.this,
                                "No account is linked to that email. Please enter a valid email account.",
                                "Invalid email", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    if (err != null) { fail(null, "Could not send email: " + err); return; }
                    msg.setForeground(OK);
                    msg.setText("A reset code was sent to your email");
                    showResetDialog(email);
                }
            }.execute();
        }

        static final int RESEND_COOLDOWN_SEC = 30;

        void showResetDialog(String email) {
            final Color okGreen = new Color(0x2E7D32);
            JDialog d = new JDialog(SwingUtilities.getWindowAncestor(this), "Create a new password",
                    Dialog.ModalityType.APPLICATION_MODAL);
            d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

            JTextField code = new JTextField();
            JPasswordField n1 = new JPasswordField(), n2 = new JPasswordField();
            JLabel timerLbl = new JLabel(" ");
            JLabel err = new JLabel(" ");
            JButton resend = new JButton("Resend code");
            JButton cancel = new JButton("Cancel");
            JButton reset = new JButton("Reset password");

            JPanel p = new JPanel(new GridLayout(0, 1, 0, 6));
            p.setBorder(new EmptyBorder(16, 16, 4, 16));
            p.add(new JLabel("6-digit code from your email:")); p.add(code);
            p.add(timerLbl);
            p.add(new JLabel("New password:"));                 p.add(n1);
            p.add(new JLabel("Confirm new password:"));         p.add(n2);
            p.add(err);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
            buttons.add(resend); buttons.add(cancel); buttons.add(reset);

            long[] expiresAt = { System.currentTimeMillis() + UserStore.RESET_TTL_MS };   // 5 minutes
            long[] resendAt  = { System.currentTimeMillis() + RESEND_COOLDOWN_SEC * 1000L };
            boolean[] sending = { false };

            Runnable refresh = () -> {
                long now = System.currentTimeMillis();
                long left = expiresAt[0] - now;
                if (left <= 0) {
                    timerLbl.setText("Code expired - click Resend code");
                    timerLbl.setForeground(RoyalGambit.ERROR);
                    reset.setEnabled(false);
                } else {
                    long s = (left + 999) / 1000;
                    timerLbl.setText(String.format("Code expires in %d:%02d", s / 60, s % 60));
                    timerLbl.setForeground(Color.DARK_GRAY);
                    reset.setEnabled(true);
                }
                long wait = (resendAt[0] - now + 999) / 1000;
                if (sending[0])    { resend.setEnabled(false); resend.setText("Sending..."); }
                else if (wait > 0) { resend.setEnabled(false); resend.setText("Resend code (" + wait + "s)"); }
                else               { resend.setEnabled(true);  resend.setText("Resend code"); }
            };
            Timer tick = new Timer(250, ev -> refresh.run());

            resend.addActionListener(ev -> {
                sending[0] = true;
                err.setText(" ");
                refresh.run();
                final LoadingDialog loading = new LoadingDialog(d, "Sending a new code...");
                loading.start();
                new SwingWorker<String, Void>() {
                    @Override protected String doInBackground() {
                        try { issueAndSendCode(email); return null; }
                        catch (Exception ex) { return Mailer.friendly(ex); }
                    }
                    @Override protected void done() {
                        loading.close();
                        sending[0] = false;
                        String er;
                        try { er = get(); } catch (Exception ex) { er = ex.getMessage(); }
                        if (er != null) {
                            err.setForeground(RoyalGambit.ERROR);
                            err.setText("Could not send email: " + er);
                        } else {
                            long now = System.currentTimeMillis();
                            expiresAt[0] = now + UserStore.RESET_TTL_MS;          // new code = new 5 minutes
                            resendAt[0]  = now + RESEND_COOLDOWN_SEC * 1000L;
                            code.setText("");
                            err.setForeground(okGreen);
                            err.setText("A new code was sent (the old one no longer works)");
                            code.requestFocusInWindow();
                        }
                        refresh.run();
                    }
                }.execute();
            });

            cancel.addActionListener(ev -> d.dispose());

            reset.addActionListener(ev -> {
                String np = new String(n1.getPassword());
                String er;
                if (code.getText().trim().isEmpty()) er = "Enter the 6-digit code";
                else if (np.length() < 6) er = "Password must be at least 6 characters";
                else if (!np.equals(new String(n2.getPassword()))) er = "Passwords do not match";
                else {
                    try { er = store.resetPassword(email, code.getText().trim(), np); }
                    catch (Exception ex) { er = "Something went wrong: " + ex.getMessage(); }
                }
                if (er == null) {
                    d.dispose();
                    setMode(false);                       // go to "Welcome Back"
                    msg.setForeground(OK);
                    msg.setText("Password updated - please log in");
                    JOptionPane.showMessageDialog(this, "You successfully reset your password.",
                            "Password reset", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    err.setForeground(RoyalGambit.ERROR);
                    err.setText(er);
                }
            });

            d.addWindowListener(new WindowAdapter() {
                @Override public void windowClosed(WindowEvent we) { tick.stop(); }
            });
            d.getContentPane().add(p, BorderLayout.CENTER);
            d.getContentPane().add(buttons, BorderLayout.SOUTH);
            d.getRootPane().setDefaultButton(reset);
            d.pack();
            d.setSize(Math.max(d.getWidth(), 420), d.getHeight());
            d.setResizable(false);
            d.setLocationRelativeTo(this);
            refresh.run();
            tick.start();
            d.setVisible(true);
        }

        void showTerms() {
            JOptionPane.showMessageDialog(this,
                    "Royal Gambit Terms of Service\n\nPlay fair, no cheating, respect other players.\n(Replace this text with your real terms.)",
                    "Terms of Service", JOptionPane.INFORMATION_MESSAGE);
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            int w = getWidth(), h = getHeight();
            g.setColor(new Color(0, 0, 0, 40));
            g.fill(new RoundRectangle2D.Float(2, 5, w - 4, h - 4, 26, 26));
            g.setColor(PANEL);
            g.fill(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 26, 26));
            g.setColor(new Color(255, 255, 255, 12));
            g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 2, h - 2, 26, 26));
            g.dispose();
        }
    }

    /** Guest: skips login/register. Nothing is saved to this computer. */
    static void onGuest(Component parent) {
        int c = JOptionPane.showConfirmDialog(parent,
                "Play as a guest?\n\nYour progress, stats and settings will NOT be saved.\nCreate an account any time to keep your data.",
                "Play as Guest", JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE);
        if (c != JOptionPane.OK_OPTION) return;
        Window w = SwingUtilities.getWindowAncestor(parent);
        if (w != null) w.dispose();
        Page.openGuest();
    }

    /** Hook: open your main menu / lobby here. */
    static void onLoginSuccess(Component parent, String username) {
        Window w = SwingUtilities.getWindowAncestor(parent);
        if (w != null) w.dispose();
        Page.open(username);
    }

    static final String TAGLINE = "PLAY. OUTSMART. REIGN.";

    /** Small, widely letter-spaced tagline shown under the ROYAL GAMBIT title. */
    static Font taglineFont(int size) {
        java.util.Map<java.awt.font.TextAttribute, Object> a = new java.util.HashMap<>();
        a.put(java.awt.font.TextAttribute.FAMILY, SANS);
        a.put(java.awt.font.TextAttribute.SIZE, (float) size);
        a.put(java.awt.font.TextAttribute.WEIGHT, java.awt.font.TextAttribute.WEIGHT_BOLD);
        a.put(java.awt.font.TextAttribute.TRACKING, 0.18f);
        return new Font(a);
    }

    // =====================================================================
    //  Window
    // =====================================================================
    static class App extends JFrame {
        final Backdrop root = new Backdrop();
        final JLabel title = new JLabel("ROYAL GAMBIT", SwingConstants.CENTER);
        final JLabel tagline = new JLabel(TAGLINE, SwingConstants.CENTER);
        final AuthForm form = new AuthForm();

        App() {
            super("Royal Gambit");
            setDefaultCloseOperation(EXIT_ON_CLOSE);

            JPanel content = new JPanel();
            content.setOpaque(false);
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            title.setForeground(BG_IMAGE != null ? NAVY : GOLD);
            tagline.setForeground(BG_IMAGE != null ? GOLD_DEEP : MUTED);
            title.setAlignmentX(Component.CENTER_ALIGNMENT);
            tagline.setAlignmentX(Component.CENTER_ALIGNMENT);
            form.setAlignmentX(Component.CENTER_ALIGNMENT);
            content.add(title);
            content.add(tagline);
            content.add(form);
            root.add(content, new GridBagConstraints());

            root.addComponentListener(new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent e) { relayout(); }
            });
            setContentPane(root);
            setMinimumSize(new Dimension(420, 620));
            setSize(1280, 720);
            setLocationRelativeTo(null);
            relayout();
        }

        void relayout() {
            int w = root.getWidth(), h = root.getHeight();
            if (w <= 0 || h <= 0) { w = 1280; h = 720; }
            form.applyScale(w, h);
            int fh = form.fh;

            int s = Math.max(22, Math.min((int) (fh * 1.7), (w - 32) / 9));
            int top = (int) (s * 0.25), bottom = (int) (fh * 0.5);
            title.setFont(new Font(SERIF, Font.PLAIN, s));
            title.setBorder(new EmptyBorder(top, 0, 2, 0));
            fix(title, Math.max(200, w - 32), (int) (s * 1.3) + top + 2);

            int ts = Math.max(11, (int) (s * 0.24));
            tagline.setFont(taglineFont(ts));
            tagline.setBorder(new EmptyBorder(0, 0, bottom, 0));
            fix(tagline, Math.max(200, w - 32), (int) (ts * 1.5) + bottom);

            root.revalidate();
            root.repaint();
        }
    }

    // =====================================================================
    //  Main menu (shown after login): Player vs Bot, Player vs Player, Levels, Settings
    // =====================================================================
    static class MainMenu extends JFrame {

        static final String[] LEVELS = { "Easy", "Medium", "Hard", "Extreme" };
        static String level = LEVELS[0];          // bot difficulty chosen in LEVELS

        // =====================================================================
        //  Hooks - plug your real screens in here
        // =====================================================================
        static void onPlayVsBot(Component parent, String player) {
            info(parent, "Player vs Bot  (" + level + ")\nOpen your game board here.");
        }
        static void onPlayVsPlayer(Component parent, String player) {
            info(parent, "Player vs Player\nOpen your matchmaking / game board here.");
        }
        static void onLevels(Component parent) {
            Object pick = JOptionPane.showInputDialog(parent, "Choose bot difficulty:", "Levels",
                    JOptionPane.PLAIN_MESSAGE, null, LEVELS, level);
            if (pick != null) level = pick.toString();
        }
        static void onSettings(Component parent) {
            info(parent, "Settings\n(sound volume, language, ...)");
        }
        static void info(Component parent, String text) {
            JOptionPane.showMessageDialog(parent, text, "Royal Gambit", JOptionPane.INFORMATION_MESSAGE);
        }

        // =====================================================================
        //  Background: soft radial glow, no board
        // =====================================================================
        static class Bg extends JPanel {
            Bg() { setLayout(new BorderLayout()); }
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                aa(g);
                int w = getWidth(), h = getHeight();
                if (paintCover(g, w, h)) { g.dispose(); return; }
                g.setColor(BG_DARK);
                g.fillRect(0, 0, w, h);
                float r = Math.max(w, h) * 0.65f;
                g.setPaint(new RadialGradientPaint(new Point2D.Float(w / 2f, h * 0.42f), r,
                        new float[] { 0f, 1f }, new Color[] { BG_LIGHT, BG_DARK }));
                g.fillRect(0, 0, w, h);
                g.dispose();
            }
        }

        // =====================================================================
        //  Outlined gold button (secondary actions)
        // =====================================================================
        static class OutlineButton extends JButton {
            boolean hover;
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
                aa(g);
                int w = getWidth(), h = getHeight() - getInsets().bottom;
                int r = Math.max(6, h / 6);
                Shape s = new RoundRectangle2D.Float(1, 1, w - 2, h - 2, r, r);
                if (hover || getModel().isPressed()) {
                    g.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), getModel().isPressed() ? 50 : 30));
                    g.fill(s);
                }
                g.setColor(BG_IMAGE != null ? (hover ? GOLD_DARK : GOLD_DEEP) : (hover ? GOLD_LIGHT : GOLD));
                g.setStroke(new BasicStroke(1.6f));
                g.draw(s);
                g.setFont(getFont());
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
                aa(g);
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
        //  Window
        // =====================================================================
        final String player;
        final Bg root = new Bg();
        final JLabel title = new JLabel("ROYAL GAMBIT", SwingConstants.CENTER);
        final JLabel tagline = new JLabel(TAGLINE, SwingConstants.CENTER);
        final JLabel welcome;
        final GoldButton vsBot = new GoldButton("PLAYER VS BOT");
        final OutlineButton vsPlayer = new OutlineButton("PLAYER VS PLAYER");
        final OutlineButton levels = new OutlineButton("LEVELS");
        final GearButton gear;
        final JPanel topBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));

        MainMenu(String player) {
            super("Royal Gambit");
            this.player = player;
            setDefaultCloseOperation(EXIT_ON_CLOSE);

            welcome = new JLabel(player == null || player.isEmpty() ? " " : "Welcome, " + player, SwingConstants.CENTER);
            boolean light = BG_IMAGE != null;
            welcome.setForeground(light ? new Color(0x5A5F6B) : MUTED);
            title.setForeground(light ? NAVY : GOLD);
            tagline.setForeground(light ? GOLD_DEEP : MUTED);

            gear = new GearButton(() -> onSettings(this));
            topBar.setOpaque(false);
            topBar.add(gear);

            vsBot.addActionListener(e -> onPlayVsBot(this, player));
            vsPlayer.addActionListener(e -> onPlayVsPlayer(this, player));
            levels.addActionListener(e -> onLevels(this));
            vsPlayer.setForeground(light ? GOLD_DEEP : GOLD);
            levels.setForeground(light ? GOLD_DEEP : GOLD);

            JPanel content = new JPanel();
            content.setOpaque(false);
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            for (JComponent c : new JComponent[] { title, tagline, welcome, vsBot, vsPlayer, levels }) {
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
            setMinimumSize(new Dimension(420, 560));
            setSize(1280, 720);
            setLocationRelativeTo(null);
            relayout();
        }

        void relayout() {
            int w = root.getWidth(), h = root.getHeight();
            if (w <= 0 || h <= 0) { w = 1280; h = 720; }

            int bh = clamp((int) (h * 0.075), 40, 60);
            int bw = Math.min(clamp((int) (w * 0.30), 260, 420), w - 48);
            int gap = (int) (bh * 0.3);
            float base = Math.max(13f, bh * 0.34f);

            int gs = clamp((int) (bh * 0.7), 28, 40);
            fix(gear, gs, gs);
            topBar.setBorder(new EmptyBorder(16, 16, 0, 20));

            int s = Math.max(24, Math.min((int) (bh * 1.6), (w - 32) / 9));
            int top = (int) (s * 0.25);
            title.setFont(new Font(SERIF, Font.PLAIN, s));
            title.setBorder(new EmptyBorder(top, 0, 2, 0));
            fix(title, Math.max(200, w - 32), (int) (s * 1.3) + top + 2);

            int ts = Math.max(11, (int) (s * 0.24));
            int tb = (int) (bh * 0.35);
            tagline.setFont(taglineFont(ts));
            tagline.setBorder(new EmptyBorder(0, 0, tb, 0));
            fix(tagline, Math.max(200, w - 32), (int) (ts * 1.5) + tb);

            int wb = (int) (bh * 0.7);
            welcome.setFont(new Font(SANS, Font.PLAIN, Math.round(base)));
            welcome.setBorder(new EmptyBorder(0, 0, wb, 0));
            fix(welcome, bw, (int) (base * 1.5) + wb);

            Font bf = new Font(SANS, Font.BOLD, Math.round(base * 1.1f));
            for (AbstractButton b : new AbstractButton[] { vsBot, vsPlayer, levels }) {
                b.setFont(bf);
                b.setBorder(new EmptyBorder(0, 0, gap, 0));
                fix((JComponent) b, bw, bh + gap);
            }

            root.revalidate();
            root.repaint();
        }

        /** Call this after a successful login. */
        static void open(String username) {
            SwingUtilities.invokeLater(() -> new MainMenu(username).setVisible(true));
        }
    }

    /** Shows the login screen (used at startup and after Log Out). */
    static void showLogin() {
        SwingUtilities.invokeLater(() -> {
            App app = new App();
            app.form.setMode(false);                    // open on "Welcome Back"
            app.setVisible(true);
            app.form.pass.field.requestFocusInWindow();
        });
    }

    /** Opens the Create Account screen (shown at startup). */
    static void showRegister() {
        App app = new App();
        app.setVisible(true);
        app.form.user.field.requestFocusInWindow(); // gold focus ring on Username, like the reference
    }

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        // Loading splash first, then the Create Account screen
        SwingUtilities.invokeLater(() -> LoadingSplash.show(RoyalGambit::showRegister));
    }
}
