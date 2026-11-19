package solitaire;
import java.util.*;

enum Suit {
    COPAS("♥", true), OUROS("♦", true), PAUS("♣", false), ESPADAS("♠", false);
    final String sym; final boolean red;
    Suit(String s, boolean r) { sym = s; red = r; }
    boolean sameColor(Suit o) { return red == o.red; }            // RF06
}

enum Rank {                                                        // RF05
    AS("A"), DOIS("2"), TRES("3"), QUATRO("4"), CINCO("5"), SEIS("6"), SETE("7"),
    OITO("8"), NOVE("9"), DEZ("10"), VALETE("J"), DAMA("Q"), REI("K");
    final String label;
    Rank(String l) { label = l; }
    boolean isJustAbove(Rank o) { return ordinal() == o.ordinal() + 1; }
    boolean isJustBelow(Rank o) { return o.isJustAbove(this); }
}

enum Kind { STOCK, WASTE, FOUNDATION, COLUMN }

/** Cada carta é uma única instância (RF04). A visibilidade vive na própria carta (RNF01). */
final class Card {
    final Suit suit; final Rank rank; private boolean faceUp;
    Card(Suit s, Rank r) { suit = s; rank = r; }
    String id() { return rank.name() + "-" + suit.name(); }      // RNF05
    boolean isFaceUp() { return faceUp; }
    void setFaceUp(boolean v) { faceUp = v; }
    @Override public String toString() { return rank.label + suit.sym; }
}

/** Visão somente leitura de um local (RF09, RF10, RF27). */
interface PileView {
    Kind kind(); int index(); boolean isEmpty(); int size(); Card top(); List<Card> cards();
}

final class Pile implements PileView {                             // RF08
    private final Kind kind; private final int index; private final ArrayList<Card> list = new ArrayList<>();
    Pile(Kind k, int i) { kind = k; index = i; }
    public Kind kind() { return kind; }
    public int index() { return index; }
    public boolean isEmpty() { return list.isEmpty(); }
    public int size() { return list.size(); }
    public Card top() { return list.isEmpty() ? null : list.get(list.size() - 1); }
    public List<Card> cards() { return List.copyOf(list); }
    int indexOf(Card c) { return list.indexOf(c); }
    void add(Card c) { list.add(c); }
    void addAll(List<Card> cs) { list.addAll(cs); }
    Card removeTop() { return list.remove(list.size() - 1); }
    List<Card> removeLast(int n) {
        List<Card> sub = list.subList(list.size() - n, list.size());
        List<Card> r = new ArrayList<>(sub); sub.clear(); return r;
    }
    void clear() { list.clear(); }
}
