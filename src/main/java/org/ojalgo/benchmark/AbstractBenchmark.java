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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.CharBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.ojalgo.OjAlgoUtils;
import org.ojalgo.benchmark.ForkedTask.ReturnValue;
import org.ojalgo.concurrent.ExternalProcessExecutor;
import org.ojalgo.concurrent.Parallelism;
import org.ojalgo.concurrent.ParallelismSupplier;
import org.ojalgo.concurrent.ProcessOptions;
import org.ojalgo.concurrent.ProcessingService;
import org.ojalgo.netio.ASCII;
import org.ojalgo.netio.BasicLogger;
import org.ojalgo.netio.TextLineReader;
import org.ojalgo.netio.TextLineWriter;
import org.ojalgo.netio.TextLineWriter.CSVLineBuilder;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Optimisation;
import org.ojalgo.optimisation.Optimisation.Result;
import org.ojalgo.optimisation.Optimisation.State;
import org.ojalgo.random.SampleSet;
import org.ojalgo.type.CalendarDateDuration;
import org.ojalgo.type.CalendarDateUnit;
import org.ojalgo.type.Stopwatch;
import org.ojalgo.type.Stopwatch.TimedResult;
import org.ojalgo.type.context.NumberContext;

public abstract class AbstractBenchmark {

    public static final class Configuration {

        /**
         * Distinguishes the output files of different runs of the same benchmark class - different solver
         * builds, different size ranges... The files are named after the class whose main method ran the
         * benchmark, then this label (if any), then the number of workers.
         */
        public String label = null;
        /**
         * Absolute paths to the native libraries to use, keyed by contender name. Anything not listed here is
         * resolved the usual way - whatever the integration's own loader finds installed.
         * <p>
         * The library is loaded in the worker JVM before the integration initialises, and both the HiGHS and
         * SCIP loaders check for an already-loaded library before searching, so that is the one they bind to.
         * <p>
         * One build per solver per run. Worker JVMs are reused, and a library, once loaded, stays loaded - so
         * two builds of the same solver cannot be told apart within a single run. Compare builds by running
         * again with a different path and a different {@link #label}.
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
         * own budget on every pass, until its last three times agree, and reports the middle one. The pair is
         * done once the last three passes agree the same way, and its time is the middle one of those.
         * <p>
         * At 1 the reported time is a single sample, and how warm it is depends on what the worker JVM ran
         * before. Worker JVMs are reused across pairs and only restarted after a timeout or failure, so the
         * first solve with a given solver in a worker includes class loading, JIT warm-up, native library
         * loading and environment creation, while later ones don't. On models that solve in milliseconds that
         * difference dominates, so read those times as "it worked", not as measurements, and don't compare
         * them against a longer run.
         */
        public int maxIterations = DEFAULT_MAX_ITERATIONS;
        /**
         * Models with more variables, or more constraints, than this are left out. Checked on the parsed
         * model.
         */
        public int maxProbSize = 10_000;
        /**
         * ms
         */
        public long maxWaitTime = 1_000L * 60L * 5L;
        /**
         * Models with fewer variables than this are left out. Checked on the parsed model.
         */
        public int minProbSize = 1;
        /**
         * The models to consider. The factory of each model set fills it with the full set, and it is then
         * narrowed - normally by filtering on properties from the set's metadata, where there is any, but it
         * can also be replaced by, or intersected with, an explicit list. What is left is filtered on size,
         * {@link #minProbSize} and {@link #maxProbSize}, when the benchmark starts.
         */
        public final Set<String> models = new TreeSet<>();
        /**
         * The number of worker JVMs solving concurrently. Published results always use a single worker -
         * more only to get results faster.
         */
        public ParallelismSupplier parallelism = Parallelism.ONE;
        public String pathPrefix;
        public String pathSuffix;
        public final String[] solvers;
        /**
         * Expected (optimal) objective values, keyed by model name - from the model set's metadata, where there
         * is any. A model without one is solved once with a reference solver before the benchmark starts, and
         * that value is used instead - see {@link AbstractBenchmark#referenceSolver(ExpressionsBasedModel)}.
         * Not when {@link #maxIterations} is 1 - that is only to test which solvers can solve which models. A
         * model that still has no expected value is only checked to be OPTIMAL and feasible.
         */
        public final Map<String, BigDecimal> values = new HashMap<>();

