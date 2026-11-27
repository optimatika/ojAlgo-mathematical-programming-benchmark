/*
 * Copyright 1997-2026 Optimatika
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.ojalgo.benchmark.integer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Optimisation;
import org.ojalgo.optimisation.Variable;
import org.ojalgo.optimisation.integer.IntegerStrategy;
import org.ojalgo.structure.Access1D;
import org.ojalgo.type.context.NumberContext;

/**
 * ojAlgo as a FlatZinc solver – the program MiniZinc runs with the FlatZinc file it compiled a model to:
 *
 * <pre>
 * java -cp &lt;classpath&gt; org.ojalgo.benchmark.integer.FlatZinc [options] model.fzn
 * </pre>
 * <p>
 * The model should be compiled with MiniZinc's linear library ({@code "mznlib": "-Glinear"} in the solver
 * configuration file), see {@link ExpressionsBasedModel.FileFormat#FZN}. Solutions are printed as a FlatZinc
 * solver prints them: the output variables, then {@code ----------}, and {@code ==========} when the solution
 * is proven optimal.
 * {@code =====UNSATISFIABLE=====} when there is no solution, and {@code =====UNKNOWN=====} when none was
 * found in the time there was.
 * <p>
 * Options:
 * <dl>
 * <dt>-a, -i</dt>
 * <dd>All/intermediate solutions: each new best solution is printed as it is found. (For a satisfaction
 * problem only one solution is found.)</dd>
 * <dt>-f</dt>
 * <dd>Free search. Always – search annotations are ignored.</dd>
 * <dt>-p &lt;n&gt;</dt>
 * <dd>Number of threads (default 1).</dd>
 * <dt>-t &lt;ms&gt;</dt>
 * <dd>Time limit in milliseconds. A solution found by then is printed before then.</dd>
 * <dt>-s</dt>
 * <dd>Statistics, as {@code %%%mzn-stat:} lines.</dd>
 * <dt>-v</dt>
 * <dd>Verbose, to standard error.</dd>
 * <dt>-n &lt;n&gt;, -r &lt;seed&gt;</dt>
 * <dd>Accepted and ignored.</dd>
 * <dt>--all-values</dt>
 * <dd>Also print every FlatZinc variable's value, after the output variables – to check a solution against
 * the FlatZinc file.</dd>
 * <dt>--parse-only</dt>
 * <dd>Only parse the file, and print the size of the model.</dd>
 * </dl>
 *
 * @author apete
 */
public abstract class FlatZinc {

    /**
     * What a FlatZinc file is: the model (parsed by ojAlgo), and what the model does not have – how to write a
     * solution. For that the file is read once more, only for its declarations. The model's variables have
     * the FlatZinc names.
     */
    private static final class Parsed {

        private record Output(String name, Type type, List<String> dimensions, List<?> elements) {

        }

        private enum Type {

            BOOL, FLOAT, INT, SET;

            /**
             * The type of a domain, from its first token: bool, int, float, set of .., 1..9, {1,3,5} or 1.0..9.0
             */
            static Type of(final String token) {
                return switch (token) {
                    case "bool"      -> BOOL;
                    case "float"     -> FLOAT;
                    case "set"       -> SET;
                    case "int", "{"  -> INT;
                    default          -> token.contains(".") || token.contains("e") || token.contains("E") ? FLOAT : INT;
                };
            }

        }

        private static final BigDecimal HALF = new BigDecimal("0.5");

        static Parsed parse(final String file) throws IOException {

            byte[] bytes = Files.readAllBytes(Path.of(file));

            // Throws, with the line number, if the file is not (supported) FlatZinc – what is read below is valid
            ExpressionsBasedModel model = ExpressionsBasedModel.parse(new ByteArrayInputStream(bytes), ExpressionsBasedModel.FileFormat.FZN);

            Map<String, Variable> variables = new HashMap<>();
            for (Variable variable : model.getVariables()) {
                variables.put(variable.getName(), variable);
            }

            List<Output> output = new ArrayList<>();
            Map<String, Object> symbols = new LinkedHashMap<>();

            List<String> tokens = Parsed.tokenize(new String(bytes, StandardCharsets.UTF_8));

            for (int first = 0, last = 0; first < tokens.size(); first = last + 1) {

                last = first;
                while (last < tokens.size() && !";".equals(tokens.get(last))) {
                    last++;
                }
                List<String> item = tokens.subList(first, last);

                if (!item.isEmpty() && !"predicate".equals(item.get(0)) && !"constraint".equals(item.get(0)) && !"solve".equals(item.get(0))) {
                    Parsed.declare(item, variables, symbols, output);
                }
            }

            return new Parsed(model, output, symbols);
        }

