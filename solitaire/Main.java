package solitaire;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] a) {
        if (a.length > 0 && a[0].equals("sim")) { sim(a.length > 1 ? Integer.parseInt(a[1]) : 1000); return; }
        SwingUtilities.invokeLater(Main::gui);
    }

    static void sim(int n) {                                        // RF03 estatísticas + RF25 verificação
        int wins = 0, bad = 0, noprog = 0; long tot = 0;
        for (int s = 0; s < n; s++) {
            Game g = new Game(new Random(s)); String ini = g.snapshot();
            int steps = 0; while (steps < 5000 && g.autoStep()) steps++;
            if (steps >= 5000) noprog++;
            if (g.isWon()) wins++;
            tot += g.score();
            g.undoAll();
            if (!g.snapshot().equals(ini)) bad++;
        }
        System.out.printf("Partidas: %d | vitórias: %d | pontuação média: %.2f | loops: %d | falhas no desfazer: %d%n",
                n, wins, tot / (double) n, noprog, bad);
    }

    static void gui() {
        Game g = new Game();
        JFrame f = new JFrame("Paciência (Solitaire)");
        Board b = new Board(g);
        JButton undo = new JButton("Desfazer"), auto = new JButton("Jogada automática");
        JCheckBox on = new JCheckBox("Estratégia ativada", true);
        undo.addActionListener(e -> g.undo());
        auto.addActionListener(e -> g.autoStep());
        on.addActionListener(e -> g.setStrategy(on.isSelected() ? new AutoStrategy() : new NoStrategy()));
        JPanel bar = new JPanel(); bar.add(undo); bar.add(auto); bar.add(on);
        f.add(new JScrollPane(b), BorderLayout.CENTER); f.add(bar, BorderLayout.SOUTH);
        f.setSize(840, 760); f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); f.setVisible(true);
    }

    static class Board extends JPanel {
        static final int W = 84, H = 116, G = 18, X0 = 20, Y0 = 20, TY = Y0 + H + 30, DOWN = 16, UP = 28;
        final Game g; List<Card> drag; int gx, gy; Point mouse; PileView hover; boolean ok;

        Board(Game g) {
            this.g = g;
            setBackground(new Color(0, 110, 60));
            setPreferredSize(new Dimension(X0 * 2 + 7 * (W + G) - G, 800));
            g.addListener(() -> {
                repaint();
                if (g.isWon()) SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Você venceu!"));
            });
            MouseAdapter m = new MouseAdapter() {
                public void mousePressed(MouseEvent e) { press(e.getPoint()); }
                public void mouseDragged(MouseEvent e) {
                    if (drag == null) return;
                    mouse = e.getPoint(); hover = pileAt(mouse);
                    ok = hover != null && g.canMove(drag.get(0), hover);   // RNF03: regra vem do jogo
                    repaint();
                }
                public void mouseReleased(MouseEvent e) {
                    if (drag == null) return;
                    PileView d = pileAt(e.getPoint());
                    Card first = drag.get(0);
                    drag = null; hover = null;
                    if (d != null) g.move(first, d);
                    repaint();
                }
            };
            addMouseListener(m); addMouseMotionListener(m);
        }

        int cx(int i) { return X0 + i * (W + G); }
        int px(PileView p) {
            switch (p.kind()) { case STOCK: return cx(0); case WASTE: return cx(1); case FOUNDATION: return cx(3 + p.index()); default: return cx(p.index()); }
        }
        int cy(PileView p, int idx) {
            if (p.kind() != Kind.COLUMN) return Y0;
            int y = TY; List<Card> cs = p.cards();
            for (int i = 0; i < idx; i++) y += cs.get(i).isFaceUp() ? UP : DOWN;
            return y;
        }
        List<PileView> piles() {
            List<PileView> l = new ArrayList<>(List.of(g.stock(), g.waste()));
            for (int i = 0; i < 4; i++) l.add(g.foundation(i));
            for (int i = 0; i < 7; i++) l.add(g.column(i));
            return l;
        }
        PileView pileAt(Point p) {
            for (PileView v : piles()) {
                int x = px(v);
                if (p.x < x || p.x > x + W) continue;
                if (v.kind() == Kind.COLUMN ? p.y >= TY : (p.y >= Y0 && p.y <= Y0 + H)) return v;
            }
            return null;
        }
        int hitIndex(PileView p, int y) {
            int n = p.size();
            for (int i = n - 1; i >= 0; i--) {
                int top = cy(p, i), bottom = i == n - 1 ? top + H : cy(p, i + 1);
                if (y >= top && y < bottom) return i;
            }
            return -1;
        }

        void press(Point pt) {
            PileView p = pileAt(pt);
            if (p == null) return;
            if (p.kind() == Kind.STOCK) {                           // comprar ou, se vazio, nova partida (RF01)
                if (!p.isEmpty()) g.draw();
                else if (JOptionPane.showConfirmDialog(this, "Iniciar nova partida?", "Monte vazio", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) g.newGame();
                return;
            }
            if (p.isEmpty()) return;
            int idx = p.kind() == Kind.COLUMN ? hitIndex(p, pt.y) : p.size() - 1;
            if (idx < 0) return;
            Card c = p.cards().get(idx);
            if (g.locationOf(c) == null) return;
            List<Card> seq = p.kind() == Kind.COLUMN ? g.sequenceFrom(c) : List.of(c);   // RF18, RF32
            String payload = seq.stream().map(Card::id).collect(Collectors.joining(","));  // RNF05
            drag = Arrays.stream(payload.split(",")).map(g::cardById).collect(Collectors.toList());
            gx = pt.x - px(p); gy = pt.y - cy(p, idx); mouse = pt; hover = null;
        }

        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g2 = (Graphics2D) g0;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (PileView p : piles()) {
                int x = px(p);
                g2.setColor(new Color(255, 255, 255, 70)); g2.drawRoundRect(x, cy(p, 0), W, H, 10, 10);
                if (p.kind() == Kind.STOCK) {
                    if (p.isEmpty()) { g2.setFont(new Font("Dialog", Font.BOLD, 30)); g2.drawString("↻", x + 30, Y0 + 70); }
                    else card(g2, p.top(), x, Y0);
                } else if (p.kind() == Kind.COLUMN) {
                    List<Card> cs = p.cards();
                    for (int i = 0; i < cs.size(); i++) if (!inDrag(cs.get(i))) card(g2, cs.get(i), x, cy(p, i));
                } else {
                    List<Card> cs = p.cards();
                    int k = cs.size() - 1;
                    while (k >= 0 && inDrag(cs.get(k))) k--;
                    if (k >= 0) card(g2, cs.get(k), x, Y0);
                }
            }
            if (drag != null && hover != null) {                    // indicação de destino válido/inválido
                g2.setColor(ok ? Color.GREEN : Color.RED); g2.setStroke(new BasicStroke(4));
                int hy = hover.kind() == Kind.COLUMN ? cy(hover, Math.max(0, hover.size() - 1)) : Y0;
                g2.drawRoundRect(px(hover), hy, W, H, 10, 10);
            }
            if (drag != null)
                for (int i = 0; i < drag.size(); i++) card(g2, drag.get(i), mouse.x - gx, mouse.y - gy + i * UP);
        }
        boolean inDrag(Card c) { return drag != null && drag.contains(c); }

        void card(Graphics2D g2, Card c, int x, int y) {
            if (!c.isFaceUp()) {
                g2.setColor(new Color(30, 60, 160)); g2.fillRoundRect(x, y, W, H, 10, 10);
                g2.setColor(Color.WHITE); g2.drawRoundRect(x + 5, y + 5, W - 10, H - 10, 8, 8);
                g2.setColor(Color.BLACK); g2.drawRoundRect(x, y, W, H, 10, 10);
                return;
            }
            g2.setColor(Color.WHITE); g2.fillRoundRect(x, y, W, H, 10, 10);
            g2.setColor(Color.BLACK); g2.drawRoundRect(x, y, W, H, 10, 10);
            g2.setColor(c.suit.red ? new Color(200, 0, 0) : Color.BLACK);
            g2.setFont(new Font("Dialog", Font.BOLD, 16)); g2.drawString(c.rank.label + c.suit.sym, x + 6, y + 18);
            g2.setFont(new Font("Dialog", Font.PLAIN, 42)); g2.drawString(c.suit.sym, x + W / 2 - 16, y + H / 2 + 16);
        }
    }
}
