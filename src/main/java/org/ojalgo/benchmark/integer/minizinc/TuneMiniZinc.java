package org.ojalgo.benchmark.integer.minizinc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.ojalgo.benchmark.AbstractBenchmark;
import org.ojalgo.benchmark.StrategyVariant;

/**
 * Searches for the ojAlgo MIP configuration ({@link StrategyVariant}) that does best on one category of the
 * minizinc collection:
 * <ol>
 * <li>Solve every model of the category with the default configuration. The models it solves in less than
 * {@link #TRIVIAL} ms are left out from then on – their times say nothing about the configuration.
 * <li>Solve the remaining models with every variant that differs from the best so far in one setting
 * ({@link StrategyVariant#neighbours()}), and with the best so far and the default again – under the same
 * conditions. Move to the best of the new variants if its score is at least {@link #MIN_IMPROVEMENT} better
 * than that of the best so far, and repeat – at most {@link #MAX_ROUNDS} times.
 * </ol>
 * A variant stops itself before {@link #MAX_WAIT_TIME}, with the best solution it has. Costs and scores are
 * as described in {@link AbstractMiniZinc}, with the score over the models where at least one of the
 * variants compared has a solution – the others say nothing about the configuration either.
 * <p>
 * Each round is a benchmark run of its own, with output files labelled with the category and the round.
 * The category is the first argument, "scheduling" if there is none. For quicker, coarser experiments, see
 * {@link CompareMiniZinc}.
 */
public final class TuneMiniZinc extends AbstractMiniZinc {

    /**
     * ms, per model and variant
     */
    static final long MAX_WAIT_TIME = 60_000L;
    static final int MAX_ROUNDS = 5;
    static final double MIN_IMPROVEMENT = 0.05;
    /**
     * ms
     */
    static final double TRIVIAL = 500.0;

    public static void main(final String[] args) {

        String category = args.length > 0 ? args[0] : "scheduling";

        Map<String, BigDecimal> references = AbstractMiniZinc.referenceValues();

        StrategyVariant defaultVariant = StrategyVariant.DEFAULT;

        Map<String, AbstractBenchmark.Outcome> defaultOutcomes = TuneMiniZinc.run(category, 0, List.of(defaultVariant), null).get(defaultVariant.toString());

        Set<String> training = new TreeSet<>();
        defaultOutcomes.forEach((model, outcome) -> {
            if (!(outcome.solved() && outcome.time() < TRIVIAL)) {
                training.add(model);
            }
        });

        System.out.println();
        System.out.println(category + ": " + defaultOutcomes.size() + " models, " + training.size() + " not trivial for the default configuration");
        System.out.println("Round 0: " + defaultVariant + " (" + AbstractMiniZinc.describe(defaultOutcomes, training) + ")");

        StrategyVariant best = defaultVariant;
        Set<StrategyVariant> tried = new HashSet<>(List.of(defaultVariant));

        // From the latest round: the costs of every variant in it, and the models that tell them apart
        Map<StrategyVariant, Map<String, Double>> costs = new HashMap<>();
        costs.put(defaultVariant, AbstractMiniZinc.costs(defaultOutcomes, training, references, MAX_WAIT_TIME));
        Set<String> informative = AbstractMiniZinc.informative(training, costs.values(), MAX_WAIT_TIME);

        for (int round = 1; round <= MAX_ROUNDS && !training.isEmpty(); round++) {

            List<StrategyVariant> candidates = new ArrayList<>(best.neighbours());
            candidates.removeAll(tried);
            tried.addAll(candidates);

            if (candidates.isEmpty()) {
                break;
            }

            // The best so far, and the default, are measured again with the candidates – under the same
            // conditions, as the first solve in a worker JVM includes its warm-up
            List<StrategyVariant> contenders = new ArrayList<>();
            contenders.add(best);
            if (!best.equals(defaultVariant)) {
                contenders.add(defaultVariant);
            }
            contenders.addAll(candidates);

            Map<String, Map<String, AbstractBenchmark.Outcome>> results = TuneMiniZinc.run(category, round, contenders, training);

            costs = new HashMap<>();
            for (StrategyVariant contender : contenders) {
                costs.put(contender, AbstractMiniZinc.costs(results.get(contender.toString()), training, references, MAX_WAIT_TIME));
            }
            informative = AbstractMiniZinc.informative(training, costs.values(), MAX_WAIT_TIME);

            double bestScore = AbstractMiniZinc.score(costs.get(best), informative);
            System.out.println("Round " + round + ": " + informative.size() + " models with a solution");

            StrategyVariant roundBest = null;
            double roundScore = Double.POSITIVE_INFINITY;
            for (StrategyVariant contender : contenders) {
                double score = AbstractMiniZinc.score(costs.get(contender), informative);
                String role = contender.equals(best) ? " best so far" : contender.equals(defaultVariant) ? " default" : "";
                System.out.println("Round " + round + ": " + contender + " " + AbstractMiniZinc.format(score) + " ("
                        + AbstractMiniZinc.describe(results.get(contender.toString()), training) + ")" + role);
                if (candidates.contains(contender) && score < roundScore) {
                    roundScore = score;
                    roundBest = contender;
                }
            }

            if (roundScore < (1.0 - MIN_IMPROVEMENT) * bestScore) {
                best = roundBest;
                System.out.println("Round " + round + " best: " + best + " " + AbstractMiniZinc.format(roundScore));
            } else {
                System.out.println("Round " + round + ": no improvement of at least " + Math.round(100 * MIN_IMPROVEMENT) + "%");
                break;
            }
        }

        Map<String, Double> defaultCosts = costs.get(defaultVariant);
        Map<String, Double> bestCosts = costs.get(best);

        System.out.println();
        System.out.println("Category:  " + category);
        System.out.println("Default:   " + defaultVariant + " " + AbstractMiniZinc.format(AbstractMiniZinc.score(defaultCosts, informative)));
        System.out.println("Best:      " + best + " " + AbstractMiniZinc.format(AbstractMiniZinc.score(bestCosts, informative)));
        System.out.println();
        System.out.println("Per problem (score, default -> best), to see whether one problem decides the result:");

        Map<String, Set<String>> problems = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String model : informative) {
            problems.computeIfAbsent(AbstractMiniZinc.problem(model), k -> new TreeSet<>()).add(model);
        }
        for (Map.Entry<String, Set<String>> entry : problems.entrySet()) {
            System.out.println("  " + entry.getKey() + ": " + AbstractMiniZinc.format(AbstractMiniZinc.score(defaultCosts, entry.getValue())) + " -> "
                    + AbstractMiniZinc.format(AbstractMiniZinc.score(bestCosts, entry.getValue())));
        }
    }

    /**
     * The variants' outcomes, by variant name and then model
     */
    static Map<String, Map<String, AbstractBenchmark.Outcome>> run(final String category, final int round, final List<StrategyVariant> variants,
            final Collection<String> models) {

        Configuration configuration = AbstractMiniZinc.newConfiguration(category, variants.stream().map(StrategyVariant::toString).toArray(String[]::new));
        configuration.label = category + "_round" + round;
        configuration.maxIterations = 1;
        configuration.maxWaitTime = MAX_WAIT_TIME;
        if (models != null) {
            configuration.models.retainAll(models);
        }

        return AbstractMiniZinc.run(configuration);
    }

}