        /**
         * [array [..] of] [var] domain : name [:: annotations] [= value]
         */
        private static void declare(final List<String> item, final Map<String, Variable> variables, final Map<String, Object> symbols,
                final List<Output> output) {

            boolean array = "array".equals(item.get(0));
            int domain = array ? item.indexOf("of") + 1 : 0;
            if ("var".equals(item.get(domain))) {
                domain++;
            }
            Type type = Type.of(item.get(domain));

            int colon = item.indexOf(":");
            String name = item.get(colon + 1);

            // Annotations have no '=' (a string is one token)
            int equals = item.indexOf("=");
            List<String> annotations = item.subList(colon + 2, equals < 0 ? item.size() : equals);
            List<String> value = equals < 0 ? List.of() : item.subList(equals + 1, item.size());

            if (array) {

                List<?> elements;
                if ("[".equals(value.get(0))) {
                    List<Object> resolved = new ArrayList<>();
                    for (List<String> element : Parsed.split(value.subList(1, value.size() - 1))) {
                        resolved.add(Parsed.resolve(element, symbols));
                    }
                    elements = resolved;
                } else {
                    elements = (List<?>) Parsed.resolve(value, symbols);
                }
                symbols.put(name, elements);

                // output_array([1..2,1..3])
                int annotation = annotations.indexOf("output_array");
                if (annotation >= 0) {
                    List<String> ranges = annotations.subList(annotation + 3, annotations.size());
                    List<String> dimensions = new ArrayList<>();
                    for (List<String> range : Parsed.split(ranges.subList(0, ranges.indexOf("]")))) {
                        dimensions.add(String.join("", range));
                    }
                    output.add(new Output(name, type, dimensions, elements));
                }

            } else {

                Object symbol = value.isEmpty() ? variables.get(name) : Parsed.resolve(value, symbols);
                if (symbol == null) {
                    throw new IllegalArgumentException("The model has no variable '" + name + "'");
                }
                symbols.put(name, symbol);

                if (annotations.contains("output_var")) {
                    output.add(new Output(name, type, null, List.of(symbol)));
                }
            }
        }

        /**
         * A name (of a variable or a constant), a number, true/false, or a set constant – kept as its text.
         */
        private static Object resolve(final List<String> term, final Map<String, Object> symbols) {

            String token = term.get(0);

            if (term.size() > 1) {
                return String.join("", term);
            }
            if ("true".equals(token)) {
                return BigDecimal.ONE;
            }
            if ("false".equals(token)) {
                return BigDecimal.ZERO;
            }
            if (Character.isLetter(token.charAt(0)) || token.charAt(0) == '_') {
                Object symbol = symbols.get(token);
                if (symbol == null) {
                    throw new IllegalArgumentException("'" + token + "' is not declared");
                }
                return symbol;
            }
            return new BigDecimal(token);
        }

        /**
         * At the commas that are not inside brackets
         */
        private static List<List<String>> split(final List<String> tokens) {

            List<List<String>> retVal = new ArrayList<>();

            int depth = 0;
            int start = 0;
            for (int i = 0; i < tokens.size(); i++) {
                String token = tokens.get(i);
                if ("(".equals(token) || "[".equals(token) || "{".equals(token)) {
                    depth++;
                } else if (")".equals(token) || "]".equals(token) || "}".equals(token)) {
                    depth--;
                } else if (depth == 0 && ",".equals(token)) {
                    retVal.add(tokens.subList(start, i));
                    start = i + 1;
                }
            }
            if (start < tokens.size()) {
                retVal.add(tokens.subList(start, tokens.size()));
            }

            return retVal;
        }

        /**
         * Names, numbers (hexadecimal and octal ones as decimal), punctuation (:: and .. as one token), and a
         * string as the token " – what it says does not matter here. Comments are skipped.
         */
        private static List<String> tokenize(final String text) {

            List<String> retVal = new ArrayList<>();

            int length = text.length();
            int i = 0;

            while (i < length) {

                int start = i;
                char c = text.charAt(i);

                if (Character.isWhitespace(c)) {
                    i++;
                } else if (c == '%') {
                    while (i < length && text.charAt(i) != '\n') {
                        i++;
                    }
                } else if (text.startsWith("/*", i)) {
                    int end = text.indexOf("*/", i + 2);
                    i = end < 0 ? length : end + 2;
                } else if (c == '"') {
                    for (i++; i < length && text.charAt(i) != '"'; i++) {
                        if (text.charAt(i) == '\\') {
                            i++;
                        }
                    }
                    i++;
                    retVal.add("\"");
                } else if (Character.isLetterOrDigit(c) || c == '_' || c == '-' && i + 1 < length && Character.isDigit(text.charAt(i + 1))) {
                    boolean number = c == '-' || Character.isDigit(c);
                    for (i++; i < length; i++) {
                        char d = text.charAt(i);
                        boolean next = i + 1 < length && Character.isDigit(text.charAt(i + 1));
                        if (Character.isLetterOrDigit(d) || d == '_') {
                            // and the e of an exponent
                        } else if (number && d == '.' && next) {
                            // a fraction, not a range
                        } else if (number && (d == '-' || d == '+') && (text.charAt(i - 1) == 'e' || text.charAt(i - 1) == 'E') && next) {
                            // the sign of an exponent
                        } else {
                            break;
                        }
                    }
                    String word = text.substring(start, i);
                    int radix = word.contains("0x") ? 16 : word.contains("0o") ? 8 : 10;
                    if (number && radix != 10) {
                        boolean negative = word.charAt(0) == '-';
                        long magnitude = Long.parseLong(word.substring(negative ? 3 : 2), radix);
                        word = Long.toString(negative ? -magnitude : magnitude);
                    }
                    retVal.add(word);
                } else if (text.startsWith("::", i) || text.startsWith("..", i)) {
                    i += 2;
                    retVal.add(text.substring(start, i));
                } else {
                    i++;
                    retVal.add(String.valueOf(c));
                }
            }

            return retVal;
        }

