package hr.fer.zemris.ppj.lex;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;

import hr.fer.zemris.ppj.automaton.Automaton;
import hr.fer.zemris.ppj.automaton.AutomatonHandler;
import hr.fer.zemris.ppj.stream.Streamer;

/**
 * 
 * @author fhrenic
 */
public class Lex {

    private HashMap<String, List<LexRule>> states;
    private String currentState;
    private List<LexRule> currentRules;
    private OutputStream output;
    private String input;

    private int startIndex;
    private int endIndex;
    private int lastIndex;
    private int lineNumber;

    public Lex(String startState, HashMap<String, List<LexRule>> states, AutomatonHandler handler,
            OutputStream output) {
        this.currentState = startState;
        this.states = states;
        this.output = output;
        Automaton.setHandler(handler); // don't change this
        startIndex = 0; // pocetak
        endIndex = 0; // zavrsetak
        lastIndex = -1; // posljednji
        lineNumber = 1;
    }

    public void analyzeInput(InputStream stream) throws IOException {
        // we should save the table that we need to print out
        // maybe it will be needed in later exercises so we won't 
        // have to parse it

        input = Streamer.readFromStream(stream);
        int len = input.length();
        currentRules = states.get(currentState);
        LexRule lastRule = null;

        do {
            char symbol = input.charAt(endIndex);
            boolean allDead = true;
            for (LexRule rule : currentRules) {
                Automaton cur = rule.getAutomaton();
                if (cur.isDead()) {
                    continue;
                }
                allDead = false;
                cur.consume(symbol);
                if (cur.accepts()) {
                    lastRule = rule;
                    lastIndex = endIndex;
                    break; // if two automatons accept on same string, use first
                }
            }
            if (allDead) {
                if (lastRule == null) {
                    // neither automaton accepted string, start again
                    error();
                    endIndex = startIndex++;
                } else {
                    // it got accepted
                    lastRule.execute(this);
                    startIndex = lastIndex + 1;
                    endIndex = lastIndex;
                }
                lastRule = null;
                for (LexRule rule : currentRules) {
                    rule.getAutomaton().reset();
                }
            }
        } while (++endIndex < len);
    }

    public void incrementLineNumber() {
        lineNumber++;
    }

    public void printLexClass(String lexClass) {
        // TODO if we'll need the output, save this
        // otherwise, no need
        String sub = input.substring(startIndex, lastIndex + 1);
        String output = lexClass + " " + lineNumber + " " + sub + '\n';
        try {
            Streamer.writeToStream(output, this.output);
        } catch (IOException e) {
        }
    }

    public void goBack(int toIdx) {
        // OVDJE TREBA RESTARTATI AUTOMATE U TRENUTNOM STANJU
        // jer su consumeali znakove koji se sad vraćaju natrag u niz
        // zbog toga ih treba vratiti u to stanje
        // TODO
        int idx = startIndex + toIdx - 1;
        endIndex = idx;
        lastIndex = idx;
    }

    public void changeState(String state) {
        currentState = state;
        currentRules = states.get(currentState);
    }

    public void skip() {
        startIndex = endIndex + 1;
    }

    private void error() {
        int leftBound = Math.max(0, startIndex - 4);
        int rightBound = Math.min(startIndex + 4, input.length());
        System.err.println("Error at " + input.substring(leftBound, rightBound));
    }

}
