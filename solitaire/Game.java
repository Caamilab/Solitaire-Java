package solitaire;
import java.util.*;

final class Game {
    private final List<Card> deck = new ArrayList<>();
    private final Map<String, Card> byId = new HashMap<>();
    private final Pile stock = new Pile(Kind.STOCK, 0), waste = new Pile(Kind.WASTE, 0);
    private final Pile[] found = new Pile[4], cols = new Pile[7];
    private final List<Pile> all = new ArrayList<>();
    private final Deque<Action> history = new ArrayDeque<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private final Random rnd;
    private Strategy strategy = new AutoStrategy();

    Game() { this(new Random()); }
    Game(Random r) {
        rnd = r;
        for (Suit s : Suit.values()) for (Rank k : Rank.values()) { Card c = new Card(s, k); deck.add(c); byId.put(c.id(), c); } // RF04
        all.add(stock); all.add(waste);
        for (int i = 0; i < 4; i++) all.add(found[i] = new Pile(Kind.FOUNDATION, i));
        for (int i = 0; i < 7; i++) all.add(cols[i] = new Pile(Kind.COLUMN, i));
        newGame();
    }

    // ---------- partida ----------
    void newGame() {                                                // RF01, RF07, RF16
        for (Pile p : all) p.clear();
        history.clear();
        Collections.shuffle(deck, rnd);
        int k = 0;
        for (Card c : deck) c.setFaceUp(false);
        for (int i = 0; i < 7; i++) for (int j = 0; j <= i; j++) { Card c = deck.get(k++); cols[i].add(c); c.setFaceUp(j == i); }
        while (k < deck.size()) stock.add(deck.get(k++));
        fire();
    }
    int score() { int n = 0; for (Pile f : found) n += f.size(); return n; }   // RF03
    boolean isWon() { return score() == 52; }                                   // RF02

    // ---------- consultas somente leitura (RF20, RF27) ----------
    PileView stock() { return stock; }
    PileView waste() { return waste; }
    PileView foundation(int i) { return found[i]; }
    PileView column(int i) { return cols[i]; }
    List<Card> columnCards(int i) { return cols[i].cards(); }
    Card cardById(String id) { return byId.get(id); }
    boolean canUndo() { return !history.isEmpty(); }

    PileView locationOf(Card c) {                                   // RF10: local de carta acessível
        for (Pile p : all) {
            int i = p.indexOf(c);
            if (i < 0) continue;
            switch (p.kind()) {
                case COLUMN: return c.isFaceUp() ? p : null;
                case STOCK: return null;
                default: return i == p.size() - 1 ? p : null;
            }
        }
        return null;
    }
    List<Card> sequenceFrom(Card c) {                               // RF20
        PileView p = locationOf(c);
        if (p == null || p.kind() != Kind.COLUMN) return List.of();
        List<Card> cs = p.cards();
        return new ArrayList<>(cs.subList(cs.indexOf(c), cs.size()));
    }
    boolean isFirstVisible(Card c) {
        PileView p = locationOf(c);
        if (p == null || p.kind() != Kind.COLUMN) return false;
        int i = p.cards().indexOf(c);
        return i == 0 || !p.cards().get(i - 1).isFaceUp();
    }
    boolean kingAtBase(Card c) {
        PileView p = locationOf(c);
        return c.rank == Rank.REI && p != null && p.kind() == Kind.COLUMN && p.cards().indexOf(c) == 0;
    }

    /** Única fonte das regras de legalidade (RF13, RF15, RF17, RF21). */
    boolean canMove(Card c, PileView d) {
        PileView src = locationOf(c);
        if (src == null || src == d) return false;
        if (d.kind() == Kind.WASTE || d.kind() == Kind.STOCK) return false;
        if (src.kind() == Kind.FOUNDATION && d.kind() == Kind.FOUNDATION) return false;
        List<Card> seq = src.kind() == Kind.COLUMN ? sequenceFrom(c) : List.of(c);
        if (d.kind() == Kind.FOUNDATION) {
            if (seq.size() != 1) return false;
            if (d.isEmpty()) return c.rank == Rank.AS;
            Card t = d.top();
            return t.suit == c.suit && c.rank.isJustAbove(t.rank);
        }
        if (d.isEmpty()) return c.rank == Rank.REI;
        Card t = d.top();
        return c.rank.isJustBelow(t.rank) && !c.suit.sameColor(t.suit);
    }

    // ---------- execução, histórico e notificação ----------
    private Pile pileOf(PileView v) {
        switch (v.kind()) {
            case STOCK: return stock; case WASTE: return waste;
            case FOUNDATION: return found[v.index()]; default: return cols[v.index()];
        }
    }
    Action drawAction() { return stock.isEmpty() ? new NullAction() : new DrawAction(stock, waste); }
    Action moveAction(Card c, PileView d) {
        if (!canMove(c, d)) return new NullAction();
        Pile src = pileOf(locationOf(c)), dst = pileOf(d);
        int n = src.kind() == Kind.COLUMN ? sequenceFrom(c).size() : 1;
        List<Action> l = new ArrayList<>();
        l.add(new MoveAction(src, dst, n));
        if (src.kind() == Kind.COLUMN && src.size() > n && !src.cards().get(src.size() - n - 1).isFaceUp())
            l.add(new RevealAction(src));                           // RF19
        return new CompositeAction(l);
    }
    Action actionFor(Suggestion s) {
        switch (s.kind()) {
            case DRAW: return drawAction();
            case MOVE: return moveAction(s.card(), s.dest());
            default: return new NullAction();
        }
    }
    boolean perform(Action a) {                                     // caminho único (RF25, RF26, RNF02)
        a.execute();
        if (!a.isEffective()) return false;
        history.push(a); fire(); return true;
    }
    boolean draw() { return perform(drawAction()); }
    boolean move(Card c, PileView d) { return perform(moveAction(c, d)); }
    boolean autoStep() { return perform(actionFor(strategy.suggest(this))); }   // RF28, RF31
    void setStrategy(Strategy s) { strategy = s; }
    boolean undo() {                                                // RF25
        if (history.isEmpty()) return false;
        history.pop().undo(); fire(); return true;
    }
    void undoAll() { while (undo()) { } }

    void addListener(Runnable r) { listeners.add(r); }
    private void fire() { for (Runnable r : listeners) r.run(); }   // RF26

    String snapshot() {
        StringBuilder sb = new StringBuilder();
        for (Pile p : all) { sb.append(p.kind()).append(p.index()).append(':'); for (Card c : p.cards()) sb.append(c).append(c.isFaceUp() ? '+' : '-').append(' '); sb.append('\n'); }
        return sb.toString();
    }
}