        public Configuration(final String... solvers) {
            super();
            this.solvers = solvers;
        }

        public String path(final String modelName) {
            return pathPrefix + modelName + pathSuffix;
        }

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
         * Reported a solution that does not satisfy the model's constraints. Distinct from WRONG: the
         * objective value can still agree with the reference while the solution itself is infeasible.
         */
        INVALID,
        /**
         * Hangs or takes too long
         */
        TIMEOUT,
        /**
         * Not always the same results between executions (a variation on WRONG)
         */
        UNSTABLE,
        /**
         * Does not match the expected value, or the reference solver - or claims something other than
         * optimal, such as infeasible, unbounded or merely feasible
         */
        WRONG;
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

    /**
     * Collects the measurements of one model/solver pair. Only the last three times count: measuring is done
     * once they agree - the slowest and the fastest within 10% of the middle one - and the middle one is
     * what's reported. Earlier times are dropped, as they're from before the JVM was warm, or before the CPU
     * had heated up. The middle one rather than the fastest, because for a solver that isn't deterministic -
     * parallel branch-and-bound, anything multi-threaded - the fastest is the luckiest.
     * <p>
     * Used at two levels: within a forked task each solve is one {@link #add(TimedResult)}, and the task
     * returns the middle of its last three. In the main process each forked task is one
     * {@link #add(ForkedTask.ReturnValue)}, and what's reported is the middle of the last three of those.
     * <p>
     * Every result is compared against the first one. Should one disagree - a different state, or a value
     * that differs - the state is downgraded, and stays that way, so the pair can't count as solved.
     */
    static final class ResultsSet {

        private static final int WINDOW = 3;

        private boolean myConsistent = true;
        private int myCount = 0;
        /**
         * The last {@link #WINDOW} times, ms, in a ring
         */
        private final double[] myLastTimes = new double[WINDOW];
        private final int myMaxCount;
        /**
         * The middle of the last times, ms
         */
        private double myMedian = Double.NaN;
        private int myNbTimes = 0;
        /**
         * The difference between the slowest and the fastest of the last times, ms
         */
        private double myRange = Double.NaN;
        private Optimisation.Result myResult = null;
        private final double myTimeAccuracy;
        private final NumberContext myValueAccuracy;

        /**
         * No cap on the count - for the forked task, which is bounded by its time budget instead.
         */
        public ResultsSet() {
            this(Integer.MAX_VALUE);
        }

        public ResultsSet(final int maxCount) {
            this(ACCURACY, 0.1, maxCount);
        }

        private ResultsSet(final NumberContext valueAccuracy, final double timeAccuracy, final int maxCount) {
            super();
            myValueAccuracy = valueAccuracy;
            myTimeAccuracy = timeAccuracy;
            myMaxCount = maxCount;
        }

        /**
         * @return The result of that forked task (not the aggregate), or null if it didn't return one.
         */
        public Result add(final ForkedTask.ReturnValue returnValue) {

            if (returnValue == null || returnValue.result == null) {
                this.add(FAILED.result);
                return null;
            }

            Optimisation.Result result = Optimisation.Result.parse(returnValue.result);

            this.add(result, returnValue.time);

            return result;
        }

        public void add(final TimedResult<Result> another) {

            Objects.requireNonNull(another);

            if (another.result == null || another.duration == null) {
                this.add(FAILED.result);
            } else {
                this.add(another.result, another.duration.convertTo(CalendarDateUnit.MILLIS).measure);
            }
        }

        public int count() {
            return myCount;
        }

        /**
         * False once any result has disagreed with the first one.
         */
        public boolean isConsistent() {
            return myConsistent;
        }

        /**
         * True once no further measurements are wanted - either because the count is spent, or because the
         * last three times agree: the slowest and the fastest within 10% of the middle one. The count is
         * checked first, so a max of 1 means "one measurement is all we're after".
         */
        public boolean isStable() {

            if (myCount >= myMaxCount) {
                return true;
            }

            if (myNbTimes < WINDOW) {
                return false;
            }

            return myRange < myTimeAccuracy * myMedian;
        }

