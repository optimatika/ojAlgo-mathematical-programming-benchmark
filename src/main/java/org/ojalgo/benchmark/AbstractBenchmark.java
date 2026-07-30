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
import java.util.function.Supplier;

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
import org.ojalgo.optimisation.convex.ConvexSolver.Algorithm;
import org.ojalgo.optimisation.integer.IntegerSolver;
import org.ojalgo.optimisation.linear.LinearSolver;
import org.ojalgo.optimisation.solver.acm.SolverACM;
import org.ojalgo.optimisation.solver.clarabel.SolverClarabel;
import org.ojalgo.optimisation.solver.cplex.SolverCPLEX;
import org.ojalgo.optimisation.solver.highs.SolverHiGHS;
import org.ojalgo.optimisation.solver.hipparchus.SolverHipparchus;
import org.ojalgo.optimisation.solver.joptimizer.SolverJOptimizer;
import org.ojalgo.optimisation.solver.ortools.SolverORTools;
import org.ojalgo.optimisation.solver.scip.SolverSCIP;
import org.ojalgo.optimisation.solver.ssclp.SolverSSCLP;
import org.ojalgo.type.CalendarDateDuration;
import org.ojalgo.type.CalendarDateUnit;
import org.ojalgo.type.Stopwatch;
import org.ojalgo.type.Stopwatch.TimedResult;
import org.ojalgo.type.context.NumberContext;

public abstract class AbstractBenchmark {

    public static final class Configuration {

        public Set<String> investigate = Set.of();
        /**
         * Absolute paths to the native libraries to use, keyed by contender name. Anything not listed here is
         * resolved the usual way - whatever the integration's own loader finds installed.
         * <p>
         * The library is loaded in the worker JVM before the integration initialises, and both the HiGHS and
         * SCIP loaders check for an already-loaded library before searching, so that is the one they bind to.
         * <p>
         * One build per solver per run. Worker JVMs are reused, and a library, once loaded, stays loaded - so
         * two builds of the same solver cannot be told apart within a single run. Compare builds by running
         * again with a different path and a different {@link #outputPath}.
         * <p>
         * The libraries must be built for the machine the benchmark runs on - a Linux {@code .so} out of a
         * Docker image will not load on macOS.
         */
        public final Map<String, String> libraries = new HashMap<>();
        /**
         * How many times, at most, to measure each model/solver pair.
         * <p>
         * Set to 1 and each pair is solved exactly once - enough to answer whether the solver manages the
         * model at all, which for MIP is the interesting question. Above 1 the forked task repeats within its
         * own budget on every pass, and a pair is done once two consecutive measurements agree.
         * <p>
         * At 1 the reported times are cold - one solve in a fresh JVM, including class loading, JIT warm-up
         * and native library loading. On models that solve in milliseconds that overhead dominates, so read
         * those times as "it worked", not as measurements, and don't compare them against a longer run.
         */
        public int maxIterations = DEFAULT_MAX_ITERATIONS;
        public int maxProbSize = 10_000;
        /**
         * ms
         */
        public long maxWaitTime = 1_000L * 60L * 5L;
        public int minProbSize = 1;
        /**
         * Where the results CSV is written. Give each configuration its own file when running the same models
         * several times over.
         */
        public String outputPath = "./src/main/resources/benchmark_output.csv";
        public ParallelismSupplier parallelism = Parallelism.CORES.halve().adjustDown();
        public String pathPrefix;
        public String pathSuffix = ".SIF";
        public String refeenceSolver = Contender.ORTOOLS;
        public final String[] solvers;
        public final Map<String, BigDecimal> values = new HashMap<>();

        public Configuration(final String... solvers) {
            super();
            this.solvers = solvers;
        }

        public String path(final String modelName) {
            return pathPrefix + modelName + pathSuffix;
        }

    }

    public static final class Contender {

