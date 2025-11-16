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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.ojalgo.OjAlgoUtils;
import org.ojalgo.benchmark.ForkedTask.ReturnValue;
import org.ojalgo.concurrent.ExternalProcessExecutor;
import org.ojalgo.concurrent.Parallelism;
import org.ojalgo.concurrent.ParallelismSupplier;
import org.ojalgo.concurrent.ProcessingService;
import org.ojalgo.matrix.task.iterative.ConjugateGradientSolver;
import org.ojalgo.matrix.task.iterative.JacobiPreconditioner;
import org.ojalgo.matrix.task.iterative.MINRESSolver;
import org.ojalgo.matrix.task.iterative.Preconditioner;
import org.ojalgo.matrix.task.iterative.QMRSolver;
import org.ojalgo.matrix.task.iterative.SSORPreconditioner;
import org.ojalgo.netio.ASCII;
import org.ojalgo.netio.BasicLogger;
import org.ojalgo.netio.TextLineWriter;
import org.ojalgo.netio.TextLineWriter.CSVLineBuilder;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Optimisation;
import org.ojalgo.optimisation.Optimisation.Result;
import org.ojalgo.optimisation.Optimisation.State;
import org.ojalgo.optimisation.convex.ConvexSolver;
import org.ojalgo.optimisation.linear.LinearSolver;
import org.ojalgo.optimisation.solver.acm.SolverACM;
import org.ojalgo.optimisation.solver.clarabel4j.SolverClarabel4j;
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

        /**
         * ms
         */
        public long maxWaitTime = 1_000L * 60L * 5L;
        public ParallelismSupplier parallelism = Parallelism.CORES.halve().adjustDown();
        public String pathPrefix;
        public String pathSuffix = ".SIF";
        public String refeenceSolver = Contender.ORTOOLS;
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

        public static final String OJALGO_DENSE_EXPERIMENTAL = "ojAlgo-D-exp";
        public static final String OJALGO_SPARSE_EXPERIMENTAL = "ojAlgo-S-exp";
        public static final String OJALGO_DENSE_STABLE = "ojAlgo-D-stbl";
        public static final String OJALGO_SPARSE_STABLE = "ojAlgo-S-stbl";

        public static final String ORTOOLS = "ORTools";

        public static final String CLARABEL4J = "Clarabel4j";

        public static final String OJALGO_QP_CG_ID = "ojAlgo-CG-id";
        public static final String OJALGO_QP_CG_JACOBI = "ojAlgo-CG-jacobi";
        public static final String OJALGO_QP_CG_SSORP = "ojAlgo-CG-ssorp";
        public static final String OJALGO_QP_MINRES_ID = "ojAlgo-MINRES-id";
        public static final String OJALGO_QP_MINRES_JACOBI = "ojAlgo-MINRES-jacobi";
        public static final String OJALGO_QP_MINRES_SSORP = "ojAlgo-MINRES-ssorp";
        public static final String OJALGO_QP_QMR_ID = "ojAlgo-QMR-id";
        public static final String OJALGO_QP_QMR_JACOBI = "ojAlgo-QMR-jacobi";
        public static final String OJALGO_QP_QMR_SSORP = "ojAlgo-QMR-ssorp";

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
            if (!(obj instanceof ModelSolverPair other)) {
                return false;
            }
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
         * Unexpected error/exception
         */
        FAILED,
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
            return (Math.abs(value1 - value2) / (value1 + value2) < halfRelativeError);
        }

        public TimedResult<Optimisation.Result> fastest;

        private final List<TimedResult<Optimisation.Result>> all = new ArrayList<>();
        private final double myHalfRelativeTimeError;
        private final NumberContext myValueAccuracy;

        public ResultsSet() {
            this(ACCURACY, 0.1);
        }

        private ResultsSet(final NumberContext valueAccuracy, final double timeAccuracy) {
            super();
            myValueAccuracy = valueAccuracy;
            myHalfRelativeTimeError = timeAccuracy / 2D;
        }

        public TimedResult<Result> add(final ForkedTask.ReturnValue returnValue) {

            if (returnValue == null) {
                fastest = FAILED;
                return null;
            }

            if (returnValue.result == null || Double.isNaN(returnValue.time)) {
                fastest = FAILED;
                return null;
            }

            Optimisation.Result result = Optimisation.Result.parse(returnValue.result);

            CalendarDateDuration duration = new CalendarDateDuration(returnValue.time, CalendarDateUnit.MILLIS);

            TimedResult<Result> another = new TimedResult<>(result, duration);

            this.add(another);

            return another;
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

            if (size >= 20) {
                return true;
            }

            double duration1 = all.get(size - 1).duration.toDurationInMillis();
            double duration2 = all.get(size - 2).duration.toDurationInMillis();

            return ResultsSet.isSimilar(duration1, duration2, myHalfRelativeTimeError);
        }

    }

    static final NumberContext ACCURACY = NumberContext.of(4);

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

        INTEGRATIONS.put(Contender.CLARABEL4J, SolverClarabel4j.INTEGRATION);

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

        INTEGRATIONS.put(Contender.OJALGO_DENSE_EXPERIMENTAL, ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.FALSE;
            opt.experimental = true;
        }));
        INTEGRATIONS.put(Contender.OJALGO_SPARSE_EXPERIMENTAL, ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.TRUE;
            opt.experimental = true;
        }));
        INTEGRATIONS.put(Contender.OJALGO_DENSE_STABLE, ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.FALSE;
            opt.experimental = false;
        }));
        INTEGRATIONS.put(Contender.OJALGO_SPARSE_STABLE, ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.TRUE;
            opt.experimental = false;
        }));

        INTEGRATIONS.put(Contender.OJALGO_QP_CG_ID, ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.TRUE;
            opt.convex().iterative(ConjugateGradientSolver::new, Preconditioner::newIdentity);
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_CG_JACOBI, ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.TRUE;
            opt.convex().iterative(ConjugateGradientSolver::new, JacobiPreconditioner::new);
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_CG_SSORP, ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.TRUE;
            opt.convex().iterative(ConjugateGradientSolver::new, SSORPreconditioner::new);
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_MINRES_ID, ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.TRUE;
            opt.convex().iterative(MINRESSolver::new, Preconditioner::newIdentity);
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_MINRES_JACOBI, ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.TRUE;
            opt.convex().iterative(MINRESSolver::new, JacobiPreconditioner::new);
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_MINRES_SSORP, ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.TRUE;
            opt.convex().iterative(MINRESSolver::new, SSORPreconditioner::new);
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_QMR_ID, ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.TRUE;
            opt.convex().iterative(QMRSolver::new, Preconditioner::newIdentity);
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_QMR_JACOBI, ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.TRUE;
            opt.convex().iterative(QMRSolver::new, JacobiPreconditioner::new);
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_QMR_SSORP, ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.TRUE;
            opt.convex().iterative(QMRSolver::new, SSORPreconditioner::new);
        }));
    }

    protected static void doBenchmark(final Set<ModelSolverPair> allWork, final Configuration configuration) {

        ProcessingService masterProcessor = ProcessingService.newInstance("benchmark");
        ExternalProcessExecutor slaveExecutor = ExternalProcessExecutor.newInstance();

        Map<ModelSolverPair, ResultsSet> totResults = new ConcurrentHashMap<>();
        Map<ModelSolverPair, FailReason> totReasons = new ConcurrentHashMap<>();
        Map<String, ModelSize> modDim = new ConcurrentHashMap<>();

        int iterations = 0;
        Set<ModelSolverPair> iterDone = ConcurrentHashMap.newKeySet();

        BasicLogger.debug();
        BasicLogger.debug("Environment: {}", OjAlgoUtils.ENVIRONMENT);
        BasicLogger.debug();

        do {

            iterations++;
            iterDone.clear();

            BasicLogger.debug();
            BasicLogger.debug("Iteration {} with {} model/solver pairs remaining {}", iterations, allWork.size(), Instant.now());
            BasicLogger.debug("-----------------------------------------------------------------------------");

            masterProcessor.process(allWork, configuration.parallelism,
                    modelSolverPair -> AbstractBenchmark.doOnePair(configuration, slaveExecutor, totResults, totReasons, modDim, iterDone, modelSolverPair));

            allWork.removeAll(iterDone);

        } while (allWork.size() > 0);

        Map<ModelSolverPair, ResultsSet> sortedResults = new TreeMap<>(totResults);

        try (TextLineWriter writer = TextLineWriter.of("./src/main/resources/benchmark_output.csv")) {

            CSVLineBuilder csv = writer.newCSVLineBuilder(ASCII.HT);

            csv.line("Model", "Solver", "Time", "nbVars", "nbExpr");

            BasicLogger.debug();
            BasicLogger.debug("Final Results");
            BasicLogger.debug("=====================================================================");
            for (Entry<ModelSolverPair, ResultsSet> entry : sortedResults.entrySet()) {

                ModelSolverPair work = entry.getKey();
                TimedResult<Result> result = entry.getValue().fastest;

                String model = work.model;
                String solver = work.solver;

                State state = result.result.getState();
                double value = result.result.getValue();
                CalendarDateDuration duration = result.duration;
                ModelSize dimensions = modDim.get(model);
                int nbVars = dimensions != null ? dimensions.nbVariables : 0;
                int nbExpr = dimensions != null ? dimensions.nbExpressions : 0;

                BigDecimal expectedValue = configuration.values.get(model);

                Result referenceResult = null;
                if (configuration.refeenceSolver != null) {
                    ModelSolverPair referenceModelSolverPair = new ModelSolverPair(model, configuration.refeenceSolver);
                    ResultsSet referenceResultsSet = sortedResults.get(referenceModelSolverPair);
                    referenceResult = referenceResultsSet != null ? referenceResultsSet.fastest.result : null;
                }

                if (expectedValue != null || referenceResult != null && referenceResult.getState().isOptimal()) {

                    double referenceValue = expectedValue != null ? expectedValue.doubleValue() : referenceResult.getValue();

                    if (state.isOptimal() && !ACCURACY.isDifferent(referenceValue, value)) {
                        BasicLogger.debugColumns(WIDTH, model, solver, state, duration);
                        csv.line(model, solver, duration.toDurationInNanos(), nbVars, nbExpr);
                    } else {
                        BasicLogger.debugColumns(WIDTH, model, solver, Optimisation.State.FAILED, totReasons.getOrDefault(work, FailReason.WRONG));
                        csv.line(model, solver, "", nbVars, nbExpr);
                    }

                } else if (state.isOptimal()) {
                    BasicLogger.debugColumns(WIDTH, model, solver, state, duration);
                    csv.line(model, solver, duration.toDurationInNanos(), nbVars, nbExpr);
                } else {
                    BasicLogger.debugColumns(WIDTH, model, solver, Optimisation.State.FAILED, totReasons.getOrDefault(work, FailReason.TIMEOUT));
                    csv.line(model, solver, "", nbVars, nbExpr);
                }
            }

        } catch (IOException cause) {
            throw new RuntimeException(cause);
        }

    }

    static void doOnePair(final Configuration configuration, final ExternalProcessExecutor executor, final Map<ModelSolverPair, ResultsSet> totResults,
            final Map<ModelSolverPair, FailReason> totReasons, final Map<String, ModelSize> modDim, final Set<ModelSolverPair> iterDone,
            final ModelSolverPair modelSolverPair) {

        String path = configuration.path(modelSolverPair.model);

        BigDecimal expectedValue = configuration.values.get(modelSolverPair.model);

        Future<ForkedTask.ReturnValue> future = null;
        try {

            future = executor.execute(ForkedTask.DESCRIPTOR, path, modelSolverPair.solver, configuration.maxWaitTime);

            ReturnValue subResults = future.get(configuration.maxWaitTime, TimeUnit.MILLISECONDS);

            modDim.computeIfAbsent(modelSolverPair.model, k -> new ModelSize(subResults.nbExpressions, subResults.nbVariables));

            ResultsSet mainResults = totResults.computeIfAbsent(modelSolverPair, k -> new ResultsSet());

            if (subResults.result != null) {

                // Have a result

                TimedResult<Result> fastest = mainResults.add(subResults);

                if (!fastest.result.getState().isOptimal()) {

                    BasicLogger.debugColumns(WIDTH, modelSolverPair.model, modelSolverPair.solver, fastest.result.getState(), FailReason.UNSTABLE);
                    totReasons.put(modelSolverPair, FailReason.UNSTABLE);
                    iterDone.add(modelSolverPair);

                } else if (expectedValue != null && ACCURACY.isDifferent(expectedValue.doubleValue(), fastest.result.getValue())) {

                    BasicLogger.debugColumns(WIDTH, modelSolverPair.model, modelSolverPair.solver, FailReason.WRONG, fastest.result.getValue(),
                            "!= " + expectedValue);
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

        } catch (TimeoutException timeout) {

            if (future != null) {
                try {
                    future.cancel(true);
                } catch (Exception ignore) {
                    // ignore
                }
            }

            ResultsSet mainResults = totResults.computeIfAbsent(modelSolverPair, k -> new ResultsSet());
            mainResults.add(FAILED);

            BasicLogger.debugColumns(WIDTH, modelSolverPair.model, modelSolverPair.solver, FAILED.result.getState(), FailReason.TIMEOUT);
            totReasons.put(modelSolverPair, FailReason.TIMEOUT);
            iterDone.add(modelSolverPair);

        } catch (Exception cause) {

            if (future != null) {
                try {
                    future.cancel(true);
                } catch (Exception ignore) {
                    // ignore
                }
            }

            BasicLogger.error("Error working with {}!", modelSolverPair);

            ResultsSet mainResults = totResults.computeIfAbsent(modelSolverPair, k -> new ResultsSet());
            mainResults.add(FAILED);

            BasicLogger.debugColumns(WIDTH, modelSolverPair.model, modelSolverPair.solver, FAILED.result.getState(), FailReason.FAILED);
            totReasons.put(modelSolverPair, FailReason.FAILED);
            iterDone.add(modelSolverPair);
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
