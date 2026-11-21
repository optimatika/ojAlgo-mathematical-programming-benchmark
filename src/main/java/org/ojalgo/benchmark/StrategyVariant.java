package org.ojalgo.benchmark;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.FlatZinc;
import org.ojalgo.optimisation.integer.IntegerSolver;
import org.ojalgo.optimisation.integer.IntegerStrategy;
import org.ojalgo.optimisation.integer.IntegerStrategy.BoundPropagation;
import org.ojalgo.optimisation.integer.IntegerStrategy.CutConfiguration;
import org.ojalgo.optimisation.integer.IntegerStrategy.CutType;
import org.ojalgo.optimisation.integer.NodeKey;

/**
 * A configuration of ojAlgo's MIP solver – a set of {@link IntegerStrategy} settings – that is its own
 * contender name, so that experiments can try configurations without registering each one in
 * {@link Contender}. The name is short enough to fit the benchmark log's columns:
 *
 * <pre>
 * MIP-BB-ICKFMG-3-100
 * MIP-DF-ICKFMG-3-100-B
 * MIP-BB+DF-ICKFMG-3-100-B
 * </pre>
 * <ol>
 * <li>The order nodes are processed in: BB (best bound), DF (depth first), BF (breadth first), LD (largest
 * displacement first) or SD (smallest displacement first) – or several joined with '+', such as BB+DF, to run
 * one worker thread per order (sharing the same tree)
 * <li>The cut types used, in the order they are attempted (always the default order), each as the first
 * letter of its {@link CutType#code} – I implied bounds, C clique, K knapsack cover, F flow cover, M mixed
 * integer rounding, G Gomory mixed integer – and a '.' for each type not used
 * <li>Separation rounds per node
 * <li>The most cuts one separator may keep per round
 * <li>Optionally, the {@link BoundPropagation} when branching: B (both branches) or F (filter the branch to be
 * deferred) – none without it
 * </ol>
 * Everything else is as {@link IntegerStrategy#DEFAULT}, with the gap tolerance ojAlgo's FlatZinc solver
 * uses ({@link FlatZinc#GAP}). A variant solves with one worker thread in the {@link IntegerSolver} per node
 * order – a single thread with one order, as in the MiniZinc Challenge's single core classes – and stops
 * itself at a time limit, with the best solution it has found, rather than being stopped from outside with
 * nothing.
 */
public record StrategyVariant(String nodes, List<CutType> cuts, int rounds, int maxCuts, BoundPropagation propagation) {

    public static final StrategyVariant DEFAULT;

    private static final List<CutType> DEFAULT_ORDER = IntegerStrategy.DEFAULT.getCutConfiguration().types;

    static final String PREFIX = "MIP-";

    private static final Map<BoundPropagation, String> PROPAGATION = Map.of(BoundPropagation.NONE, "", BoundPropagation.FILTER, "F", BoundPropagation.BOTH, "B");

    private static final Map<String, Comparator<NodeKey>> NODES = Map.of("BB", NodeKey.MIN_OBJECTIVE, "DF", NodeKey.DEPTH_FIRST_SEARCH, "BF",
            NodeKey.BREADTH_FIRST_SEARCH, "LD", NodeKey.LARGE_DISPLACEMENT, "SD", NodeKey.SMALL_DISPLACEMENT);

    static {
        CutConfiguration cuts = IntegerStrategy.DEFAULT.getCutConfiguration();
        DEFAULT = new StrategyVariant("BB", cuts.types, cuts.iterations, cuts.getMaxCuts(Integer.MAX_VALUE), BoundPropagation.NONE);
    }

    /**
     * The name as written by {@link #toString()}
     */
    public static StrategyVariant parse(final String name) {

        String[] parts = name.split("-");

        if (!name.startsWith(PREFIX) || parts.length < 5 || parts.length > 6 || parts[2].length() != DEFAULT_ORDER.size()) {
            throw new IllegalArgumentException("Not a strategy variant: " + name);
        }

        List<CutType> cuts = new ArrayList<>();
        for (int i = 0; i < DEFAULT_ORDER.size(); i++) {
            char letter = parts[2].charAt(i);
            if (letter == StrategyVariant.letter(DEFAULT_ORDER.get(i))) {
                cuts.add(DEFAULT_ORDER.get(i));
            } else if (letter != '.') {
                throw new IllegalArgumentException("Not a strategy variant: " + name);
            }
        }

        BoundPropagation propagation = BoundPropagation.NONE;
        if (parts.length == 6) {
            propagation = PROPAGATION.entrySet().stream().filter(entry -> entry.getValue().equals(parts[5])).map(Map.Entry::getKey).findFirst()
                    .filter(found -> found != BoundPropagation.NONE).orElseThrow(() -> new IllegalArgumentException("Not a strategy variant: " + name));
        }

        return new StrategyVariant(parts[1], cuts, Integer.parseInt(parts[3]), Integer.parseInt(parts[4]), propagation);
    }