        public static final String ACM = "ACM";
        public static final String CLARABEL = "Clarabel";
        public static final String CPLEX = "CPLEX";
        public static final String HIGHS = "HiGHS";
        public static final String HIPPARCHUS = "Hipparchus";
        public static final String JOPTIMIZER = "JOptimizer";
        public static final String OJALGO_LP = "ojAlgo-LP";
        public static final String OJALGO_LP_DUAL_DENSE = "ojAlgo-LP-dual-D";
        public static final String OJALGO_LP_DUAL_SPARSE = "ojAlgo-LP-dual-S";
        public static final String OJALGO_LP_PRIM_DENSE = "ojAlgo-LP-prim-D";
        public static final String OJALGO_LP_PRIM_SPARSE = "ojAlgo-LP-prim-S";
        public static final String OJALGO_MIP = "ojAlgo-MIP";
        public static final String OJALGO_QP = "ojAlgo-QP";
        public static final String OJALGO_QP_ADMM = "ojAlgo-QP-ADMM";
        public static final String OJALGO_QP_ASET = "ojAlgo-QP-ASET";
        public static final String OJALGO_QP_CG_ID = "ojAlgo-QP-CG-id";
        public static final String OJALGO_QP_CG_JACOBI = "ojAlgo-QP-CG-jacobi";
        public static final String OJALGO_QP_CG_SSORP = "ojAlgo-QP-CG-ssorp";
        public static final String OJALGO_QP_DENSE_EXPERIMENTAL = "ojAlgo-QP-D-exp";
        public static final String OJALGO_QP_DENSE_STABLE = "ojAlgo-QP-D-stbl";
        public static final String OJALGO_QP_MINRES_ID = "ojAlgo-QP-MINRES-id";
        public static final String OJALGO_QP_MINRES_JACOBI = "ojAlgo-QP-MINRES-jacobi";
        public static final String OJALGO_QP_MINRES_SSORP = "ojAlgo-QP-MINRES-ssorp";
        public static final String OJALGO_QP_NULLSPACE_DENSE = "ojAlgo-QP-NSP-D";
        public static final String OJALGO_QP_NULLSPACE_SPARSE = "ojAlgo-QP-NSP-S";
        public static final String OJALGO_QP_PLAIN_DENSE = "ojAlgo-QP-PLAIN-D";
        public static final String OJALGO_QP_PLAIN_SPARSE = "ojAlgo-QP-PLAIN-S";
        public static final String OJALGO_QP_QMR_ID = "ojAlgo-QP-QMR-id";
        public static final String OJALGO_QP_QMR_JACOBI = "ojAlgo-QP-QMR-jacobi";
        public static final String OJALGO_QP_QMR_SSORP = "ojAlgo-QP-QMR-ssorp";
        public static final String OJALGO_QP_SPARSE_EXPERIMENTAL = "ojAlgo-QP-S-exp";
        public static final String OJALGO_QP_SPARSE_STABLE = "ojAlgo-QP-S-stbl";
        public static final String ORTOOLS = "OR-Tools";
        public static final String SCIP = "SCIP";
        public static final String SSCLP = "SSC-LP";
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
        WRONG,
        /**
         * Reported a solution that does not satisfy the model's constraints. Distinct from WRONG: the
         * objective value can still agree with the reference while the solution itself is infeasible.
         */
        INVALID;
    }

    static final class ModelSize {

        public final double density;
        public final int nbExpressions;
        public final int nbVariables;

        ModelSize(final int nbExpressions, final int nbVariables, final double density) {
            super();
            this.nbExpressions = nbExpressions;
            this.nbVariables = nbVariables;
            this.density = density;
        }

    }

    static final class ResultsSet {

        static boolean isSimilar(final double value1, final double value2, final double halfRelativeError) {
            return (Math.abs(value1 - value2) / (value1 + value2) < halfRelativeError);
        }

        public TimedResult<Optimisation.Result> fastest;

        private final List<TimedResult<Optimisation.Result>> all = new ArrayList<>();
        private final double myHalfRelativeTimeError;
        private final int myMaxCount;
        private final NumberContext myValueAccuracy;

        public ResultsSet() {
            this(DEFAULT_MAX_ITERATIONS);
        }

        public ResultsSet(final int maxCount) {
            this(ACCURACY, 0.1, maxCount);
        }