        private final ExpressionsBasedModel myModel;
        private final List<Output> myOutput;
        private final Map<String, Object> mySymbols;

        Parsed(final ExpressionsBasedModel model, final List<Output> output, final Map<String, Object> symbols) {
            super();
            myModel = model;
            myOutput = output;
            mySymbols = symbols;
        }

        private String format(final Access1D<?> solution, final Object element, final Type type) {

            BigDecimal value;
            if (element instanceof Variable) {
                value = new BigDecimal(String.valueOf(solution.get(myModel.indexOf((Variable) element))));
            } else if (element instanceof BigDecimal) {
                value = (BigDecimal) element;
            } else {
                return String.valueOf(element);
            }

            return switch (type) {
                case BOOL -> value.compareTo(HALF) > 0 ? "true" : "false";
                case INT  -> value.setScale(0, RoundingMode.HALF_EVEN).toPlainString();
                default   -> Double.toString(value.doubleValue());
            };
        }

        ExpressionsBasedModel getModel() {
            return myModel;
        }

        /**
         * Writes the value of every FlatZinc variable, not only the output variables – to check a solution
         * against the FlatZinc model.
         */
        void printAll(final Access1D<?> solution, final StringBuilder destination) {
            for (Map.Entry<String, Object> entry : mySymbols.entrySet()) {
                if (entry.getValue() instanceof Variable) {
                    destination.append(entry.getKey()).append(" = ");
                    destination.append(this.format(solution, entry.getValue(), Type.FLOAT));
                    destination.append(";\n");
                }
            }
        }

        /**
         * Writes the output variables, one per line, as {@code name = value;}. The solution has one value per
         * model variable, in the model's order – as a result from the model has.
         */
        void printSolution(final Access1D<?> solution, final StringBuilder destination) {
            for (Output output : myOutput) {
                destination.append(output.name()).append(" = ");
                if (output.dimensions() == null) {
                    destination.append(this.format(solution, output.elements().get(0), output.type()));
                } else {
                    destination.append("array").append(output.dimensions().size()).append("d(");
                    for (String dimension : output.dimensions()) {
                        destination.append(dimension).append(',');
                    }
                    destination.append('[');
                    for (int i = 0, limit = output.elements().size(); i < limit; i++) {
                        if (i > 0) {
                            destination.append(',');
                        }
                        destination.append(this.format(solution, output.elements().get(i), output.type()));
                    }
                    destination.append("])");
                }
                destination.append(";\n");
            }
        }

    }

    /**
     * The MIP gap tolerance used: requiring the gap to close completely – the objective is integral, and then
     * the gap closes at the next attainable value. The default tolerance would stop at a solution "close
     * enough" and print it as optimal.
     */
    public static final NumberContext GAP = NumberContext.of(12, 9);

