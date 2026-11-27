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
package org.ojalgo.benchmark.integer.minizinc;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.ojalgo.benchmark.AbstractBenchmark;
import org.ojalgo.netio.TextLineReader;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Optimisation;

/**
 * The MiniZinc collection: mostly constraint programming problems, compiled to FlatZinc with MiniZinc's
 * linear library – so MIP models. The models are named {@code <category>/<name>}, as in {@code MINIZINC.dat},
 * and the name starts with the problem it is an instance of: {@code <problem>__<instance>}.
 * <p>
 * A satisfaction problem has no objective (and no optimisation sense) – solving one means finding a
 * solution, any solution, and its objective function value is 0.
 * <p>
 * Also how a model/solver outcome is scored, by the tuning experiments here: a model that is solved costs its
 * solve time, one with a solution that is not proven optimal costs between 1 and 2 times the time limit –
 * depending on its gap to the reference value in {@code MINIZINC.solu} – and one without a solution costs 2
 * times the time limit. A score is the shifted geometric mean of costs. Lower is better.
 */
abstract class AbstractMiniZinc extends AbstractBenchmark {

    /**
     * What there is to know about a model without reading it: its category (the folder it is in), the problem
     * it is an instance of, and the reference result from {@code MINIZINC.solu} – the status and, for an
     * optimisation model with a solution, the objective function value.
     *
     * @param status optimal, feasible (a solution, not proven optimal), solved (a solution to a satisfaction
     *        problem), unsat or unknown. From HiGHS with a 30 s time limit, then the MiniZinc Challenge's proven
     *        optima where CP-SAT or SCIP confirmed them, so feasible and unknown only mean that nothing has
     *        got further yet.
     */
    record ModelInfo(String category, String problem, String status, BigDecimal value) {

        /**
         * The reference solver proved a solution optimal, or found a solution to a satisfaction problem
         */
        boolean isSolvedByReference() {
            return OPTIMAL.equals(status) || SOLVED.equals(status);
        }

    }

    /**
     * ms
     */
    static final double SHIFT = 1_000.0;

    private static final String OPTIMAL = "optimal";
    private static final String RESOURCE_DIR = "optimisation/minizinc/";

    /**
     * Read once – MIN, MAX or (satisfaction) absent
     */
    private static final Map<String, Optional<Optimisation.Sense>> SENSES = new ConcurrentHashMap<>();

    private static final String SOLVED = "solved";
    private static final String UNKNOWN = "unknown";
    private static final String UNSAT = "unsat";

    /**
     * The categories there are – the folders of the collection, such as "scheduling"
     */
    static Set<String> categories() {
        Set<String> retVal = new TreeSet<>();
        for (String model : AbstractBenchmark.readIndex(RESOURCE_DIR + "MINIZINC.dat")) {
            retVal.add(model.substring(0, model.indexOf('/')));
        }
        return retVal;
    }

    /**
     * Cost, ms, of an outcome – see the class documentation
     */
    static double cost(final Outcome outcome, final BigDecimal reference, final Optimisation.Sense sense, final long maxWaitTime) {

        if (outcome == null || !outcome.solved() && Double.isNaN(outcome.value())) {
            return 2.0 * maxWaitTime;
        }

        if (outcome.solved()) {
            return outcome.time();
        }

        if (reference == null || sense == null) {
            // A solution, but nothing to compare it with
            return 1.5 * maxWaitTime;
        }

        double ref = reference.doubleValue();
        double gap = sense == Optimisation.Sense.MAX ? ref - outcome.value() : outcome.value() - ref;
        gap = Math.max(0.0, gap) / Math.max(1.0, Math.abs(ref));

        return maxWaitTime * (1.0 + gap / (1.0 + gap));
    }

    /**
     * The costs of these outcomes, by model
     *
     * @param references The best value known for each model, see {@link #referenceValues()}
     */
    static Map<String, Double> costs(final Map<String, Outcome> outcomes, final Collection<String> models, final Map<String, BigDecimal> references,
            final long maxWaitTime) {
        Map<String, Double> retVal = new TreeMap<>();
        for (String model : models) {
            Outcome outcome = outcomes != null ? outcomes.get(model) : null;
            retVal.put(model, AbstractMiniZinc.cost(outcome, references.get(model), AbstractMiniZinc.sense(model), maxWaitTime));
        }
        return retVal;
    }

    /**
     * How many models solved, and how many with a solution, of these
     */
    static String describe(final Map<String, Outcome> outcomes, final Collection<String> models) {
        int solved = 0;
        int solution = 0;
        for (String model : models) {
            Outcome outcome = outcomes != null ? outcomes.get(model) : null;
            if (outcome != null && outcome.solved()) {
                solved++;
            }
            if (outcome != null && (outcome.solved() || !Double.isNaN(outcome.value()))) {
                solution++;
            }
        }
        return solved + " solved, " + solution + " with a solution";
    }

    /**
     * Keeps only the models whose info satisfies the predicate. Models without info are dropped.
     */
    static void filter(final Configuration configuration, final Predicate<ModelInfo> predicate) {
        Map<String, ModelInfo> info = AbstractMiniZinc.getModelInfo();
        configuration.models.removeIf(model -> !info.containsKey(model) || !predicate.test(info.get(model)));
    }

    /**
     * A score, ms, in seconds
     */
    static String format(final double score) {
        return String.format("%.3f s", score / 1_000.0);
    }