        /**
         * The first result, its state downgraded if a later one disagreed, together with the middle of the
         * last three times (or fewer, if there aren't three yet). {@link AbstractBenchmark#FAILED} if there
         * are no times, null if nothing was added.
         */
        public TimedResult<Result> median() {

            if (myResult == null) {
                return null;
            }

            if (Double.isNaN(myMedian)) {
                return FAILED;
            }

            return new TimedResult<>(myResult, new CalendarDateDuration(myMedian, CalendarDateUnit.MILLIS));
        }

        /**
         * The middle of the last three times (or fewer, if there aren't three yet), ms - NaN if there are
         * none.
         */
        public double medianTime() {
            return myMedian;
        }

        /**
         * The first result, its state downgraded if a later one disagreed.
         */
        public Result result() {
            return myResult;
        }

        private void add(final Result result) {
            this.add(result, Double.NaN);
        }

        private void add(final Result result, final double time) {

            myCount++;

            if (myResult == null) {
                myResult = result;
            } else if (myResult.getState() != result.getState()) {
                myResult = myResult.withState(Optimisation.State.INVALID);
                myConsistent = false;
            } else if (myValueAccuracy.isDifferent(myResult.getValue(), result.getValue())) {
                myResult = myResult.withState(Optimisation.State.APPROXIMATE);
                myConsistent = false;
            }

            if (!Double.isNaN(time)) {

                myLastTimes[myNbTimes % WINDOW] = time;
                myNbTimes++;

                SampleSet window = SampleSet.wrap(Arrays.copyOf(myLastTimes, Math.min(myNbTimes, WINDOW)));
                myMedian = window.getMedian();
                myRange = window.getRange();
            }
        }

    }

    /**
     * Writes everything to the console as well as to a file.
     */
    static final class Tee extends Writer {

        private final PrintStream myConsole;
        private final Writer myFile;

        Tee(final PrintStream console, final String filePath) throws IOException {
            super();
            myConsole = console;
            myFile = Files.newBufferedWriter(Path.of(filePath));
        }

        /**
         * Closes the file, but not the console.
         */
        @Override
        public void close() throws IOException {
            myConsole.flush();
            myFile.close();
        }

        @Override
        public void flush() throws IOException {
            myConsole.flush();
            myFile.flush();
        }

