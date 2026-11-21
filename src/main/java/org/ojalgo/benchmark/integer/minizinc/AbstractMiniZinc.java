package org.ojalgo.benchmark.integer.minizinc;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.ojalgo.benchmark.AbstractBenchmark;
import org.ojalgo.netio.TextLineReader;
import org.ojalgo.optimisation.Optimisation;

/**
 * The models of the minizinc collection are named {@code <category>/<name>}, as in {@code MINIZINC.dat} –
 * the name is that in {@code MINIZINC.solu}, and starts with the problem: {@code <problem>__<instance>}.
 * <p>
 * Also how a model/solver outcome is scored, by the experiments here: a model that is solved costs its solve
 * time, one with a solution that is not proven optimal costs between 1 and 2 times the time limit –
 * depending on its gap to the reference value in {@code MINIZINC.solu} – and one without a solution costs 2
 * times the time limit. A score is the shifted geometric mean of costs. Lower is better.
 */
abstract class AbstractMiniZinc extends AbstractBenchmark {

    /**
     * One line of {@code MINIZINC.solu}: the model name, the reference solver's status (optimal, feasible,
     * solved, unsat or unknown) and, for an optimisation model with a solution, the objective function value
     */
    record Reference(String name, String status, BigDecimal value) {

    }

    /**
     * ms
     */
    static final double SHIFT = 1_000.0;

    private static final String RESOURCE_DIR = "optimisation/minizinc/";

    /**
     * Read once – MIN, MAX or (satisfaction) absent
     */
    private static final Map<String, Optional<Optimisation.Sense>> SENSES = new ConcurrentHashMap<>();

    static String category(final String model) {
        return model.substring(0, model.indexOf('/'));
    }

    /**
     * Cost, ms, of an outcome – see the class documentation
     */
    static double cost(final AbstractBenchmark.Outcome outcome, final BigDecimal reference, final Optimisation.Sense sense, final long maxWaitTime) {

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
     */
    static Map<String, Double> costs(final Map<String, AbstractBenchmark.Outcome> outcomes, final Collection<String> models,
            final Map<String, BigDecimal> references, final long maxWaitTime) {
        Map<String, Double> retVal = new TreeMap<>();
        for (String model : models) {
            AbstractBenchmark.Outcome outcome = outcomes != null ? outcomes.get(model) : null;
            retVal.put(model, AbstractMiniZinc.cost(outcome, references.get(AbstractMiniZinc.name(model)), AbstractMiniZinc.sense(model), maxWaitTime));
        }
        return retVal;
    }

    /**
     * How many models solved, and how many with a solution, of these
     */
    static String describe(final Map<String, AbstractBenchmark.Outcome> outcomes, final Collection<String> models) {
        int solved = 0;
        int solution = 0;
        for (String model : models) {
            AbstractBenchmark.Outcome outcome = outcomes != null ? outcomes.get(model) : null;
            if (outcome != null && outcome.solved()) {
                solved++;
            }
            if (outcome != null && (outcome.solved() || !Double.isNaN(outcome.value()))) {
                solution++;
            }
        }
        return solved + " solved, " + solution + " with a solution";
    }

    static String format(final double score) {
        return String.format("%.3f s", score / 1_000.0);
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

    static String name(final String model) {
        return model.substring(model.indexOf('/') + 1);
    }

    /**
     * All models of one category – a folder of the minizinc collection, such as "scheduling" – or of all
     * categories (null), except those {@code MINIZINC.solu} says are infeasible, with the optimal values from
     * {@code MINIZINC.solu} as expected values. Models of satisfaction problems have no objective, and
     * solving one means finding a solution.
     */
    static Configuration newConfiguration(final String category, final String... solvers) {

        Configuration configuration = new Configuration(solvers);

        configuration.pathPrefix = "/" + RESOURCE_DIR;
        configuration.pathSuffix = ".fzn";

        for (String model : AbstractBenchmark.readIndex(RESOURCE_DIR + "MINIZINC.dat")) {
            if (category == null || category.equals(AbstractMiniZinc.category(model))) {
                configuration.models.add(model);
            }
        }

        Map<String, Reference> references = new HashMap<>();
        for (Reference reference : AbstractMiniZinc.references()) {
            references.put(reference.name(), reference);
        }

        for (String model : new ArrayList<>(configuration.models)) {
            Reference reference = references.get(AbstractMiniZinc.name(model));
            if (reference != null && "unsat".equals(reference.status())) {
                configuration.models.remove(model);
            } else if (reference != null && "optimal".equals(reference.status()) && reference.value() != null) {
                configuration.values.put(model, reference.value());
            }
        }

        return configuration;
    }

    static String problem(final String model) {
        String name = AbstractMiniZinc.name(model);
        return name.substring(0, name.indexOf("__"));
    }

    /**
     * Every line of {@code MINIZINC.solu}
     */
    static List<Reference> references() {

        List<Reference> retVal = new ArrayList<>();

        try (TextLineReader reader = new TextLineReader(Thread.currentThread().getContextClassLoader().getResourceAsStream(RESOURCE_DIR + "MINIZINC.solu"))) {

            reader.forEach(line -> {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 2 && !parts[0].startsWith("#")) {
                    retVal.add(new Reference(parts[0], parts[1], parts.length >= 3 ? new BigDecimal(parts[2]) : null));
                }
            });

        } catch (IOException cause) {
            throw new RuntimeException(cause);
        }

        return retVal;
    }

    /**
     * The best objective function value known for each optimisation model (by name) – optimal or not
     */
    static Map<String, BigDecimal> referenceValues() {
        Map<String, BigDecimal> retVal = new HashMap<>();
        for (Reference reference : AbstractMiniZinc.references()) {
            if (reference.value() != null) {
                retVal.put(reference.name(), reference.value());
            }
        }
        return retVal;
    }

    /**
     * Runs the benchmark, and returns the outcomes by solver and then model
     */
    static Map<String, Map<String, AbstractBenchmark.Outcome>> run(final Configuration configuration) {
        Map<String, Map<String, AbstractBenchmark.Outcome>> retVal = new TreeMap<>();
        AbstractBenchmark.doBenchmark(configuration)
                .forEach((pair, outcome) -> retVal.computeIfAbsent(pair.solver, k -> new TreeMap<>()).put(pair.model, outcome));
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
     * MIN or MAX, from the model's solve item – null for a satisfaction problem
     */
    static Optimisation.Sense sense(final String model) {
        return SENSES.computeIfAbsent(model, m -> Optional.ofNullable(AbstractMiniZinc.readSense(m))).orElse(null);
    }

    private static Optimisation.Sense readSense(final String model) {

        String[] solve = { null };

        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(RESOURCE_DIR + model + ".fzn");
                TextLineReader reader = new TextLineReader(input)) {

            reader.forEach(line -> {
                if (line.startsWith("solve")) {
                    solve[0] = line;
                }
            });

        } catch (IOException | RuntimeException cause) {
            return null;
        }

        if (solve[0] == null) {
            return null;
        } else if (solve[0].contains(" minimize ")) {
            return Optimisation.Sense.MIN;
        } else if (solve[0].contains(" maximize ")) {
            return Optimisation.Sense.MAX;
        } else {
            return null;
        }
    }

    /**
     * The models (by name) the reference solver solved: proven optimal, or a solution to a satisfaction
     * problem
     */
    static Set<String> solvedByReference() {
        Set<String> retVal = new TreeSet<>();
        for (Reference reference : AbstractMiniZinc.references()) {
            if ("optimal".equals(reference.status()) || "solved".equals(reference.status())) {
                retVal.add(reference.name());
            }
        }
        return retVal;
    }

}
