package hr.fer.zemris.ppj.sintax;

import java.io.IOException;
import java.util.Map;
import java.util.Stack;
import java.util.Vector;

import hr.fer.zemris.ppj.sintax.actions.LRAction;
import hr.fer.zemris.ppj.sintax.grammar.Production;
import hr.fer.zemris.ppj.sintax.grammar.Symbol;

/**
 * @author fhrenic
 * @author marko1597
 */
public class LRParser {
    Stack<Symbol> stackSymbol;
    Stack<Integer> stackState;
    Vector<Symbol> input;
    Symbol currentSym;
    int inputindex;
    int StartState;
    boolean running;
    Symbol StartStackSymbol;
    // stanje -> ( znak -> akcija )
    // Akcija i NovoStanje su objedinjeni u ovoj
    // zato jer nemaju presjeka, Akcija je definirana za stanje i završni,
    // a NovoStanje je definirana za stanje i nezavršni znak
    private Map<Integer, Map<Symbol, LRAction>> actions;
    LRNode tree;

    public LRParser(int startState, Symbol startStackSymbol,
            Map<Integer, Map<Symbol, LRAction>> table) {
        this.StartStackSymbol = startStackSymbol;
        this.StartState = startState;
        this.actions = table;
        this.inputindex = 0;
    }

    public LRNode analyzeInput(Vector<Symbol> input) throws IOException {
        this.input = input;
        LRAction action;
        running = true;
        stackState.push(StartState);
        stackSymbol.push(StartStackSymbol);
        while (running) {
            action = actions.get(stackState.peek()).get(currentSym);
            if (action != null)
                action.execute(this);
            else
                this.errorRecovery();
        }
        return tree;
    }

    public void acceptAction() {
        System.out.println(tree.toString());
        this.running = false;
    }

    public void moveAction(Integer newState) {
        currentSym = input.elementAt(this.inputindex++);
        stackState.push(newState);
        stackSymbol.push(currentSym);
    }

    public void putAction(Integer newState) {
        stackState.push(newState);
    }

    public void errorRecovery() {
        //todo
    }

    public void reduceAction(Production production) {
        //todo tree generation
        for (int i = 0; i < production.getRHS().size(); i++) {
            stackState.pop();
            stackSymbol.pop();
        }
        stackSymbol.push(production.getLHS());
        //put action
        this.currentSym = stackSymbol.peek();
    }
}