        @Override
        public void write(final char[] buffer, final int offset, final int length) throws IOException {
            myConsole.append(CharBuffer.wrap(buffer, offset, length));
            myFile.write(buffer, offset, length);
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
     * Measured on this model set the margins are wide in every direction. Legitimate solves disagree by ~1E-4
     * at worst while genuinely wrong values are off by tens of percent. Residuals differ by two or three
     * orders of magnitude between the dense and sparse stores (1E-10 against 1E-8 on one model, 1E-8 against
     * 1E-5 on another) without either being wrong, while a genuinely infeasible point was off by 1E+4. Note
     * that feasibility is judged per constraint, with the precision applied relative to each limit, so the
     * value has to suit small limits too.
     */
    static final NumberContext ACCURACY = NumberContext.of(4);

    static final int DEFAULT_MAX_ITERATIONS = 20;

    static final TimedResult<Optimisation.Result> FAILED = new TimedResult<>(Optimisation.Result.of(0.0, Optimisation.State.FAILED),
            new CalendarDateDuration(30, CalendarDateUnit.MINUTE).convertTo(CalendarDateUnit.MILLIS));

    /**
     * Where the results and console logs are written.
     */
    static final String OUTPUT_DIR = "./src/main/resources/";
    /**
     * Where the native solvers are installed - library paths and environment variables for the worker JVMs.
     * See the file itself for the format.
     */
    static final String NATIVE_SOLVERS = "./native-solvers.properties";
    static final int WIDTH = 22;

    /**
     * Runs every one of {@link Configuration#models} that is within the size limits, with every one of
     * {@link Configuration#solvers}. Each model is parsed once, here, to check its size - a model that is
     * missing, or fails to parse, is left out. The index files also list the models that are
     * too large to be shipped (more than 10k variables or constraints), so missing files are only counted.
     * <p>
     * Writes two files to {@link #OUTPUT_DIR}, both named after the class whose main method called this -
     * plus {@link Configuration#label} and the number of workers: the results, {@code *_output.csv}, and
     * everything logged, {@code *_console.log}.
     */
    public static void doBenchmark(final Configuration configuration) {

        Class<?> benchmark = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();

        int workers = configuration.parallelism.getAsInt();

        String name = benchmark.getSimpleName() + (configuration.label != null ? "_" + configuration.label : "") + "_" + workers;

        try (BasicLogger.BasicWriter log = new BasicLogger.BasicWriter(new Tee(System.out, OUTPUT_DIR + name + "_console.log"))) {
            AbstractBenchmark.doBenchmark(configuration, log, OUTPUT_DIR + name + "_output.csv");
        } catch (IOException cause) {
            throw new RuntimeException(cause);
        }
    }

    private static void doBenchmark(final Configuration configuration, final BasicLogger log, final String outputPath) {

        ProcessingService masterProcessor = ProcessingService.newInstance("benchmark");
        ExternalProcessExecutor slaveExecutor = ExternalProcessExecutor.newInstance();

        Map<ModelSolverPair, ResultsSet> totResults = new ConcurrentHashMap<>();
        Map<ModelSolverPair, FailReason> totReasons = new ConcurrentHashMap<>();
        Map<String, ModelSize> modDim = new ConcurrentHashMap<>();

        Set<ModelSolverPair> allWork = new HashSet<>();
        Map<String, String> references = new HashMap<>();
        int nbMissing = 0;

        for (String model : configuration.models) {

            String filePath = configuration.path(model);
            String clPath = filePath.startsWith("/") ? filePath.substring(1) : filePath;
            ExpressionsBasedModel.FileFormat format = clPath.endsWith(".lp") ? ExpressionsBasedModel.FileFormat.LP : ExpressionsBasedModel.FileFormat.MPS;

            try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(clPath)) {

                if (input == null) {
                    nbMissing++;
                    continue;
                }

                ExpressionsBasedModel parsed = ExpressionsBasedModel.parse(input, format);

                ExpressionsBasedModel.Description description = parsed.describe();

                if (description.nbVariables >= configuration.minProbSize && description.nbVariables <= configuration.maxProbSize
                        && description.countConstraints() <= configuration.maxProbSize) {

                    modDim.put(model, new ModelSize(parsed.countExpressions(), parsed.countVariables(), parsed.objective().density()));

                    for (String solver : configuration.solvers) {
                        allWork.add(new ModelSolverPair(model, solver));
                    }

                    if (configuration.maxIterations > 1 && !configuration.values.containsKey(model)) {
                        references.put(model, AbstractBenchmark.referenceSolver(parsed));
                    }
                }

            } catch (IOException | RuntimeException cause) {
                log.println("Skipping model {} ({})", model, cause.getMessage());
            }
        }

        int workers = configuration.parallelism.getAsInt();
        int threadsPerWorker = Parallelism.THREADS.divideBy(workers).getAsInt();

        int iterations = 0;
        Set<ModelSolverPair> iterDone = ConcurrentHashMap.newKeySet();

        log.println();
        log.println("Environment: {}", OjAlgoUtils.ENVIRONMENT);
        log.println("Workers: {}, threads per worker: {}", workers, threadsPerWorker);
        log.println("Models: {} of {} within size limits ({} not shipped), solvers: {}", modDim.size(), configuration.models.size(), nbMissing,
                configuration.solvers.length);

        ProcessOptions workerOptions = AbstractBenchmark.newWorkerOptions(log);

        log.println();

        if (!references.isEmpty()) {

            log.println();
            log.println("Reference values for {} models without an expected value {}", references.size(), Instant.now());
            log.println("-----------------------------------------------------------------------------");

            Map<String, BigDecimal> referenceValues = new ConcurrentHashMap<>();

            masterProcessor.process(references.keySet(), configuration.parallelism, model -> AbstractBenchmark.doOneReference(configuration, log,
                    slaveExecutor, workerOptions, threadsPerWorker, model, references.get(model), referenceValues));

            configuration.values.putAll(referenceValues);
        }

        do {

            iterations++;
            iterDone.clear();

            log.println();
            log.println("Iteration {} with {} model/solver pairs remaining {}", iterations, allWork.size(), Instant.now());
            log.println("-----------------------------------------------------------------------------");

            masterProcessor.process(allWork, configuration.parallelism, modelSolverPair -> AbstractBenchmark.doOnePair(configuration, log, slaveExecutor,
                    workerOptions, totResults, totReasons, modDim, iterDone, threadsPerWorker, modelSolverPair));

            allWork.removeAll(iterDone);

        } while (allWork.size() > 0);

        Map<ModelSolverPair, ResultsSet> sortedResults = new TreeMap<>(totResults);

        try (TextLineWriter writer = TextLineWriter.of(outputPath)) {

            CSVLineBuilder csv = writer.newCSVLineBuilder(ASCII.HT);

            csv.line("Model", "Solver", "Time", "nbVars", "nbExpr", "density");

            Map<String, int[]> tally = new TreeMap<>();

            log.println();
            log.println("Final Results");
            log.println("=====================================================================");
            for (Entry<ModelSolverPair, ResultsSet> entry : sortedResults.entrySet()) {

                ModelSolverPair work = entry.getKey();
                TimedResult<Result> result = entry.getValue().median();

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

                boolean solved;
                FailReason reason;
                if (expectedValue != null) {
                    solved = state.isOptimal() && !ACCURACY.isDifferent(expectedValue.doubleValue(), value);
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
                    log.columns(WIDTH, model, solver, state, duration);
                    csv.line(model, solver, duration.toDurationInNanos(), nbVars, nbExpr, density);
                } else {
                    log.columns(WIDTH, model, solver, Optimisation.State.FAILED, reason);
                    csv.line(model, solver, "", nbVars, nbExpr, density);
                }
            }

            log.println();
            log.println("Models Solved");
            log.println("=====================================================================");
            for (Entry<String, int[]> entry : tally.entrySet()) {
                int[] counts = entry.getValue();
                log.columns(WIDTH, entry.getKey(), counts[0] + " / " + counts[1], Math.round(100.0 * counts[0] / counts[1]) + "%");
            }

        } catch (IOException cause) {
            throw new RuntimeException(cause);
        }

    }

