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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.ojalgo.benchmark.StrategyVariant;

/**
 * Quick, coarse experiments: a few ojAlgo MIP configurations ({@link StrategyVariant}s) on the quick set of
 * the minizinc collection – the models with at most {@link #MAX_SIZE} variables and constraints that the
 * reference solver solved – with a short time limit, {@link #MAX_WAIT_TIME}. One benchmark run, no search.
 * <p>
 * The arguments are the configurations to compare, by name, such as {@code MIP-BB-ICKFMG-3-100} – the default
 * and a few others if there are none – optionally preceded by {@code -c <category>} to use only that
 * category's models.
 * <p>
 * Prints, for all models, for the optimisation and the satisfaction models, and for each category, each
 * configuration's score – costs and scores as described
 * in {@link AbstractMiniZinc}, over the models where at least one of the configurations has a solution – and
 * how many models it solved, and found a solution to. Then, for each configuration, on how many models it
 * did best.
 */
public final class CompareMiniZinc extends AbstractMiniZinc {

    /**
     * ms, per model and configuration
     */
    static final long MAX_WAIT_TIME = 10_000L;
    static final int MAX_SIZE = 1_000;
    /**
     * A win only counts as clear when every other configuration costs at least this much more – absolute (ms) and relative.
     */
    static final double CLEAR_MARGIN_MS = 10.0;
    static final double CLEAR_MARGIN_REL = 0.10;

    public static void main(final String[] args) {

        String category = null;
        List<StrategyVariant> variants = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if ("-c".equals(args[i])) {
                category = args[++i];
            } else {
                variants.add(StrategyVariant.parse(args[i]));
            }
        }
        if (variants.isEmpty()) {
            variants.add(StrategyVariant.DEFAULT);
            variants.add(StrategyVariant.parse("MIP-BB-......-3-100"));
            variants.add(StrategyVariant.parse("MIP-BB-.CKFMG-3-100"));
            variants.add(StrategyVariant.parse("MIP-DF-ICKFMG-3-100"));
        }

        Configuration configuration = AbstractMiniZinc.newConfiguration(variants.stream().map(StrategyVariant::toString).toArray(String[]::new));
        if (category != null) {
            AbstractMiniZinc.retainCategories(configuration, category);
        } else {
            configuration.label = "all";
        }
        configuration.maxIterations = 1;
        configuration.maxWaitTime = MAX_WAIT_TIME;
        configuration.maxProbSize = MAX_SIZE;

        AbstractMiniZinc.filter(configuration, ModelInfo::isSolvedByReference);

        Map<String, Map<String, Outcome>> results = AbstractMiniZinc.run(configuration);

        // The models that were run – within the size limit
        Set<String> models = new TreeSet<>();
        results.values().forEach(outcomes -> models.addAll(outcomes.keySet()));

        Map<String, BigDecimal> references = AbstractMiniZinc.referenceValues();
        Map<StrategyVariant, Map<String, Double>> costs = new TreeMap<>((a, b) -> a.toString().compareTo(b.toString()));
        for (StrategyVariant variant : variants) {
            costs.put(variant, AbstractMiniZinc.costs(results.get(variant.toString()), models, references, MAX_WAIT_TIME));
        }

        Map<String, ModelInfo> info = AbstractMiniZinc.getModelInfo();
        Map<String, Set<String>> categories = new TreeMap<>();
        for (String model : models) {
            categories.computeIfAbsent(info.get(model).category(), k -> new TreeSet<>()).add(model);
        }
        categories.put("(all)", models);
        for (String model : models) {
            String kind = AbstractMiniZinc.sense(model) != null ? "(optimisation)" : "(satisfaction)";
            categories.computeIfAbsent(kind, k -> new TreeSet<>()).add(model);
        }

        System.out.println();
        for (Map.Entry<String, Set<String>> entry : categories.entrySet()) {

            Set<String> informative = AbstractMiniZinc.informative(entry.getValue(), costs.values(), MAX_WAIT_TIME);

            System.out.println(entry.getKey() + ": " + entry.getValue().size() + " models, " + informative.size() + " with a solution");
            for (StrategyVariant variant : variants) {
                System.out.println(String.format("  %-22s %10s   %s", variant, AbstractMiniZinc.format(AbstractMiniZinc.score(costs.get(variant), informative)),
                        AbstractMiniZinc.describe(results.get(variant.toString()), entry.getValue())));
            }
        }

        System.out.println();
        System.out.println("Best on (models where at least one has a solution – ties count for each), and clearly best on");
        System.out.println("(at least " + Math.round(CLEAR_MARGIN_MS) + " ms and " + Math.round(100.0 * CLEAR_MARGIN_REL) + " % cheaper than every other):");
        Set<String> informative = AbstractMiniZinc.informative(models, costs.values(), MAX_WAIT_TIME);
        for (StrategyVariant variant : variants) {
            int wins = 0;
            int clear = 0;
            for (String model : informative) {
                double own = costs.get(variant).get(model);
                double min = Double.POSITIVE_INFINITY;
                double others = Double.POSITIVE_INFINITY;
                for (Map.Entry<StrategyVariant, Map<String, Double>> entry : costs.entrySet()) {
                    double cost = entry.getValue().get(model);
                    min = Math.min(min, cost);
                    if (!entry.getKey().equals(variant)) {
                        others = Math.min(others, cost);
                    }
                }
                if (own <= min) {
                    wins++;
                }
                if (others - own >= CLEAR_MARGIN_MS && others >= (1.0 + CLEAR_MARGIN_REL) * own) {
                    clear++;
                }
            }
            System.out.println(String.format("  %-22s %4d %4d", variant, wins, clear));
        }
    }

}