    public static void main(final String[] args) {

        long start = System.nanoTime();

        boolean allValues = false;
        boolean intermediate = false;
        boolean parseOnly = false;
        boolean statistics = false;
        boolean verbose = false;
        int threads = 1;
        long timeLimit = -1L;
        String file = null;

        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "-a", "-i", "--all-solutions", "--intermediate-solutions" -> intermediate = true;
                    case "-f", "--free-search"                                     -> {
                        // always
                    }
                    case "-s", "--statistics"                                      -> statistics = true;
                    case "-v", "--verbose"                                         -> verbose = true;
                    case "-p", "--parallel"                                        -> threads = Integer.parseInt(args[++i]);
                    case "-t", "--time-limit"                                      -> timeLimit = Long.parseLong(args[++i]);
                    case "-n", "-r", "--num-solutions", "--random-seed", "--seed"  -> i++;
                    case "--all-values"                                            -> allValues = true;
                    case "--parse-only"                                            -> parseOnly = true;
                    default                                                        -> {
                        if (args[i].startsWith("-")) {
                            System.err.println("Ignoring unknown option " + args[i]);
                        } else {
                            file = args[i];
                        }
                    }
                }
            }
        } catch (RuntimeException cause) {
            FlatZinc.error("Bad options: " + cause.getMessage());
            return;
        }

        if (file == null) {
            FlatZinc.error("Usage: java -cp <classpath> org.ojalgo.benchmark.integer.FlatZinc [options] model.fzn");
            return;
        }

        Parsed parsed;
        try {
            parsed = Parsed.parse(file);
        } catch (IOException | RuntimeException cause) {
            FlatZinc.error(cause.getMessage());
            return;
        }

        long parsedAt = System.nanoTime();
        long parseMillis = (parsedAt - start) / 1_000_000L;

        ExpressionsBasedModel model = parsed.getModel();

        // MIN or MAX, or null for a satisfaction problem – read before solving, minimise() or maximise() sets it
        Optimisation.Sense sense = model.getOptimisationSense();

        if (parseOnly) {
            long integers = model.getVariables().stream().filter(Variable::isInteger).count();
            System.out.println("parsed variables=" + model.countVariables() + " integer=" + integers + " expressions=" + model.countExpressions() + " sense="
                    + sense + " ms=" + parseMillis);
            System.out.flush();
            System.exit(0);
        }

        model.options.parallelism(threads);
        IntegerStrategy.ConfigurableStrategy strategy = IntegerStrategy.DEFAULT.withGapTolerance(GAP);

        if (timeLimit > 0L) {
            // Some margin to stop, and print, before the time is up
            long margin = Math.max(500L, timeLimit / 50L);
            long remaining = Math.max(1L, timeLimit - parseMillis - margin);
            model.options.time_abort = remaining;
            model.options.time_suffice = remaining;
        }

        if (verbose) {
            System.err.println(
                    "% ojAlgo: " + model.countVariables() + " variables, " + model.countExpressions() + " expressions, parsed in " + parseMillis + " ms");
        }

        // The last solution printed
        AtomicReference<String> printed = new AtomicReference<>();
        AtomicInteger incumbents = new AtomicInteger();

        if (intermediate) {
            boolean withAllValues = allValues;
            strategy = strategy.withIncumbentListener(incumbent -> {
                incumbents.incrementAndGet();
                String solution = FlatZinc.solution(parsed, incumbent, withAllValues);
                System.out.print(solution);
                System.out.print("----------\n");
                System.out.flush();
                printed.set(solution);
            });
        }

        model.options.integer(strategy);

        Optimisation.Result result;
        try {
            result = sense == Optimisation.Sense.MAX ? model.maximise() : model.minimise();
        } catch (RuntimeException cause) {
            FlatZinc.error(cause.toString());
            return;
        }

        long solvedAt = System.nanoTime();

        Optimisation.State state = result.getState();

        StringBuilder text = new StringBuilder();

        if (state == Optimisation.State.UNBOUNDED) {
            text.append("=====UNBOUNDED=====\n");
        } else if (state.isFeasible()) {
            String solution = FlatZinc.solution(parsed, result, allValues);
            if (!solution.equals(printed.get())) {
                text.append(solution);
                text.append("----------\n");
            }
            if (state.isOptimal() && sense != null) {
                text.append("==========\n");
            }
        } else if (state == Optimisation.State.INFEASIBLE) {
            text.append("=====UNSATISFIABLE=====\n");
        } else {
            text.append("=====UNKNOWN=====\n");
        }

        if (statistics) {
            text.append("%%%mzn-stat: flatTime=").append((parsedAt - start) / 1.0E9).append('\n');
            text.append("%%%mzn-stat: solveTime=").append((solvedAt - parsedAt) / 1.0E9).append('\n');
            if (state.isFeasible() && sense != null) {
                text.append("%%%mzn-stat: objective=").append(result.getValue()).append('\n');
            }
            text.append("%%%mzn-stat-end\n");
        }

        System.out.print(text);
        System.out.flush();

        if (verbose) {
            System.err.println("% ojAlgo: " + state + " " + result.getValue() + ", solved in " + (solvedAt - parsedAt) / 1_000_000L + " ms, " + incumbents.get()
                    + " incumbents printed");
        }

        System.exit(0);
    }

    private static void error(final String message) {
        System.err.println(message);
        System.out.println("=====ERROR=====");
        System.out.flush();
        System.exit(1);
    }

    private static String solution(final Parsed parsed, final Optimisation.Result result, final boolean allValues) {
        StringBuilder retVal = new StringBuilder();
        parsed.printSolution(result, retVal);
        if (allValues) {
            retVal.append("% all values\n");
            parsed.printAll(result, retVal);
        }
        return retVal.toString();
    }

}