    static void doOnePair(final Configuration configuration, final BasicLogger log, final ExternalProcessExecutor executor,
            final ProcessOptions workerOptions, final Map<ModelSolverPair, ResultsSet> totResults, final Map<ModelSolverPair, FailReason> totReasons,
            final Map<String, ModelSize> modDim, final Set<ModelSolverPair> iterDone, final int threadsPerWorker, final ModelSolverPair modelSolverPair) {

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

            future = executor.execute(ForkedTask.DESCRIPTOR, workerOptions, path, modelSolverPair.solver, configuration.maxWaitTime, threadsPerWorker,
                    maxSolves, configuration.libraries.getOrDefault(modelSolverPair.solver, ""), firstPass);

            ReturnValue subResults = future.get(configuration.maxWaitTime, TimeUnit.MILLISECONDS);

            modDim.computeIfAbsent(modelSolverPair.model, k -> new ModelSize(subResults.nbExpressions, subResults.nbVariables, subResults.density));

            ResultsSet mainResults = totResults.computeIfAbsent(modelSolverPair, k -> new ResultsSet(configuration.maxIterations));

            if (subResults.result != null) {

                // Have a result

                Result latest = mainResults.add(subResults);

                if (!subResults.consistent || !mainResults.isConsistent()) {

                    // Repeated solves, within this pass or across passes, didn't agree
                    log.columns(WIDTH, modelSolverPair.model, modelSolverPair.solver, latest.getState(), FailReason.UNSTABLE);
                    totReasons.put(modelSolverPair, FailReason.UNSTABLE);
                    iterDone.add(modelSolverPair);

                } else if (!latest.getState().isOptimal()) {

                    // Consistently not optimal - infeasible, unbounded, only feasible...
                    FailReason reason = latest.getState() == Optimisation.State.FAILED ? FailReason.FAILED : FailReason.WRONG;
                    log.columns(WIDTH, modelSolverPair.model, modelSolverPair.solver, latest.getState(), reason);
                    totReasons.put(modelSolverPair, reason);
                    iterDone.add(modelSolverPair);

                } else if (expectedValue != null && ACCURACY.isDifferent(expectedValue.doubleValue(), latest.getValue())) {

                    log.columns(WIDTH, modelSolverPair.model, modelSolverPair.solver, FailReason.WRONG, latest.getValue(), "!= " + expectedValue);
                    totReasons.put(modelSolverPair, FailReason.WRONG);
                    iterDone.add(modelSolverPair);

                } else if (!subResults.valid) {

                    log.columns(WIDTH, modelSolverPair.model, modelSolverPair.solver, FailReason.INVALID, latest.getValue(),
                            "value agrees, solution infeasible");
                    totReasons.put(modelSolverPair, FailReason.INVALID);
                    iterDone.add(modelSolverPair);

                } else {

                    // Report every pass, whether or not the pair is done with, so progress can be followed
                    boolean stable = mainResults.isStable();

                    String what;
                    if (mainResults.count() == 1) {
                        what = "Solved";
                    } else if (stable) {
                        what = "Time stable";
                    } else {
                        what = "Time not stable";
                    }

                    log.columns(WIDTH, modelSolverPair.model, modelSolverPair.solver, what, mainResults.median().duration,
                            mainResults.median().result.getValue());

                    if (stable) {
                        iterDone.add(modelSolverPair);
                    }
                }

            } else {

                // No result, timeout

                mainResults.add(FAILED);

                log.columns(WIDTH, modelSolverPair.model, modelSolverPair.solver, FAILED.result.getState(), FailReason.TIMEOUT);
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

            log.columns(WIDTH, modelSolverPair.model, modelSolverPair.solver, FAILED.result.getState(), FailReason.TIMEOUT);
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

            log.println(cause, "Error working with {}!", modelSolverPair);

            ResultsSet mainResults = totResults.computeIfAbsent(modelSolverPair, k -> new ResultsSet(configuration.maxIterations));
            mainResults.add(FAILED);

            log.columns(WIDTH, modelSolverPair.model, modelSolverPair.solver, FAILED.result.getState(), FailReason.FAILED);
            totReasons.put(modelSolverPair, FailReason.FAILED);
            iterDone.add(modelSolverPair);
        }
    }

