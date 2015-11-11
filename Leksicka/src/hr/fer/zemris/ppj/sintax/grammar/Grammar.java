package hr.fer.zemris.ppj.sintax.grammar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;

import hr.fer.zemris.ppj.automaton.DFA;
import hr.fer.zemris.ppj.automaton.DFAExtended;
import hr.fer.zemris.ppj.automaton.EpsilonNFA;
import hr.fer.zemris.ppj.automaton.NFA;
import hr.fer.zemris.ppj.sintax.LREntry;
import hr.fer.zemris.ppj.sintax.actions.LRAction;
import hr.fer.zemris.ppj.stream.SintaxInputParser;

/**
 * @author fhrenic
 */
public class Grammar {

    private List<Symbol> terminalSymbols;
    private Map<Symbol, List<Production>> productions;
    private Production startingProduction; // S' -> S
    private Map<Symbol, Set<Symbol>> startSets;

    private Map<Integer, Map<Symbol, LRAction>> actions;

    public Grammar(List<Symbol> terminalSymbols, Map<Symbol, List<Production>> productions,
            Production startingProduction) {
        this.terminalSymbols = terminalSymbols;
        this.productions = productions;
        this.startingProduction = startingProduction;

        // finds empty symbols
        annotateEmptySymbols();

        constructStartSets();

        // find empty productions
        annotateProductions();

        // zadnja metoda u konstruktoru
        buildActionTable(generateDFA());
    }

    private void annotateProductions() {
        for (List<Production> ps : productions.values()) {
            for (Production p : ps) {
                p.annotate();
            }
        }
        startingProduction.annotate();
    }

    private void annotateEmptySymbols() {
        boolean foundNewEmpty = false;
        for (Symbol term : productions.keySet()) {
            if (term.isEmpty()) {
                continue;
            }
            for (Production prod : productions.get(term)) {
                if (prod.isEmpty()) {
                    term.setEmpty(true);
                    foundNewEmpty = true;
                    break;
                }
            }
        }
        if (foundNewEmpty) {
            annotateEmptySymbols();
        }
    }

    private Set<Symbol> startsWith(Symbol sym) {
        return startSets.get(sym);
    }

    private Set<Symbol> startsWith(Iterable<Symbol> it) {
        Set<Symbol> set = new TreeSet<>();
        for (Symbol s : it) {
            set.addAll(startsWith(s));
            if (!s.isEmpty()) {
                break;
            }
        }
        return set;
    }

    public List<Production> getProductionsForLhs(Symbol sym) {
        return productions.get(sym);
    }

    private void buildActionTable(DFAExtended<LREntry, Symbol> dfa) {

        //System.out.println(dfa);
        System.exit(0);

        Map<Integer, Set<LREntry>> aliases = dfa.getAliases();
        DFA<Integer, Symbol> dfan = dfa.getDfa();

        List<Integer> states = new ArrayList<>(aliases.keySet());
        Collections.sort(states);

        Map<Integer, Map<Symbol, Integer>> transitions = dfan.getTransitions();

        for (Integer state : transitions.keySet()) {
            Map<Symbol, Integer> trans = transitions.get(state);
            for (Symbol sym : trans.keySet()) {

            }

        }

        for (Integer state : states) {
            LREntry lre = null;
            for (LREntry e : aliases.get(state)) {
                if (lre != null) {
                    if (e.compareTo(lre) < 0) {
                        lre = e;
                    }
                } else {
                    lre = e;
                }
            }

            LRAction action = null;
            if (!lre.isComplete()) {

            }

        }

    }

