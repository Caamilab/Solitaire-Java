package solitaire;
import java.util.*;

/** Toda ação é reversível (RF22). */
interface Action {
    void execute(); void undo();
    default boolean isEffective() { return true; }
}

final class NullAction implements Action {                        // RF24
    public void execute() {} public void undo() {}
    public boolean isEffective() { return false; }
}

final class DrawAction implements Action {                         // RF11, RF12
    private final Pile stock, waste; private Card card;
    DrawAction(Pile s, Pile w) { stock = s; waste = w; }
    public void execute() { card = stock.removeTop(); card.setFaceUp(true); waste.add(card); }
    public void undo() { waste.removeTop(); card.setFaceUp(false); stock.add(card); }
}

final class MoveAction implements Action {                         // RF18
    private final Pile from, to; private final int n;
    MoveAction(Pile f, Pile t, int n) { from = f; to = t; this.n = n; }
    public void execute() { to.addAll(from.removeLast(n)); }
    public void undo() { from.addAll(to.removeLast(n)); }
}

final class RevealAction implements Action {                       // RF19
    private final Pile pile; private Card card;
    RevealAction(Pile p) { pile = p; }
    public void execute() { card = pile.top(); card.setFaceUp(true); }
    public void undo() { card.setFaceUp(false); }
}

final class CompositeAction implements Action {                    // RF23
    private final List<Action> parts;
    CompositeAction(List<Action> p) { parts = List.copyOf(p); }
    public void execute() { for (Action a : parts) a.execute(); }
    public void undo() { for (int i = parts.size() - 1; i >= 0; i--) parts.get(i).undo(); }
}
