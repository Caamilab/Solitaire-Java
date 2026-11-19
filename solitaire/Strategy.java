package solitaire;
import java.util.*;

enum SKind { NONE, DRAW, MOVE }
record Suggestion(SKind kind, Card card, PileView dest) {
    static final Suggestion NONE = new Suggestion(SKind.NONE, null, null);
    static Suggestion draw() { return new Suggestion(SKind.DRAW, null, null); }
    static Suggestion move(Card c, PileView d) { return new Suggestion(SKind.MOVE, c, d); }
}

/** Decide sem alterar o estado: só consultas (RF31, RNF04). */
interface Strategy { Suggestion suggest(Game g); }

final class NoStrategy implements Strategy {                       // RF28: desativada
    public Suggestion suggest(Game g) { return Suggestion.NONE; }
}

final class AutoStrategy implements Strategy {                     // RF29, RF30
    public Suggestion suggest(Game g) {
        PileView w = g.waste(), s = g.stock();
        if (w.isEmpty() && !s.isEmpty()) return Suggestion.draw();                      // 1
        if (!w.isEmpty()) {
            Card c = w.top();
            for (int i = 0; i < 4; i++) if (g.canMove(c, g.foundation(i))) return Suggestion.move(c, g.foundation(i)); // 2
            for (int i = 0; i < 7; i++) if (g.canMove(c, g.column(i))) return Suggestion.move(c, g.column(i));         // 3
        }
        for (int i = 0; i < 7; i++) {                                                   // 4
            Card c = g.column(i).top();
            if (c == null) continue;
            for (int f = 0; f < 4; f++) if (g.canMove(c, g.foundation(f))) return Suggestion.move(c, g.foundation(f));
        }
        for (int i = 0; i < 7; i++)                                                     // 5
            for (Card c : g.column(i).cards()) {
                if (!c.isFaceUp() || !g.isFirstVisible(c) || g.kingAtBase(c)) continue; // só com ganho concreto
                for (int j = 0; j < 7; j++) if (j != i && g.canMove(c, g.column(j))) return Suggestion.move(c, g.column(j));
            }
        if (!s.isEmpty()) return Suggestion.draw();                                     // 6
        return Suggestion.NONE;                                                         // 7
    }
}