        private ResultsSet(final NumberContext valueAccuracy, final double timeAccuracy, final int maxCount) {
            super();
            myValueAccuracy = valueAccuracy;
            myHalfRelativeTimeError = timeAccuracy / 2D;
            myMaxCount = maxCount;
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

        public int count() {
            return all.size();
        }

        /**
         * True once no further measurements are wanted - either because the count is spent, or because the
         * two most recent times agree closely enough that another one wouldn't tell us much. The count is
         * checked first, so a max of 1 means "one measurement is all we're after".
         */
        public boolean isStable() {

            int size = all.size();

            if (size >= myMaxCount) {
                return true;
            }

            if (size < 3) {
                return false;
            }

            double duration1 = all.get(size - 1).duration.toDurationInMillis();
            double duration2 = all.get(size - 2).duration.toDurationInMillis();

            return ResultsSet.isSimilar(duration1, duration2, myHalfRelativeTimeError);
        }

    }

    /**
     * The one bar everything is measured against. This is a speed benchmark, so it asks "not completely
     * wrong" rather than asserting accuracy, and the same tolerance suits all three questions:
     * <ul>
     * <li>do repeated solves of a pair agree well enough to trust the timing,
     * <li>does the value match the expected one (or the reference solver),
     * <li>does the returned solution actually satisfy the model's constraints.
     * </ul>
     * Measured on this model set the margins are wide in every direction. Legitimate solves disagree by
     * ~1E-4 at worst while genuinely wrong values are off by tens of percent. Residuals differ by two or
     * three orders of magnitude between the dense and sparse stores (1E-10 against 1E-8 on one model, 1E-8
     * against 1E-5 on another) without either being wrong, while a genuinely infeasible point was off by
     * 1E+4. Note that feasibility is judged per constraint, with the precision applied relative to each
     * limit, so the value has to suit small limits too.
     */
    static final NumberContext ACCURACY = NumberContext.of(4);

    static final int DEFAULT_MAX_ITERATIONS = 20;

    static final TimedResult<Optimisation.Result> FAILED = new TimedResult<>(Optimisation.Result.of(0.0, Optimisation.State.FAILED),
            new CalendarDateDuration(30, CalendarDateUnit.MINUTE).convertTo(CalendarDateUnit.MILLIS));

    /**
     * Suppliers rather than instances so that a solver's classes - and therefore its native libraries - are
     * only loaded when that solver is actually used. OR-Tools in particular bundles its own libhighs, which
     * the HiGHS integration would then bind to instead of the system one.
     */
    static final Map<String, Supplier<ExpressionsBasedModel.Integration<?>>> INTEGRATIONS = new HashMap<>();
    static final int WIDTH = 22;

    static {

        INTEGRATIONS.put(Contender.ACM, () -> SolverACM.INTEGRATION);
        INTEGRATIONS.put(Contender.HIPPARCHUS, () -> SolverHipparchus.INTEGRATION);
        INTEGRATIONS.put(Contender.CPLEX, () -> SolverCPLEX.INTEGRATION);
        INTEGRATIONS.put(Contender.ORTOOLS, () -> SolverORTools.INTEGRATION);
        INTEGRATIONS.put(Contender.OJALGO_QP_ADMM, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.experimental = true;
        }));
        // INTEGRATIONS.put("Gurobi", SolverGurobi.INTEGRATION);
        INTEGRATIONS.put(Contender.JOPTIMIZER, () -> SolverJOptimizer.INTEGRATION);
        // INTEGRATIONS.put("Mosek", SolverMosek.INTEGRATION);

        INTEGRATIONS.put(Contender.OJALGO_LP, () -> LinearSolver.INTEGRATION);
        INTEGRATIONS.put(Contender.OJALGO_MIP, () -> IntegerSolver.INTEGRATION);

        INTEGRATIONS.put(Contender.CLARABEL, () -> SolverClarabel.INTEGRATION);
        INTEGRATIONS.put(Contender.HIGHS, () -> SolverHiGHS.INTEGRATION);