    /**
     * The first letter of its {@link CutType#code}
     */
    private static char letter(final CutType type) {
        return type.code.charAt(0);
    }

    public StrategyVariant {
        for (String order : nodes.split("\\+")) {
            if (!NODES.containsKey(order)) {
                throw new IllegalArgumentException("Unknown node order " + nodes);
            }
        }
        List<CutType> given = cuts;
        cuts = DEFAULT_ORDER.stream().filter(given::contains).toList();
        rounds = Math.max(0, rounds);
        maxCuts = Math.max(1, maxCuts);
        if (propagation == null) {
            propagation = BoundPropagation.NONE;
        }
    }

    /**
     * Every variant that differs from this one in one setting: another node order, one cut type more or
     * less (or none at all), one round more or less, half or twice as many cuts, another bound propagation.
     */
    public List<StrategyVariant> neighbours() {

        List<StrategyVariant> retVal = new ArrayList<>();

        for (String other : List.of("BB", "DF", "LD", "SD")) {
            if (!other.equals(nodes)) {
                retVal.add(new StrategyVariant(other, cuts, rounds, maxCuts, propagation));
            }
        }

        if (!cuts.isEmpty()) {
            retVal.add(new StrategyVariant(nodes, List.of(), rounds, maxCuts, propagation));
        }
        for (CutType type : DEFAULT_ORDER) {
            List<CutType> changed = new ArrayList<>();
            // With this type toggled
            for (CutType candidate : DEFAULT_ORDER) {
                if (candidate == type ? !cuts.contains(type) : cuts.contains(candidate)) {
                    changed.add(candidate);
                }
            }
            if (!changed.isEmpty()) {
                retVal.add(new StrategyVariant(nodes, changed, rounds, maxCuts, propagation));
            }
        }

        if (!cuts.isEmpty()) {
            if (rounds > 1) {
                retVal.add(new StrategyVariant(nodes, cuts, rounds - 1, maxCuts, propagation));
            }
            retVal.add(new StrategyVariant(nodes, cuts, rounds + 1, maxCuts, propagation));
            if (maxCuts > 10) {
                retVal.add(new StrategyVariant(nodes, cuts, rounds, maxCuts / 2, propagation));
            }
            retVal.add(new StrategyVariant(nodes, cuts, rounds, maxCuts * 2, propagation));
        }

        for (BoundPropagation other : BoundPropagation.values()) {
            if (other != propagation) {
                retVal.add(new StrategyVariant(nodes, cuts, rounds, maxCuts, other));
            }
        }

        retVal.removeIf(this::equals);
        return retVal.stream().distinct().toList();
    }

    /**
     * @param timeLimit ms – the solver stops then, and returns the best solution it has found, if any
     */
    public ExpressionsBasedModel.Integration<?> toIntegration(final long timeLimit) {
        IntegerStrategy strategy = this.toStrategy();
        return IntegerSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.integer(strategy);
            opt.parallelism(this.orders().length);
            opt.time_abort = timeLimit;
            opt.time_suffice = timeLimit;
        });
    }

    @SuppressWarnings("unchecked")
    public IntegerStrategy toStrategy() {
        CutConfiguration configuration = IntegerStrategy.DEFAULT.getCutConfiguration().withTypes(cuts.toArray(new CutType[cuts.size()]))
                .withIterations(rounds).withMaxCuts(10, 3, maxCuts);
        Comparator<NodeKey>[] definitions = Arrays.stream(this.orders()).map(NODES::get).toArray(Comparator[]::new);
        return IntegerStrategy.DEFAULT.withGapTolerance(FlatZinc.GAP).withPriorityDefinitions(definitions).withCutConfiguration(configuration)
                .withBoundPropagation(propagation);
    }

    /**
     * The node orders, one per worker thread
     */
    public String[] orders() {
        return nodes.split("\\+");
    }

    @Override
    public String toString() {
        StringBuilder letters = new StringBuilder();
        for (CutType type : DEFAULT_ORDER) {
            letters.append(cuts.contains(type) ? StrategyVariant.letter(type) : '.');
        }
        String suffix = PROPAGATION.get(propagation);
        return PREFIX + nodes + "-" + letters + "-" + rounds + "-" + maxCuts + (suffix.isEmpty() ? "" : "-" + suffix);
    }

}