    private DFAExtended<LREntry, Symbol> generateDFA() {

        /* * * * * * * * * *
         * ENFA parameters *
         * * * * * * * * * */

        // S' -> .S, { STREAM_END } is the start state
        // S' -> S., { STREAM_END } is the final state
        // starts(S') = { STREAM_END }
        Set<Symbol> startingStateStartSet = Collections.singleton(Symbol.STREAM_END);
        LREntry startState = new LREntry(startingProduction, startingStateStartSet);
        LREntry finalState = startState.next();

        // empty maps for transitions
        Map<LREntry, Map<Symbol, Set<LREntry>>> transitions = new HashMap<>();
        Map<LREntry, Set<LREntry>> epsilonTransitions = new HashMap<>();

        // create empty ENFA with no transitions
        EpsilonNFA<LREntry, Symbol> enfa = new EpsilonNFA<LREntry, Symbol>(startState, finalState,
                transitions, epsilonTransitions);

        Queue<LREntry> queue = new LinkedList<>();
        queue.add(startState);

        /* * * * * * * * * * * * * *
         * Add transitions to ENFA *
         * * * * * * * * * * * * * */

        while (!queue.isEmpty()) {
            LREntry entry = queue.poll();

            while (!entry.isComplete()) {

                // case 4b
                Symbol sym = entry.getTransitionSymbol();
                LREntry next = entry.next();
                enfa.addTransition(entry, sym, next);

                // case 4c
                if (sym.getType() == SymbolType.NON_TERMINAL) {

                    // entry = A -> x.By, {a1,...,an}
                    // next  = A -> xB.y, {a1,...,an}

                    // case 4 - c - i
                    Set<Symbol> newStartSet = startsWith(next.getSymbolsAfterDot());
                    if (next.isEmptyAfterDot()) {
                        // case 4 - c - ii
                        newStartSet.addAll(entry.getStartSet());
                    }

                    for (Production ntp : getProductionsForLhs(sym)) {

                        if (ntp.isEpsilonProduction()) {
                            //                            enfa.addEpsilonTransition(entry, next);
                            //                            continue;
                        }

                        LREntry nonTermEntry = new LREntry(ntp, newStartSet);
                        enfa.addEpsilonTransition(entry, nonTermEntry);

                        // if not already processed
                        if (!epsilonTransitions.containsKey(nonTermEntry)
                                && !transitions.containsKey(nonTermEntry)) {
                            queue.add(nonTermEntry);
                        }
                    }
                }
                entry = next;
            }
        }

        NFA<LREntry, Symbol> nfa = enfa.toNFA();

        DFAExtended<LREntry, Symbol> dfa = nfa.toDFA();
        System.out.println("------------------");
        System.out.println(dfa);

        return dfa;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Symbol key : productions.keySet()) {
            sb.append(key);
            sb.append('\n');
            for (Production p : productions.get(key)) {
                sb.append(' ');
                sb.append(p.toString());
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * @author dnakic
     */
    private void constructStartSets() {

        startSets = new HashMap<Symbol, Set<Symbol>>();

        // startsDirectly
        for (Symbol symbol : terminalSymbols) {
            Set<Symbol> firstSet = new HashSet<Symbol>();
            firstSet.add(symbol);
            startSets.put(symbol, firstSet);
        }

        Set<Symbol> nonTerminalSymbols = productions.keySet();
        for (Symbol symbol : nonTerminalSymbols) {
            Set<Symbol> firstSet = new HashSet<Symbol>();

            for (Production production : productions.get(symbol)) {
                for (Symbol rhsSym : production.getRHS()) {
                    firstSet.add(rhsSym);
                    if (!rhsSym.isEmpty()) {
                        break;
                    }
                }
            }

            startSets.put(symbol, firstSet);
        }

        // equivalence relation
        boolean change;
        do {
            change = false;
            for (Symbol symbol : nonTerminalSymbols) {
                Set<Symbol> firstSet = startSets.get(symbol);
                Set<Symbol> tmp = new HashSet<Symbol>(firstSet);
                for (Symbol first : firstSet) {
                    if (first.getType() == SymbolType.NON_TERMINAL) {
                        if (tmp.addAll(startSets.get(first))) {
                            change = true;
                        }
                    }
                }
                startSets.put(symbol, tmp);
            }
        } while (change);

        // remove all non terminal symbols from every set
        // also remove epsilon
        for (Set<Symbol> set : startSets.values()) {
            set.retainAll(terminalSymbols);
            set.remove(SintaxInputParser.EPS_SYMBOL);
        }

    }

}