    /**
     * The info of every model listed in {@code MINIZINC.dat}, by model
     */
    static Map<String, ModelInfo> getModelInfo() {

        Map<String, String[]> references = new HashMap<>();

        try (TextLineReader reader = new TextLineReader(Thread.currentThread().getContextClassLoader().getResourceAsStream(RESOURCE_DIR + "MINIZINC.solu"))) {
            reader.forEach(line -> {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 2 && !parts[0].startsWith("#")) {
                    references.put(parts[0], parts);
                }
            });
        } catch (IOException cause) {
            throw new RuntimeException(cause);
        }

        Map<String, ModelInfo> retVal = new TreeMap<>();

        for (String model : AbstractBenchmark.readIndex(RESOURCE_DIR + "MINIZINC.dat")) {

            String category = model.substring(0, model.indexOf('/'));
            String name = model.substring(model.indexOf('/') + 1);
            String problem = name.contains("__") ? name.substring(0, name.indexOf("__")) : name;

            String[] reference = references.get(name);
            String status = reference != null ? reference[1] : UNKNOWN;
            BigDecimal value = reference != null && reference.length >= 3 ? new BigDecimal(reference[2]) : null;

            retVal.put(model, new ModelInfo(category, problem, status, value));
        }

        return retVal;
    }

    /**
     * The models where at least one of these has a solution
     */
    static Set<String> informative(final Collection<String> models, final Collection<Map<String, Double>> costs, final long maxWaitTime) {
        Set<String> retVal = new TreeSet<>();
        for (String model : models) {
            for (Map<String, Double> cost : costs) {
                if (cost.get(model) < 2.0 * maxWaitTime) {
                    retVal.add(model);
                }
            }
        }
        return retVal;
    }

    /**
     * All models listed in {@code MINIZINC.dat}, except those {@code MINIZINC.solu} says are infeasible
     * (unsat), with the optimal values from {@code MINIZINC.solu} as expected values – and 0 for the
     * satisfaction problems it has a solution to. The collection only has models with at most 10k variables
     * and constraints – the usual size limit.
     */
    static Configuration newConfiguration(final String... solvers) {

        Configuration configuration = new Configuration(solvers);

        configuration.pathPrefix = "/" + RESOURCE_DIR;
        configuration.pathSuffix = ".fzn";

        configuration.models.addAll(AbstractBenchmark.readIndex(RESOURCE_DIR + "MINIZINC.dat"));

        for (Map.Entry<String, ModelInfo> entry : AbstractMiniZinc.getModelInfo().entrySet()) {
            ModelInfo info = entry.getValue();
            if (UNSAT.equals(info.status())) {
                configuration.models.remove(entry.getKey());
            } else if (OPTIMAL.equals(info.status()) && info.value() != null) {
                configuration.values.put(entry.getKey(), info.value());
            } else if (SOLVED.equals(info.status())) {
                configuration.values.put(entry.getKey(), BigDecimal.ZERO);
            }
        }

        return configuration;
    }

    /**
     * The best objective function value known for each optimisation model – optimal or not – by model
     */
    static Map<String, BigDecimal> referenceValues() {
        Map<String, BigDecimal> retVal = new HashMap<>();
        for (Map.Entry<String, ModelInfo> entry : AbstractMiniZinc.getModelInfo().entrySet()) {
            if (entry.getValue().value() != null) {
                retVal.put(entry.getKey(), entry.getValue().value());
            }
        }
        return retVal;
    }

    /**
     * Keeps only the models of these categories – all of them if there are none – and labels the run with
     * the categories, so that each category run has output files of its own.
     */
    static void retainCategories(final Configuration configuration, final String... categories) {

        if (categories.length == 0) {
            return;
        }

        Set<String> known = AbstractMiniZinc.categories();
        List<String> selected = List.of(categories);
        for (String category : selected) {
            if (!known.contains(category)) {
                throw new IllegalArgumentException("Unknown category '" + category + "' – there are " + known);
            }
        }

        AbstractMiniZinc.filter(configuration, info -> selected.contains(info.category()));
        configuration.label = String.join("+", selected);
    }

    /**
     * Runs the benchmark, and returns the outcomes by solver and then model
     */
    static Map<String, Map<String, Outcome>> run(final Configuration configuration) {
        Map<String, Map<String, Outcome>> retVal = new TreeMap<>();
        AbstractBenchmark.doBenchmark(configuration).forEach((pair, outcome) -> retVal.computeIfAbsent(pair.solver, k -> new TreeMap<>()).put(pair.model, outcome));
        return retVal;
    }

    /**
     * Shifted geometric mean of the costs, ms, of these models
     */
    static double score(final Map<String, Double> costs, final Collection<String> models) {
        if (models.isEmpty()) {
            return Double.NaN;
        }
        double sum = 0.0;
        for (String model : models) {
            sum += Math.log(costs.get(model) + SHIFT);
        }
        return Math.exp(sum / models.size()) - SHIFT;
    }

    /**
     * MIN or MAX, as the model says – null for a satisfaction problem (or a model that cannot be read)
     */
    static Optimisation.Sense sense(final String model) {
        return SENSES.computeIfAbsent(model, m -> Optional.ofNullable(AbstractMiniZinc.readSense(m))).orElse(null);
    }

    private static Optimisation.Sense readSense(final String model) {
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(RESOURCE_DIR + model + ".fzn")) {
            return ExpressionsBasedModel.parse(input, ExpressionsBasedModel.FileFormat.FZN).getOptimisationSense();
        } catch (IOException | RuntimeException cause) {
            return null;
        }
    }

}