        INTEGRATIONS.put(Contender.SCIP, () -> SolverSCIP.INTEGRATION);
        INTEGRATIONS.put(Contender.SSCLP, () -> SolverSSCLP.INTEGRATION);

        INTEGRATIONS.put(Contender.OJALGO_LP_DUAL_DENSE, () -> LinearSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.linear().dual();
            opt.sparse = Boolean.FALSE;
        }));
        INTEGRATIONS.put(Contender.OJALGO_LP_DUAL_SPARSE, () -> LinearSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.linear().dual();
            opt.sparse = Boolean.TRUE;
        }));
        INTEGRATIONS.put(Contender.OJALGO_LP_PRIM_DENSE, () -> LinearSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.linear().primal();
            opt.sparse = Boolean.FALSE;
        }));
        INTEGRATIONS.put(Contender.OJALGO_LP_PRIM_SPARSE, () -> LinearSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.linear().primal();
            opt.sparse = Boolean.TRUE;
        }));

        INTEGRATIONS.put(Contender.OJALGO_QP_DENSE_EXPERIMENTAL, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.FALSE;
            opt.experimental = true;
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_SPARSE_EXPERIMENTAL, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.TRUE;
            opt.experimental = true;
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_DENSE_STABLE, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.FALSE;
            opt.experimental = false;
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_SPARSE_STABLE, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.TRUE;
            opt.experimental = false;
        }));

        INTEGRATIONS.put(Contender.OJALGO_QP_CG_ID, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.TRUE;
            opt.convex().iterative(ConjugateGradientSolver::new, Preconditioner::newIdentity);
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_CG_JACOBI, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.TRUE;
            opt.convex().iterative(ConjugateGradientSolver::new, JacobiPreconditioner::new);
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_CG_SSORP, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.TRUE;
            opt.convex().iterative(ConjugateGradientSolver::new, SSORPreconditioner::new);
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_MINRES_ID, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.TRUE;
            opt.convex().iterative(MINRESSolver::new, Preconditioner::newIdentity);
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_MINRES_JACOBI, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.TRUE;
            opt.convex().iterative(MINRESSolver::new, JacobiPreconditioner::new);
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_MINRES_SSORP, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.TRUE;
            opt.convex().iterative(MINRESSolver::new, SSORPreconditioner::new);
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_QMR_ID, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.TRUE;
            opt.convex().iterative(QMRSolver::new, Preconditioner::newIdentity);
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_QMR_JACOBI, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.TRUE;
            opt.convex().iterative(QMRSolver::new, JacobiPreconditioner::new);
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_QMR_SSORP, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.TRUE;
            opt.convex().iterative(QMRSolver::new, SSORPreconditioner::new);
        }));

        INTEGRATIONS.put(Contender.CLARABEL, () -> SolverClarabel.INTEGRATION);
        INTEGRATIONS.put(Contender.OJALGO_QP, () -> ConvexSolver.INTEGRATION);

        INTEGRATIONS.put(Contender.OJALGO_QP_ADMM, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.convex().algorithm(Algorithm.ADMM);
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_ASET, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.convex().algorithm(Algorithm.ACTIVE_SET);
        }));

        INTEGRATIONS.put(Contender.OJALGO_QP_NULLSPACE_DENSE, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.convex().algorithm(Algorithm.ACTIVE_SET);
            opt.convex().projection(Boolean.TRUE);
            opt.sparse = Boolean.FALSE;
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_NULLSPACE_SPARSE, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.convex().algorithm(Algorithm.ACTIVE_SET);
            opt.convex().projection(Boolean.TRUE);
            opt.sparse = Boolean.TRUE;
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_PLAIN_DENSE, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.convex().algorithm(Algorithm.ACTIVE_SET);
            opt.convex().projection(Boolean.FALSE);
            opt.sparse = Boolean.FALSE;
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_PLAIN_SPARSE, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.convex().algorithm(Algorithm.ACTIVE_SET);
            opt.convex().projection(Boolean.FALSE);
            opt.sparse = Boolean.TRUE;
        }));

    }

    protected static void doBenchmark(final Set<ModelSolverPair> allWork, final Configuration configuration) {

        ProcessingService masterProcessor = ProcessingService.newInstance("benchmark");
        ExternalProcessExecutor slaveExecutor = ExternalProcessExecutor.newInstance();

        Map<ModelSolverPair, ResultsSet> totResults = new ConcurrentHashMap<>();
        Map<ModelSolverPair, FailReason> totReasons = new ConcurrentHashMap<>();
        Map<String, ModelSize> modDim = new ConcurrentHashMap<>();

        int workers = configuration.parallelism.getAsInt();
        int threadsPerWorker = Parallelism.THREADS.divideBy(workers).getAsInt();

        int iterations = 0;
        Set<ModelSolverPair> iterDone = ConcurrentHashMap.newKeySet();

        BasicLogger.debug();
        BasicLogger.debug("Environment: {}", OjAlgoUtils.ENVIRONMENT);
        BasicLogger.debug("Workers: {}, threads per worker: {}", workers, threadsPerWorker);
        BasicLogger.debug();

        do {

            iterations++;
            iterDone.clear();

            BasicLogger.debug();
            BasicLogger.debug("Iteration {} with {} model/solver pairs remaining {}", iterations, allWork.size(), Instant.now());
            BasicLogger.debug("-----------------------------------------------------------------------------");

            masterProcessor.process(allWork, configuration.parallelism, modelSolverPair -> AbstractBenchmark.doOnePair(configuration, slaveExecutor, totResults,
                    totReasons, modDim, iterDone, threadsPerWorker, modelSolverPair));

            allWork.removeAll(iterDone);

        } while (allWork.size() > 0);

        Map<ModelSolverPair, ResultsSet> sortedResults = new TreeMap<>(totResults);

        try (TextLineWriter writer = TextLineWriter.of(configuration.outputPath)) {

            CSVLineBuilder csv = writer.newCSVLineBuilder(ASCII.HT);

            csv.line("Model", "Solver", "Time", "nbVars", "nbExpr", "density");

            Map<String, int[]> tally = new TreeMap<>();

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
                double density = dimensions != null ? dimensions.density : Double.NaN;

                BigDecimal expectedValue = configuration.values.get(model);

                Result referenceResult = null;
                if (configuration.refeenceSolver != null) {
                    ModelSolverPair referenceModelSolverPair = new ModelSolverPair(model, configuration.refeenceSolver);
                    ResultsSet referenceResultsSet = sortedResults.get(referenceModelSolverPair);
                    referenceResult = referenceResultsSet != null ? referenceResultsSet.fastest.result : null;
                }

                boolean solved;
                FailReason reason;
                if (expectedValue != null || referenceResult != null && referenceResult.getState().isOptimal()) {
                    double referenceValue = expectedValue != null ? expectedValue.doubleValue() : referenceResult.getValue();
                    solved = state.isOptimal() && !ACCURACY.isDifferent(referenceValue, value);
                    reason = totReasons.getOrDefault(work, FailReason.WRONG);
                } else {
                    solved = state.isOptimal();
                    reason = totReasons.getOrDefault(work, FailReason.TIMEOUT);
                }

                // A point that does not satisfy the constraints is not a solve, whatever its objective value
                // says. Both tests above look only at the value, so this has to be applied separately.
                solved &= totReasons.get(work) != FailReason.INVALID;

                int[] counts = tally.computeIfAbsent(solver, k -> new int[2]);
                counts[1]++;

                if (solved) {
                    counts[0]++;
                    BasicLogger.debugColumns(WIDTH, model, solver, state, duration);
                    csv.line(model, solver, duration.toDurationInNanos(), nbVars, nbExpr, density);
                } else {
                    BasicLogger.debugColumns(WIDTH, model, solver, Optimisation.State.FAILED, reason);
                    csv.line(model, solver, "", nbVars, nbExpr, density);
                }
            }

            BasicLogger.debug();
            BasicLogger.debug("Models Solved");
            BasicLogger.debug("=====================================================================");
            for (Entry<String, int[]> entry : tally.entrySet()) {
                int[] counts = entry.getValue();
                BasicLogger.debugColumns(WIDTH, entry.getKey(), counts[0] + " / " + counts[1], Math.round(100.0 * counts[0] / counts[1]) + "%");
            }

        } catch (IOException cause) {
            throw new RuntimeException(cause);
        }

    }

    static void doOnePair(final Configuration configuration, final ExternalProcessExecutor executor, final Map<ModelSolverPair, ResultsSet> totResults,
            final Map<ModelSolverPair, FailReason> totReasons, final Map<String, ModelSize> modDim, final Set<ModelSolverPair> iterDone,
            final int threadsPerWorker, final ModelSolverPair modelSolverPair) {

        String path = configuration.path(modelSolverPair.model);

        BigDecimal expectedValue = configuration.values.get(modelSolverPair.model);

        // One solve is all a capability test needs. When stabilising, though, the fork should use the
        // whole budget every time - it has already paid for the JVM, the class loading and the parsing,
        // and each repeat feeds the same measurement.
        int maxSolves = configuration.maxIterations <= 1 ? 1 : 0;

        // Capability is decided on the first pass; later passes only refine the timing.
        boolean firstPass = !totResults.containsKey(modelSolverPair);

        Future<ForkedTask.ReturnValue> future = null;
        try {

            future = executor.execute(ForkedTask.DESCRIPTOR, path, modelSolverPair.solver, configuration.maxWaitTime, threadsPerWorker, maxSolves,
                    configuration.libraries.getOrDefault(modelSolverPair.solver, ""), firstPass);

            ReturnValue subResults = future.get(configuration.maxWaitTime, TimeUnit.MILLISECONDS);

            modDim.computeIfAbsent(modelSolverPair.model, k -> new ModelSize(subResults.nbExpressions, subResults.nbVariables, subResults.density));

            ResultsSet mainResults = totResults.computeIfAbsent(modelSolverPair, k -> new ResultsSet(configuration.maxIterations));

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

                } else if (!subResults.valid) {

                    BasicLogger.debugColumns(WIDTH, modelSolverPair.model, modelSolverPair.solver, FailReason.INVALID, fastest.result.getValue(),
                            "value agrees, solution infeasible");
                    totReasons.put(modelSolverPair, FailReason.INVALID);
                    iterDone.add(modelSolverPair);

                } else if (mainResults.count() == 1) {

                    // Report the first pass whether or not the pair is done with - otherwise a stabilising
                    // run shows nothing but failures until the third iteration.
                    BasicLogger.debugColumns(WIDTH, modelSolverPair.model, modelSolverPair.solver, "Solved", mainResults.fastest.duration,
                            mainResults.fastest.result.getValue());
                    if (mainResults.isStable()) {
                        iterDone.add(modelSolverPair);
                    }

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

            ResultsSet mainResults = totResults.computeIfAbsent(modelSolverPair, k -> new ResultsSet(configuration.maxIterations));
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

            ResultsSet mainResults = totResults.computeIfAbsent(modelSolverPair, k -> new ResultsSet(configuration.maxIterations));
            mainResults.add(FAILED);

            BasicLogger.debugColumns(WIDTH, modelSolverPair.model, modelSolverPair.solver, FAILED.result.getState(), FailReason.FAILED);
            totReasons.put(modelSolverPair, FailReason.FAILED);
            iterDone.add(modelSolverPair);
        }
    }

    static TimedResult<Result> meassure(final ExpressionsBasedModel model, final ExpressionsBasedModel.Integration<?> integration) {
        return Stopwatch.meassure(() -> AbstractBenchmark.solve(model, integration));
    }

    static Optimisation.Result solve(final ExpressionsBasedModel model, final ExpressionsBasedModel.Integration<?> integration) {

        Optimisation.Result result = null;

        boolean maximisation = model.getOptimisationSense() == Optimisation.Sense.MAX;

        if (maximisation) {
            if (integration != null) {
                result = model.maximise(integration);
            } else {
                result = model.maximise();
            }
        } else {
            if (integration != null) {
                result = model.minimise(integration);
            } else {
                result = model.minimise();
            }
        }

        return result;
    }

}
