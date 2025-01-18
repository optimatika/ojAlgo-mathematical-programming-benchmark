/*
 * Copyright 1997-2025 Optimatika
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
package org.ojalgo.benchmark;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.ojalgo.netio.ASCII;
import org.ojalgo.netio.BasicLogger;
import org.ojalgo.netio.TextLineWriter;
import org.ojalgo.netio.TextLineWriter.CSVLineBuilder;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.ExpressionsBasedModel.FileFormat;
import org.ojalgo.optimisation.ExpressionsBasedModel.Integration;
import org.ojalgo.optimisation.Optimisation;
import org.ojalgo.optimisation.Optimisation.Result;
import org.ojalgo.optimisation.Optimisation.State;
import org.ojalgo.optimisation.linear.LinearSolver;
import org.ojalgo.optimisation.solver.acm.SolverACM;
import org.ojalgo.optimisation.solver.cplex.SolverCPLEX;
import org.ojalgo.optimisation.solver.hipparchus.SolverHipparchus;
import org.ojalgo.optimisation.solver.joptimizer.SolverJOptimizer;
import org.ojalgo.optimisation.solver.ortools.SolverORTools;
import org.ojalgo.type.CalendarDateDuration;
import org.ojalgo.type.CalendarDateUnit;
import org.ojalgo.type.Stopwatch;
import org.ojalgo.type.Stopwatch.TimedResult;
import org.ojalgo.type.context.NumberContext;

public abstract class AbstractBenchmark {

    public static final class Configuration {

        public NumberContext accuracy = NumberContext.of(4);
        /**
         * ms
         */
        public long maxWaitTime = 60_000L;
        public String pathPrefix;
        public String pathSuffix = ".SIF";
        public String refeenceSolver = Contender.CPLEX;
        public final Map<String, BigDecimal> values = new HashMap<>();

        public String path(final String modelName) {
            return pathPrefix + modelName + pathSuffix;
        }

    }

    public static final class Contender {

        public static final String ACM = "ACM";
        public static final String CPLEX = "CPLEX";
        public static final String HIPPARCHUS = "Hipparchus";
        public static final String JOPTIMIZER = "JOptimizer";
        public static final String OJALGO = "ojAlgo";
        public static final String OJALGO_DUAL_DENSE = "ojAlgo-dual-D";
        public static final String OJALGO_DUAL_SPARSE = "ojAlgo-dual-S";
        public static final String OJALGO_PRIM_DENSE = "ojAlgo-prim-D";
        public static final String OJALGO_PRIM_SPARSE = "ojAlgo-prim-S";
        public static final String ORTOOLS = "ORTools";

    }

    public static final class ModelSolverPair implements Comparable<ModelSolverPair> {

        public final String model;
        public final String solver;

        public ModelSolverPair(final String m, final String s) {
            super();
            model = m;
            solver = s;
        }

        @Override
        public int compareTo(final ModelSolverPair other) {
            int mod = model.compareTo(other.model);
            if (mod == 0) {
                return solver.compareTo(other.solver);
            }
            return mod;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ModelSolverPair)) {
                return false;
            }
            ModelSolverPair other = (ModelSolverPair) obj;
            if (model == null) {
                if (other.model != null) {
                    return false;
                }
            } else if (!model.equals(other.model)) {
                return false;
            }
            if (solver == null) {
                if (other.solver != null) {
                    return false;
                }
            } else if (!solver.equals(other.solver)) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (model == null ? 0 : model.hashCode());
            return prime * result + (solver == null ? 0 : solver.hashCode());
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("ModelSolverPair [model=");
            builder.append(model);
            builder.append(", solver=");
            builder.append(solver);
            builder.append("]");
            return builder.toString();
        }
    }

    enum FailReason {
        /**
         * Hangs or takes too long
         */
        TIMEOUT,
        /**
         * Not always the same results between executions (a variation on WRONG)
         */
        UNSTABLE,
        /**
         * Does not match the expected value, or the reference solver
         */
        WRONG;
    }

    static final class ModelSize {

        public final int nbExpressions;
        public final int nbVariables;

        ModelSize(final int m, final int n) {
            super();
            nbExpressions = m;
            nbVariables = n;
        }

    }

    static final class ResultsSet {

        static boolean isSimilar(final double value1, final double value2, final double halfRelativeError) {
            if (Math.abs(value1 - value2) / (value1 + value2) > halfRelativeError) {
                return false;
            }
            return true;
        }

        public TimedResult<Optimisation.Result> fastest;

        private final List<TimedResult<Optimisation.Result>> all = new ArrayList<>();
        private final double myHalfRelativeTimeError;
        private final NumberContext myValueAccuracy;

        public ResultsSet(final NumberContext valueAccuracy, final double timeAccuracy) {
            super();
            myValueAccuracy = valueAccuracy;
            myHalfRelativeTimeError = timeAccuracy / 2D;
        }

        public void add(final TimedResult<Result> another) {

            Objects.requireNonNull(another);

            Result anotherR = another.result;
            CalendarDateDuration anotherD = another.duration;

            if (anotherR == null || anotherD == null) {
                fastest = FAILED;
                return;
            }

            all.add(another);

            if (fastest != null) {

                Result fastestR = fastest.result;
                CalendarDateDuration fastestD = fastest.duration;

                State stateF = fastestR.getState();
                State stateA = anotherR.getState();

                double valueF = fastestR.getValue();
                double valueA = anotherR.getValue();

                if (stateF != stateA) {
                    fastest = new TimedResult<>(anotherR.withState(Optimisation.State.INVALID), anotherD);
                } else if (myValueAccuracy.isDifferent(valueF, valueA)) {
                    fastest = new TimedResult<>(anotherR.withState(Optimisation.State.APPROXIMATE), anotherD);
                } else if (fastestD.measure > anotherD.measure) {
                    fastest = another;
                }

            } else {

                fastest = another;
            }
        }

        public boolean isStable() {

            int size = all.size();

            if (size < 3) {
                return false;
            }

            double duration1 = all.get(size - 1).duration.toDurationInMillis();
            double duration2 = all.get(size - 2).duration.toDurationInMillis();

            return ResultsSet.isSimilar(duration1, duration2, myHalfRelativeTimeError);
        }

    }

    static final TimedResult<Optimisation.Result> FAILED = new TimedResult<>(Optimisation.Result.of(0.0, Optimisation.State.FAILED),
            new CalendarDateDuration(30, CalendarDateUnit.MINUTE).convertTo(CalendarDateUnit.MILLIS));

    static final Map<String, ExpressionsBasedModel.Integration<?>> INTEGRATIONS = new HashMap<>();
    static final int WIDTH = 22;

    static {

        INTEGRATIONS.put(Contender.ACM, SolverACM.INTEGRATION);
        INTEGRATIONS.put(Contender.HIPPARCHUS, SolverHipparchus.INTEGRATION);
        // INTEGRATIONS.put(Contender.HIPPARCHUS, ADMMQPOptimizerImpl.INTEGRATION);
        INTEGRATIONS.put(Contender.CPLEX, SolverCPLEX.INTEGRATION);
        INTEGRATIONS.put(Contender.ORTOOLS, SolverORTools.INTEGRATION);
        // INTEGRATIONS.put("Gurobi", SolverGurobi.INTEGRATION);
        INTEGRATIONS.put(Contender.JOPTIMIZER, SolverJOptimizer.INTEGRATION);
        // INTEGRATIONS.put("Mosek", SolverMosek.INTEGRATION);

        INTEGRATIONS.put(Contender.OJALGO, LinearSolver.INTEGRATION);

        INTEGRATIONS.put(Contender.OJALGO_DUAL_DENSE, LinearSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.linear().dual();
            opt.sparse = Boolean.FALSE;
        }));
        INTEGRATIONS.put(Contender.OJALGO_DUAL_SPARSE, LinearSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.linear().dual();
            opt.sparse = Boolean.TRUE;
        }));
        INTEGRATIONS.put(Contender.OJALGO_PRIM_DENSE, LinearSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.linear().primal();
            opt.sparse = Boolean.FALSE;
        }));
        INTEGRATIONS.put(Contender.OJALGO_PRIM_SPARSE, LinearSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.linear().primal();
            opt.sparse = Boolean.TRUE;
        }));
    }

    protected static void doBenchmark(final Set<ModelSolverPair> allWork, final Configuration configuration) {

        Map<ModelSolverPair, ResultsSet> totResults = new TreeMap<>();
        Map<ModelSolverPair, FailReason> totReasons = new TreeMap<>();
        Map<String, ModelSize> modDim = new TreeMap<>();

        int iterations = 0;
        Set<ModelSolverPair> iterDone = new HashSet<>();

        do {

            iterations++;
            iterDone.clear();

            BasicLogger.debug();
            BasicLogger.debug("Iteration {} with {} model/solver pairs remaining {}", iterations, allWork.size(), Instant.now());
            BasicLogger.debug("-----------------------------------------------------------------------------");

            for (ModelSolverPair modelSolverPair : allWork) {

                ExpressionsBasedModel.clearIntegrations();
                Integration<?> integration = INTEGRATIONS.get(modelSolverPair.solver);
                if (integration != null) {
                    ExpressionsBasedModel.addIntegration(integration);
                }

                String path = configuration.path(modelSolverPair.model);

                BigDecimal expectedValue = configuration.values.get(modelSolverPair.model);

                try (InputStream input = AbstractBenchmark.class.getResourceAsStream(path)) {
                    ExpressionsBasedModel parsedMPS = ExpressionsBasedModel.parse(input, FileFormat.MPS);
                    modDim.computeIfAbsent(modelSolverPair.model, k -> new ModelSize(parsedMPS.countExpressions(), parsedMPS.countVariables()));
                    ExpressionsBasedModel simplified = parsedMPS.simplify();

                    ResultsSet mainResults = totResults.computeIfAbsent(modelSolverPair, k -> new ResultsSet(configuration.accuracy, 0.01));
                    ResultsSet subResults = new ResultsSet(configuration.accuracy, 0.1);

                    AtomicBoolean working = new AtomicBoolean(false);

                    Thread worker = new Thread(() -> {

                        while (!subResults.isStable()) {
                            subResults.add(AbstractBenchmark.meassure(simplified));
                        }
                        working.set(false);
                    });

                    working.set(true);
                    long start = System.currentTimeMillis();
                    worker.start();

                    while (working.get() && System.currentTimeMillis() - start <= configuration.maxWaitTime) {
                        Thread.sleep(1_000L);
                    }

                    worker.stop();
                    working.set(false);

                    if (subResults.fastest != null) {

                        // Have a result

                        mainResults.add(subResults.fastest);

                        if (!subResults.fastest.result.getState().isOptimal()) {

                            BasicLogger.debugColumns(WIDTH, modelSolverPair.model, modelSolverPair.solver, subResults.fastest.result.getState(),
                                    FailReason.UNSTABLE);
                            totReasons.put(modelSolverPair, FailReason.UNSTABLE);
                            iterDone.add(modelSolverPair);

                        } else if (expectedValue != null
                                && configuration.accuracy.isDifferent(expectedValue.doubleValue(), subResults.fastest.result.getValue())) {

                            BasicLogger.debugColumns(WIDTH, modelSolverPair.model, modelSolverPair.solver, FailReason.WRONG,
                                    subResults.fastest.result.getValue(), "!= " + expectedValue);
                            totReasons.put(modelSolverPair, FailReason.WRONG);
                            iterDone.add(modelSolverPair);

                        } else if (mainResults.isStable()) {

                            BasicLogger.debugColumns(WIDTH, modelSolverPair.model, modelSolverPair.solver, "Time stable", mainResults.fastest.duration,
                                    mainResults.fastest.result.getValue());
                            iterDone.add(modelSolverPair);
                        }

                    } else {

                        // No result, timeout

                        mainResults.add(FAILED);

                        BasicLogger.debugColumns(WIDTH, modelSolverPair.model, modelSolverPair.solver, FAILED.result.getState(), FailReason.TIMEOUT);
                        totReasons.put(modelSolverPair, FailReason.TIMEOUT);
                        iterDone.add(modelSolverPair);
                    }

                } catch (Throwable cause) {
                    BasicLogger.error("Error working with {}!", modelSolverPair);
                    throw new RuntimeException(cause);
                }
            }

            allWork.removeAll(iterDone);

        } while (allWork.size() > 0);

        try (TextLineWriter writer = TextLineWriter.of("./src/main/resources/benchmark_output.csv")) {

            CSVLineBuilder csv = writer.newCSVLineBuilder(ASCII.HT);

            csv.line("Model", "Solver", "Time", "nbVars", "nbExpr");

            BasicLogger.debug();
            BasicLogger.debug("Final Results");
            BasicLogger.debug("=====================================================================");
            for (Entry<ModelSolverPair, ResultsSet> entry : totResults.entrySet()) {

                ModelSolverPair work = entry.getKey();
                TimedResult<Result> result = entry.getValue().fastest;

                String model = work.model;
                String solver = work.solver;

                State state = result.result.getState();
                double value = result.result.getValue();
                CalendarDateDuration duration = result.duration;
                ModelSize dimensions = modDim.get(model);

                BigDecimal expectedValue = configuration.values.get(model);

                Result referenceResult = null;
                if (configuration.refeenceSolver != null) {
                    ModelSolverPair referenceModelSolverPair = new ModelSolverPair(model, configuration.refeenceSolver);
                    ResultsSet referenceResultsSet = totResults.get(referenceModelSolverPair);
                    referenceResult = referenceResultsSet != null ? referenceResultsSet.fastest.result : null;
                }

                if (expectedValue != null || referenceResult != null && referenceResult.getState().isOptimal()) {

                    double referenceValue = expectedValue != null ? expectedValue.doubleValue() : referenceResult.getValue();

                    if (state.isOptimal() && !configuration.accuracy.isDifferent(referenceValue, value)) {
                        BasicLogger.debugColumns(WIDTH, model, solver, state, duration);
                        csv.line(model, solver, duration.toDurationInNanos(), dimensions.nbVariables, dimensions.nbExpressions);
                    } else {
                        BasicLogger.debugColumns(WIDTH, model, solver, Optimisation.State.FAILED, totReasons.getOrDefault(work, FailReason.WRONG));
                        csv.line(model, solver, "", dimensions.nbVariables, dimensions.nbExpressions);
                    }

                } else if (state.isOptimal()) {
                    BasicLogger.debugColumns(WIDTH, model, solver, state, duration);
                    csv.line(model, solver, duration.toDurationInNanos(), dimensions.nbVariables, dimensions.nbExpressions);
                } else {
                    BasicLogger.debugColumns(WIDTH, model, solver, Optimisation.State.FAILED, totReasons.getOrDefault(work, FailReason.TIMEOUT));
                    csv.line(model, solver, "", dimensions.nbVariables, dimensions.nbExpressions);
                }
            }

        } catch (IOException cause) {
            throw new RuntimeException(cause);
        }

    }

    static TimedResult<Result> meassure(final ExpressionsBasedModel model) {
        return Stopwatch.meassure(() -> AbstractBenchmark.solve(model));
    }

    static Optimisation.Result solve(final ExpressionsBasedModel model) {

        Optimisation.Result result = null;

        boolean maximisation = model.getOptimisationSense() == Optimisation.Sense.MAX;

        if (maximisation) {
            result = model.maximise();
        } else {
            result = model.minimise();
        }

        return result;
    }

}
