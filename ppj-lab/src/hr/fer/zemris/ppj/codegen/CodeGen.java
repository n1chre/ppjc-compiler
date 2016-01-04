package hr.fer.zemris.ppj.codegen;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class CodeGen {
    public static final int NUM_REGS = 8; // number of registers, [0,NUM_REG>
    private static final int SP = Integer.parseInt("40000", 16); // default stack size

    private static final int FRAME_PTR = 5; // frame pointer for indexing
    private static final int RET_REG = 6; // register for return value
    private static final int SP_REG = 7; // stack pointer register

    private static final Map<String, String> CMP_NAMES;

    static {
        CMP_NAMES = new HashMap<>();
        CMP_NAMES.put(">", "SGT");
        CMP_NAMES.put("<", "SLT");
        CMP_NAMES.put(">=", "SGE");
        CMP_NAMES.put("<=", "SLE");
        CMP_NAMES.put("==", "EQ");
        CMP_NAMES.put("!=", "NE");
    }

    private CodeBlock startBlock;
    private List<CodeBlock> blocks;
    private CodeBlock data;
    private CodeBlock curr;

    private CodeBlock buffer;

    private Counter dataLabeler;
    private Counter temporaryLabeler;

    public CodeGen() {
        this(SP);
    }

    public CodeGen(int stackSize) {
        startBlock = new CodeBlock();
        blocks = new LinkedList<>();
        data = new CodeBlock();

        curr = startBlock;

        // labels
        dataLabeler = new Counter("G");
        temporaryLabeler = new Counter("T");

        // initialize stack pointer
        startBlock.add(new Code(new Command("MOVE", Param.num(stackSize), Param.reg(SP_REG)),
                "initialize stack pointer"));
    }

    public void blabla() {
        startBlock.add(new Code(new Command("CALL", Param.label(functionLabel("main")))));
        startBlock.add(new Code(new Command("HALT")));
    }

    public void flushBuffer() {
        curr.consume(buffer);
    }

    // allocation, reference and assignment 

    public void allocGlobal(String varName, boolean oneByte) {
        String label = dataLabel(varName);
        int size = oneByte ? 1 : 4;
        data.add(new Code(label, new Command("DS", Param.num(size)), "var " + varName));
    }

    public void allocLocal(String varName) {
        curr.add(new Code(new Command("SUB", Param.reg(SP_REG), Param.num(4), Param.reg(SP_REG)),
                "var " + varName));
    }

    public void arrayAlloc(String arrName, int size, boolean oneByte) {
        if (!oneByte) size *= 4;
        String varLabel = dataLabel(arrName);
        data.add(new Code(varLabel, new Command("DS", Param.num(size))));
    }

    /**
     * Reference a global variable.
     * 
     * @param vName variable name
     * @param oneByte is the variable one byte
     * @param byValue is the value at that label a value (or an address)
     * @param bufferIt if <code>true</code> don't add code but buffer it
     */
    public void globalRef(String vName, boolean oneByte, boolean byValue, boolean bufferIt) {
        CodeBlock tmpCurr = curr;
        if (bufferIt) curr = buffer = new CodeBlock();
        String label = startBlock.getLabel(vName);
        if (byValue) {
            addMemoryCode(true, oneByte, Param.reg(1), Param.aLabel(label));
        } else {
            curr.add(new Code(new Command("MOVE", Param.label(label), Param.reg(1))));
        }
        stackOp(true, 1);
        curr = tmpCurr;
    }

    /**
     * References a local variable / parameter to function.
     * 
     * @param offset + for local variable, - for parameter
     * @param oneByte
     * @param byValue
     * @param bufferIt if <code>true</code> don't add code but buffer it
     */
    public void localRef(int offset, boolean oneByte, boolean byValue, boolean bufferIt) {
        CodeBlock tmpCurr = curr;
        if (bufferIt) curr = buffer = new CodeBlock();
        if (byValue) {
            addMemoryCode(true, oneByte, Param.reg(1), Param.aRegwOff(FRAME_PTR, offset));
        } else {
            curr.add(new Code(
                    new Command("ADD", Param.reg(FRAME_PTR), Param.num(offset), Param.reg(1))));
        }
        stackOp(true, 1);
        curr = tmpCurr;
    }

    public void assign(boolean oneByte) {
        stackOp(false, 1, "value"); // value
        stackOp(false, 2, "var address"); // variable address
        addMemoryCode(false, oneByte, Param.reg(1), Param.aReg(2)); // assign
    }

    /**
     * Generate code on function start
     * 
     * @param functionName
     */
    public void functionStart(String functionName) {
        String startLabel = functionLabel(functionName);
        curr = new CodeBlock(functionName);
        blocks.add(curr);

        // init frame pointer
        curr.add(
                new Code(startLabel, new Command("MOVE", Param.reg(SP_REG), Param.reg(FRAME_PTR))));
    }

    /**
     * Jump to function end
     */
    public void functionReturn() {
        String label = functionEndLabel(curr.getName());
        curr.add(new Code(new Command("JR", Param.label(label))));
    }

    /**
     * Generate code for function end.
     * 
     * @param functionName
     * @param numberOfLocalVariables
     */
    public void functionEnd(String functionName, int numberOfLocalVariables, boolean returns) {
        String label = functionEndLabel(curr.getName());
        curr.labelNext(label);
        if (returns) stackOp(false, RET_REG, "return value");
        if (numberOfLocalVariables > 0) removeFromStack(numberOfLocalVariables);
        curr.add(new Code(new Command("RET")));
    }

    /**
     * Load value n into register.
     * 
     * @param n value
     */
    public void numberCode(int n) {
        // pass by value
        if (in20bit(n)) {
            curr.add(new Code(new Command("MOVE", Param.num(n), Param.reg(1))));
        } else {
            String label = tmpLabel();
            data.add(new Code(label, new Command("DW", Param.num(n))));
            curr.add(new Code(new Command("LOAD", Param.reg(1), Param.aLabel(label))));
        }
        stackOp(true, 1);
    }

    /**
     * Load value c into register.
     * 
     * @param c value
     */
    public void characterCode(char c) {
        // pass by value
        curr.add(new Code(new Command("MOVE", Param.num(c), Param.reg(1))));
        stackOp(true, 1);
    }

    /**
     * Store string into memory (along with implicit '\0') and load it's address
     * into register.
     * 
     * @param s string
     */
    public void stringCode(String s) {
        if (s.isEmpty()) return;
        String label = tmpLabel();
        data.add(new Code(label, new Command("DB", Param.num(s.charAt(0)))));
        for (int i = 1; i < s.length(); i++) {
            data.add(new Code(new Command("DB", Param.num(s.charAt(i)))));
        }
        data.add(new Code(new Command("DB", Param.num('\0'))));
        // pass by reference
        curr.add(new Code(new Command("MOVE", Param.label(label), Param.reg(1))));
        stackOp(true, 1);
    }

    /**
     * Generates code to swap address for the value at that address.
     * 
     * @param oneByte is the value one byte
     */
    public void refToVal(boolean oneByte) {
        // current reference to value
        stackOp(false, 1);
        addMemoryCode(true, oneByte, Param.reg(1), Param.aReg(1));
        stackOp(true, 1);
    }

    public void prepareForFunctionCall(String functionName) {
        String fLabel = functionLabel(functionName);
        curr.add(new Code(new Command("MOVE", Param.label(fLabel), Param.reg(1))));
    }

    /**
     * Save context on stack and call function.
     * 
     * @param returns <code>true</code> if function returns
     */
    public void call(boolean returns) {
        // stackOp(false, FUNC_REG); // pop function label
        stackOp(true, FRAME_PTR); // save this function frame
        curr.add(new Code(new Command("CALL", Param.aReg(1))));
        stackOp(false, FRAME_PTR); // restore frame
        if (returns) {
            stackOp(true, RET_REG); // push return value onto stack
        }
    }

    /**
     * Removes n entries from stack simply by manipulating stack pointer
     * 
     * @param n how manny entries
     */
    public void removeFromStack(int n) {
        int size = n * 4;
        if (n <= 0) return;
        curr.add(new Code(new Command("ADD", Param.reg(SP_REG), Param.num(size), Param.reg(SP_REG)),
                "remove local vars"));
    }

    /**
     * Calculate binary operation between two elements on stack and push the result back on.
     * 
     * @param operator operator that needs to be performed
     * @param leftByValue left operand is represented by value (could be by reference)
     * @param rightByValue the same as for the left operand
     */
    public void binaryOp(String operator) { //, boolean leftByValue, boolean rightByValue) {
        stackOp(false, 2); // right operand
        // if (!rightByValue) addMemoryCode(true, false, Param.reg(2), Param.aReg(2));

        stackOp(false, 1); // left operand
        //  if (!leftByValue) addMemoryCode(true, false, Param.reg(1), Param.aReg(1));

        String cmpName = CMP_NAMES.get(operator);
        if (cmpName != null) {
            cmp(cmpName);
        } else {
            char op = operator.charAt(0);
            if (op == '+') binOp("ADD");
            else if (op == '-') binOp("SUB");
            else if (op == '&') binOp("AND");
            else if (op == '|') binOp("OR");
            else if (op == '^') binOp("XOR");
            else if (op == '*') multiplication();
            else if (op == '/') division();
            else if (op == '%') modulo();
            else throw new IllegalArgumentException("Unknown operator " + operator);
        }
    }

    /**
     * Perform simple built in binary operation on registers 1 and 2 and push the result.
     * 
     * @param name operation name
     */
    private void binOp(String name) {
        curr.add(new Code(new Command(name, Param.reg(1), Param.reg(2), Param.reg(1))));
        stackOp(true, 1); // push result
    }

    /**
     * Compare values in register 1 and 2
     * name explanation                 sign
     * ULE  Unsigned Less or Equal      <=
     * UGT  Unsigned Greater Than       >
     * ULT  Unsigned Less Than          <
     * UGE  Unsigned Greater or Equal   >=
     * EQ   Equal                       ==
     * NE   Not Equal                   !=
     * 
     * @param name comparator name
     */
    private void cmp(String name) {
        // SR = ...|zero|overflow|carry|negative|
        String label = tmpLabel();

        curr.add(new Code(new Command("CMP", Param.reg(1), Param.reg(2)))); // update flags in SR
        curr.add(new Code(new Command("MOVE", Param.num(1), Param.reg(1)))); // assume OK
        curr.add(new Code(new Command("JR_" + name, Param.label(label)))); // real comparing
        curr.add(new Code(new Command("MOVE", Param.num(0), Param.reg(1)))); // change because not OK
        curr.add(new Code(label, new Command("PUSH", Param.reg(1)))); // push register
    }

    /**
     * Multiply values in R1 and R2 and push the result onto stack.
     */
    private void multiplication() {
        // TODO R2 is negative?
        String loop = tmpLabel();
        String end = tmpLabel();

        curr.add(new Code(new Command("MOVE", Param.num(0), Param.reg(0)))); // R0 = 0;

        curr.add(new Code(loop, new Command("SUB", Param.reg(2), Param.num(1), Param.reg(2)))); // R2--;
        curr.add(new Code(new Command("JR_EQ", Param.label(end)))); // R2 == 0

        curr.add(new Code(new Command("ADD", Param.reg(0), Param.reg(1), Param.reg(0)))); // R0 += R1;
        curr.add(new Code(new Command("JR", Param.label(loop)))); // again

        curr.add(new Code(end, new Command("PUSH", Param.reg(0)))); // push result stored in R0
    }

    private void division() {
        // TODO
        // R1 <- R1 / R2
    }

    private void modulo() {
        // TODO
        // R1 <- R1 % R2
    }

    /**
     * Generates array access code.
     * 
     * @param oneByte memory size of one element in array
     */
    public void arrayAccess(boolean oneByte, boolean byValue) {
        // a[idx]
        stackOp(false, 1); // idx
        stackOp(false, 2); // a

        if (!oneByte) {
            // multiply by 4 to get offset
            curr.add(new Code(new Command("SHL", Param.reg(1), Param.num(2), Param.reg(1))));
        }

        // r1 = &element
        curr.add(new Code(new Command("ADD", Param.reg(1), Param.reg(2), Param.reg(1))));

        // r1 = element
        if (byValue) addMemoryCode(true, oneByte, Param.reg(1), Param.aReg(1));

        stackOp(true, 1); // push result
    }

    /**
     * Change value of variable by 1, either increment or decrement.
     *  
     * @param pre if <code>true</code>, first change then return, otherwise
     *            save value, change, then return saved value
     * @param increment if <code>true</code> increment, otherwise decrement
     * @param oneByte
     */
    public void byOne(boolean pre, boolean increment, boolean oneByte) {
        String op = increment ? "ADD" : "SUB";
        Code opCode = new Code(new Command(op, Param.reg(1), Param.num(1), Param.reg(1)));

        stackOp(false, 2); // variable address
        addMemoryCode(true, oneByte, Param.reg(1), Param.aReg(2)); // load variable

        if (pre) curr.add(opCode);
        stackOp(true, 1); // push result
        if (!pre) curr.add(opCode);

        addMemoryCode(false, oneByte, Param.reg(1), Param.aReg(2)); // store variable
    }

    public void negative() {
        stackOp(false, 1);
        curr.add(new Code(new Command("SUB", Param.reg(2), Param.reg(2), Param.reg(2)))); // R2 <- 0
        curr.add(new Code(new Command("SUB", Param.reg(2), Param.reg(1), Param.reg(1)))); // R1 <- 0-R1
        stackOp(true, 1);
    }

    public void bitNot() {
        stackOp(false, 1);
        curr.add(new Code(new Command("XOR", Param.reg(1), Param.num(-1), Param.reg(1)))); // R1 <- ~R1
        stackOp(true, 1);
    }

    public void logicNot() {
        String label = tmpLabel();
        stackOp(false, 1);
        curr.add(new Code(new Command("CMP", Param.reg(1), Param.num(0)))); // update flags in SR
        curr.add(new Code(new Command("MOVE", Param.num(1), Param.reg(1)))); // assume r1==0
        curr.add(new Code(new Command("JR_EQ", Param.label(label)))); // real comparing
        curr.add(new Code(new Command("MOVE", Param.num(0), Param.reg(1)))); // r1 != 0
        curr.add(new Code(label, new Command("PUSH", Param.reg(1)))); // push register
        stackOp(true, 1);
    }

    public void arrayInitialization(int size, boolean oneByte) {
        int next = oneByte ? 1 : 4;
        int offset = next * size;
        String label = curr.lastLabel();

        curr.add(new Code(new Command("MOVE", Param.label(label), Param.reg(2))));
        curr.add(new Code(new Command("ADD", Param.reg(2), Param.num(offset), Param.reg(2))));
        for (int i = 0; i < size; i++) {
            curr.add(new Code(new Command("SUB", Param.reg(2), Param.num(next), Param.reg(2)),
                    "move pointer"));
            stackOp(false, 1, "adding element");
            addMemoryCode(false, oneByte, Param.reg(1), Param.aReg(2));
        }
        stackOp(true, 2, "add address to stack");
    }

    public String logicOperator(String operator) {
        String condition;
        int value;
        if (operator.equals("||")) {
            condition = "NE";
            value = 1;
        } else {
            condition = "EQ";
            value = 0;
        }

        String label = tmpLabel();
        stackOp(false, 1, "operator " + operator);
        curr.add(new Code(new Command("MOVE", Param.num(value), Param.reg(2))));
        stackOp(true, 2);
        curr.add(new Code(new Command("CMP", Param.reg(1), Param.num(0))));
        curr.add(new Code(new Command("JR_" + condition, Param.label(label))));
        stackOp(false, 2);
        return label;
    }

    public String evalIf() {
        String label = tmpLabel();
        stackOp(false, 1, "evaluated condition");
        curr.add(new Code(new Command("CMP", Param.reg(1), Param.num(0)), "decide if"));
        curr.add(new Code(new Command("JR_EQ", Param.label(label))));
        return label;
    }

    public String afterIf() {
        String label = tmpLabel();
        curr.add(new Code(new Command("JR", Param.label(label))));
        return label;
    }

    public void labelNext(String label) {
        curr.labelNext(label);
    }

    public String labelNextSelf() {
        String label = tmpLabel();
        curr.labelNext(label);
        return label;
    }

    public void jumpTo(String label) {
        curr.add(new Code(new Command("JR", Param.label(label))));
    }

    public void pushR1() {
        stackOp(true, 1);
    }

    /**
     * Stack operation without comment. See stackOp with three parameters for more info.
     * 
     * @param push
     * @param r
     */
    private void stackOp(boolean push, int r) {
        stackOp(push, r, null);
    }

    /**
     * Performs two stack operations, PUSH or POP.
     * 
     * @param push if <code>true</code> then push, else pop
     * @param r register to push from / pop to
     * @param comment comment
     */
    private void stackOp(boolean push, int r, String comment) {
        String name = push ? "PUSH" : "POP";
        curr.add(new Code(new Command(name, Param.reg(r)), comment));
    }

    /**
     * Generates load or store code.
     * 
     * @param load if <code>true</code> then load, else store
     * @param oneByte is the value one byte
     * @param params parameters to the command
     */
    private void addMemoryCode(boolean load, boolean oneByte, Param... params) {
        String name = (load ? "LOAD" : "STORE") + (oneByte ? "B" : "");
        curr.add(new Code(new Command(name, params)));
    }

    private String functionLabel(String functionName) {
        return "F_" + functionName.toUpperCase();
    }

    private String functionEndLabel(String functionName) {
        return "R_" + functionName.toUpperCase();
    }

    private String dataLabel(String varName) {
        String label = dataLabeler.next();
        curr.addLabel(varName, label);
        return label;
    }

    private String tmpLabel() {
        return temporaryLabeler.next();
    }

    private static boolean in20bit(int n) {
        int top = n >> 20;
        return top == 0 || top == (1 << 12);
    }

    @Override
    public String toString() {
        blabla(); // TODO

        char delim = '\n';
        StringBuilder sb = new StringBuilder();
        sb.append(startBlock);
        sb.append(delim);
        for (CodeBlock cb : blocks) {
            sb.append(cb);
            sb.append(delim);
        }
        sb.append(data);
        return sb.toString();
    }

    private class Counter {
        private String prefix;
        private int cnt;

        public Counter(String preffix) {
            this.prefix = preffix;
            cnt = 0;
        }

        public String next() {
            return prefix + '_' + cnt++;
        }
    }

}