    /**
     * Solves the model once with the reference solver, and records its value as the expected one - provided
     * it is optimal and the solution feasible.
     */
    static void doOneReference(final Configuration configuration, final BasicLogger log, final ExternalProcessExecutor executor,
            final ProcessOptions workerOptions, final int threadsPerWorker, final String model, final String solver,
            final Map<String, BigDecimal> referenceValues) {

        Future<ForkedTask.ReturnValue> future = null;
        try {

            future = executor.execute(ForkedTask.DESCRIPTOR, workerOptions, configuration.path(model), solver, configuration.maxWaitTime, threadsPerWorker, 1,
                    configuration.libraries.getOrDefault(solver, ""), true);

            ReturnValue returnValue = future.get(configuration.maxWaitTime, TimeUnit.MILLISECONDS);

            Result result = returnValue.result != null ? Optimisation.Result.parse(returnValue.result) : null;

            if (result != null && result.getState().isOptimal() && returnValue.valid) {
                referenceValues.put(model, BigDecimal.valueOf(result.getValue()));
                log.columns(WIDTH, model, solver, "Reference", result.getValue());
            } else {
                log.columns(WIDTH, model, solver, "No reference", result != null ? result.getState() : FailReason.FAILED);
            }

        } catch (TimeoutException timeout) {

            if (future != null) {
                try {
                    future.cancel(true);
                } catch (Exception ignore) {
                    // ignore
                }
            }

            log.columns(WIDTH, model, solver, "No reference", FailReason.TIMEOUT);

        } catch (Exception cause) {

            if (future != null) {
                try {
                    future.cancel(true);
                } catch (Exception ignore) {
                    // ignore
                }
            }

            log.println(cause, "Error working with {}!", model);
            log.columns(WIDTH, model, solver, "No reference", FailReason.FAILED);
        }
    }

