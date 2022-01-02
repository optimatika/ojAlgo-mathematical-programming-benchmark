/*
 * Copyright 1997-2022 Optimatika
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

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.PrintWriter;
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

import org.ojalgo.commons.math3.optim.linear.SolverCommonsMath;
import org.ojalgo.joptimizer.SolverJOptimizer;
import org.ojalgo.netio.BasicLogger;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.ExpressionsBasedModel.FileFormat;
import org.ojalgo.optimisation.ExpressionsBasedModel.Integration;
import org.ojalgo.optimisation.Optimisation;
import org.ojalgo.optimisation.Optimisation.Result;
import org.ojalgo.optimisation.Optimisation.State;
import org.ojalgo.optimisation.solver.cplex.SolverCPLEX;
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
        public static final String J_OPTIMIZER = "JOptimizer";
        public static final String OJALGO = "ojAlgo";

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
            result = prime * result + (solver == null ? 0 : solver.hashCode());
            return result;
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
        INTEGRATIONS.put(Contender.ACM, SolverCommonsMath.INTEGRATION);
        INTEGRATIONS.put(Contender.CPLEX, SolverCPLEX.INTEGRATION);
        // INTEGRATIONS.put("Gurobi", SolverGurobi.INTEGRATION);
        INTEGRATIONS.put(Contender.J_OPTIMIZER, SolverJOptimizer.INTEGRATION);
        // INTEGRATIONS.put("Mosek", SolverMosek.INTEGRATION);
    }

    protected static void doBenchmark(final Set<ModelSolverPair> WORK, final Configuration configuration) {

        Set<ModelSolverPair> done = new HashSet<>();
        Map<ModelSolverPair, ResultsSet> results = new TreeMap<>();
        Map<ModelSolverPair, FailReason> reasons = new TreeMap<>();

        int iterations = 0;

        do {
            iterations++;
            done.clear();

            BasicLogger.debug();
            BasicLogger.debug("Iteration {} with {} model/solver pairs remaining {}", iterations, WORK.size(), Instant.now());
            BasicLogger.debug("--------------------------------------------------------------------------");

            for (ModelSolverPair work : WORK) {
                ExpressionsBasedModel.clearIntegrations();

                Integration<?> integration = INTEGRATIONS.get(work.solver);
                if (integration != null) {
                    ExpressionsBasedModel.addPreferredSolver(integration);
                }

                String path = configuration.path(work.model);

                BigDecimal expectedValue = configuration.values.get(work.model);

                try (InputStream input = AbstractBenchmark.class.getResourceAsStream(path)) {
                    ExpressionsBasedModel parsedMPS = ExpressionsBasedModel.parse(input, FileFormat.MPS);

                    ResultsSet resultsSet = results.computeIfAbsent(work, k -> new ResultsSet(configuration.accuracy, 0.01));

                    TimedResult<Optimisation.Result>[] resultA = (TimedResult<Result>[]) new TimedResult<?>[1];

                    Thread worker = new Thread(() -> {
                        ResultsSet subresults = new ResultsSet(configuration.accuracy, 0.1);
                        while (!subresults.isStable()) {
                            subresults.add(AbstractBenchmark.meassure(parsedMPS));
                        }
                        resultA[0] = subresults.fastest;
                    });

                    resultA[0] = null;
                    long start = System.currentTimeMillis();
                    worker.start();

                    while (resultA[0] == null && System.currentTimeMillis() - start <= configuration.maxWaitTime) {
                        Thread.sleep(1_000L);
                    }

                    worker.stop();

                    TimedResult<Optimisation.Result> result = resultA[0];

                    if (result != null) {

                        // Have a result

                        resultsSet.add(result);

                        if (!result.result.getState().isOptimal()) {

                            BasicLogger.debug(WIDTH, work.model, work.solver, result.result.getState(), FailReason.UNSTABLE);
                            reasons.put(work, FailReason.UNSTABLE);
                            done.add(work);

                        } else if (expectedValue != null && configuration.accuracy.isDifferent(expectedValue.doubleValue(), result.result.getValue())) {

                            BasicLogger.debug(WIDTH, work.model, work.solver, FailReason.WRONG, result.result.getValue(), "!= " + expectedValue);
                            reasons.put(work, FailReason.WRONG);
                            done.add(work);

                        } else if (resultsSet.isStable()) {

                            BasicLogger.debug(WIDTH, work.model, work.solver, "Time stable");
                            done.add(work);
                        }

                    } else {

                        // No result, timeout

                        resultsSet.add(FAILED);

                        BasicLogger.debug(WIDTH, work.model, work.solver, FAILED.result.getState(), FailReason.TIMEOUT);
                        reasons.put(work, FailReason.TIMEOUT);
                        done.add(work);
                    }

                } catch (Throwable cause) {
                    BasicLogger.error("Error working with {}!", work);
                    throw new RuntimeException(cause);
                }
            }

            WORK.removeAll(done);

        } while (WORK.size() > 0);

        try (PrintWriter writer = new PrintWriter("./src/main/resources/benchmark_output.csv")) {

            writer.println("Model" + "\t" + "Solver" + "\t" + "Time");

            BasicLogger.debug();
            BasicLogger.debug("Final Results");
            BasicLogger.debug("=====================================================================");
            for (Entry<ModelSolverPair, ResultsSet> entry : results.entrySet()) {

                ModelSolverPair work = entry.getKey();
                TimedResult<Result> result = entry.getValue().fastest;

                String model = work.model;
                String solver = work.solver;

                State state = result.result.getState();
                double value = result.result.getValue();
                CalendarDateDuration duration = result.duration;

                BigDecimal expectedValue = configuration.values.get(model);

                Result referenceResult = results.get(new ModelSolverPair(model, configuration.refeenceSolver)).fastest.result;

                if (expectedValue != null || referenceResult.getState().isOptimal()) {

                    double referenceValue = expectedValue != null ? expectedValue.doubleValue() : referenceResult.getValue();

                    if (state.isOptimal() && !configuration.accuracy.isDifferent(referenceValue, value)) {
                        BasicLogger.debug(WIDTH, model, solver, state, duration);
                        writer.println(model + "\t" + solver + "\t" + duration.toDurationInNanos());
                    } else {
                        BasicLogger.debug(WIDTH, model, solver, Optimisation.State.FAILED, reasons.getOrDefault(work, FailReason.WRONG));
                        writer.println(model + "\t" + solver + "\t");
                    }

                } else if (state.isOptimal()) {
                    BasicLogger.debug(WIDTH, model, solver, state, duration);
                    writer.println(model + "\t" + solver + "\t" + duration.toDurationInNanos());
                } else {
                    BasicLogger.debug(WIDTH, model, solver, Optimisation.State.FAILED, reasons.getOrDefault(work, FailReason.TIMEOUT));
                    writer.println(model + "\t" + solver + "\t");
                }
            }

        } catch (FileNotFoundException cause) {
            throw new RuntimeException(cause);
        }

    }

    static TimedResult<Result> meassure(final ExpressionsBasedModel model) {
        return Stopwatch.meassure(() -> AbstractBenchmark.solve(model));
    }

    static Optimisation.Result solve(final ExpressionsBasedModel model) {

        Optimisation.Result result = null;

        if (model.isMinimisation()) {
            result = model.minimise();
        } else {
            result = model.maximise();
        }

        return result;
    }

}