    static TimedResult<Result> meassure(final ExpressionsBasedModel model, final ExpressionsBasedModel.Integration<?> integration) {
        return Stopwatch.meassure(() -> AbstractBenchmark.solve(model, integration));
    }

    /**
     * The options for the worker JVMs: the library paths and environment variables from
     * {@link #NATIVE_SOLVERS}, and native access enabled. The main JVM's own java.library.path is kept, after
     * the configured directories. The main JVM itself needs none of this - only the workers load solvers.
     */
    static ProcessOptions newWorkerOptions(final BasicLogger log) {

        Properties settings = new Properties();

        Path file = Path.of(NATIVE_SOLVERS);
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                settings.load(reader);
            } catch (IOException cause) {
                throw new RuntimeException(cause);
            }
        } else {
            log.println("No {} - native solvers only find what is on the default library path", NATIVE_SOLVERS);
        }

        Set<String> libraryPath = new LinkedHashSet<>();
        Map<String, String> env = new TreeMap<>();

        for (String key : new TreeSet<>(settings.stringPropertyNames())) {

            String value = settings.getProperty(key).trim();

            if (key.endsWith(".library.path")) {
                libraryPath.addAll(Arrays.asList(value.split(File.pathSeparator)));
            } else if (key.contains(".env.")) {
                String name = key.substring(key.indexOf(".env.") + 5);
                env.merge(name, value, (previous, another) -> name.endsWith("PATH") ? previous + File.pathSeparator + another : another);
            } else {
                log.println("Ignoring {} in {}", key, NATIVE_SOLVERS);
            }
        }

        String inherited = System.getProperty("java.library.path");
        if (inherited != null) {
            libraryPath.addAll(Arrays.asList(inherited.split(File.pathSeparator)));
        }
        libraryPath.remove("");

        ProcessOptions.Builder builder = new ProcessOptions.Builder().enableNativeAccessAllUnnamed(true);

        if (!libraryPath.isEmpty()) {
            builder.systemProperty("java.library.path", String.join(File.pathSeparator, libraryPath));
        }
        env.forEach(builder::env);

        log.println("Worker library path: {}", String.join(File.pathSeparator, libraryPath));
        env.forEach((name, value) -> log.println("Worker environment: {}={}", name, value));

        return builder.build();
    }

    /**
     * The model names listed in a model set's index file, such as {@code NETLIB.dat} - one per line, blank
     * lines and lines starting with '#' ignored.
     */
    protected static List<String> readIndex(final String resource) {

        List<String> retVal = new ArrayList<>();

        try (TextLineReader reader = new TextLineReader(Thread.currentThread().getContextClassLoader().getResourceAsStream(resource))) {

            reader.forEach(line -> {
                String name = line.trim();
                if (!name.isEmpty() && !name.startsWith("#")) {
                    retVal.add(name);
                }
            });

        } catch (IOException cause) {
            BasicLogger.debug("Problem reading list of models {}!", resource);
            throw new RuntimeException(cause);
        }

        return retVal;
    }

    /**
     * The solver whose value is used as the expected one, for a model that does not have an expected value:
     * CPLEX for models within the limits of its Community Edition - at most 1k variables and 1k constraints.
     * Otherwise SCIP for MIP, Clarabel for QP and HiGHS for LP.
     */
    static String referenceSolver(final ExpressionsBasedModel model) {

        ExpressionsBasedModel.Description description = model.describe();

        if (description.nbVariables <= 1_000 && description.countConstraints() <= 1_000) {
            return Contender.CPLEX;
        } else if (model.isAnyVariableInteger()) {
            return Contender.SCIP;
        } else if (model.isAnyExpressionQuadratic()) {
            return Contender.CLARABEL;
        } else {
            return Contender.HIGHS;
        }
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
