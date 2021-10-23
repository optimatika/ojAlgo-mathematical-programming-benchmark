/*
 * Copyright 1997-2021 Optimatika
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
package org.ojalgo.benchmark.optimisation.lp.netlib;

import java.io.InputStream;
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

public class NetlibBenchmark {

    enum FailReason {
        TIMEOUT, WRONG;
    }

    static final class ModelSolverPair implements Comparable<ModelSolverPair> {

        String model;
        String solver;

        ModelSolverPair(final String m, final String s) {
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

    static final class ResultsSet {

        static boolean isSimilar(final double latest1, final double latest2, final double halfRelativeError) {
            if (Math.abs(latest1 - latest2) / (latest1 + latest2) > halfRelativeError) {
                return false;
            }
            return true;
        }

        private final List<TimedResult<Optimisation.Result>> all = new ArrayList<>();
        private final double halfRelativeError;

        TimedResult<Optimisation.Result> fastest;

        ResultsSet(final double accuracy) {
            super();
            halfRelativeError = accuracy / 2D;
        }

        void add(final TimedResult<Result> another) {

            Objects.requireNonNull(another);

            all.add(another);

            if (fastest != null) {

                Result fastestR = fastest.result;
                Result anotherR = another.result;

                State stateF = fastestR.getState();
                State stateA = anotherR.getState();

                double valueF = fastestR.getValue();
                double valueA = anotherR.getValue();

                if (stateF != stateA || !ResultsSet.isSimilar(valueF, valueA, halfRelativeError)) {
                    fastest = new TimedResult<>(anotherR.withState(Optimisation.State.FAILED), another.duration);
                } else if (fastest.duration.measure > another.duration.measure) {
                    fastest = another;
                }

            } else {

                fastest = another;
            }
        }

        boolean isStable() {

            int size = all.size();

            if (size < 3) {
                return false;
            }

            double latest1 = all.get(size - 1).duration.toDurationInMillis();
            double latest2 = all.get(size - 2).duration.toDurationInMillis();

            return ResultsSet.isSimilar(latest1, latest2, halfRelativeError);
        }

    }

    static final TimedResult<Optimisation.Result> FAILED = new TimedResult<>(Optimisation.Result.of(0.0, Optimisation.State.FAILED),
            new CalendarDateDuration(30, CalendarDateUnit.SECOND).convertTo(CalendarDateUnit.MILLIS));

    static final Map<String, ExpressionsBasedModel.Integration<?>> INTEGRATIONS = new HashMap<>();
    static final String[] MODELS = new String[] { "VTP-BASE", "TUFF", "STOCFOR1", "STAIR", "SHARE2B", "SHARE1B", "SEBA", "SCTAP1", "SCSD1", "SCORPION",
            "SCFXM2", "SCFXM1", "SCAGR7", "SCAGR25", "SC50B", "SC50A", "SC205", "SC105", "RECIPELP", "PILOT4", "LOTFI", "KB2", "ISRAEL", "GROW7", "GROW22",
            "GROW15", "FORPLAN", "FINNIS", "FFFFF800", "ETAMACRO", "E226", "DEGEN2", "CAPRI", "BRANDY", "BORE3D", "BOEING2", "BOEING1", "BLEND", "BEACONFD",
            "BANDM", "AGG3", "AGG2", "AGG", "AFIRO", "ADLITTLE" };
    static final String[] SOLVERS = new String[] { "CPLEX", "ojAlgo", "ACM" };
    static final int WIDTH = 22;
    static final Set<ModelSolverPair> WORK = new HashSet<>();

    static {

        for (String mod : MODELS) {
            for (String sol : SOLVERS) {
                WORK.add(new ModelSolverPair(mod, sol));
            }
        }

        INTEGRATIONS.put("ACM", SolverCommonsMath.INTEGRATION);
        INTEGRATIONS.put("CPLEX", SolverCPLEX.INTEGRATION);
        // INTEGRATIONS.put("Gurobi", SolverGurobi.INTEGRATION);
        // INTEGRATIONS.put("JOptimizer", SolverJOptimizer.INTEGRATION);
        // INTEGRATIONS.put("Mosek", SolverMosek.INTEGRATION);
    }

    public static void main(final String[] args) {

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

                String path = "/optimisation/netlib/" + work.model + ".SIF";

                try (InputStream input = NetlibBenchmark.class.getResourceAsStream(path)) {
                    ExpressionsBasedModel parsedMPS = ExpressionsBasedModel.parse(input, FileFormat.MPS);

                    ResultsSet resultsSet = results.computeIfAbsent(work, k -> new ResultsSet(0.01));

                    TimedResult<Optimisation.Result>[] resultA = (TimedResult<Result>[]) new TimedResult<?>[1];

                    Thread worker = new Thread(() -> {
                        ResultsSet subresults = new ResultsSet(0.1);
                        while (!subresults.isStable()) {
                            subresults.add(NetlibBenchmark.meassure(parsedMPS));
                        }
                        resultA[0] = subresults.fastest;
                    });

                    long start = System.currentTimeMillis();
                    worker.start();

                    while (resultA[0] == null && System.currentTimeMillis() - start <= 60_000L) {
                        Thread.sleep(1_000L);
                    }

                    worker.stop();

                    TimedResult<Optimisation.Result> result = resultA[0];

                    if (result != null) {

                        resultsSet.add(result);

                        if (!result.result.getState().isOptimal()) {

                            BasicLogger.debug(WIDTH, work.model, work.solver, result.result.getState());

                            done.add(work);
                            reasons.put(work, FailReason.WRONG);
                        }

                        if (resultsSet.isStable()) {

                            BasicLogger.debug(WIDTH, work.model, work.solver, "Time stable");

                            done.add(work);
                        }

                    } else {

                        BasicLogger.debug(WIDTH, work.model, work.solver, FAILED.result.getState());

                        resultsSet.add(FAILED);
                        done.add(work);
                        reasons.put(work, FailReason.TIMEOUT);
                    }

                } catch (Throwable cause) {
                    BasicLogger.error("Error working with {}!", work);
                    throw new RuntimeException(cause);
                }
            }

            WORK.removeAll(done);

        } while (WORK.size() > 0);

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

            if (state.isOptimal()) {
                BasicLogger.debug(WIDTH, model, solver, state, value, duration);
            } else {
                BasicLogger.debug(WIDTH, model, solver, Optimisation.State.FAILED, reasons.get(work));
            }
        }
    }

    static TimedResult<Result> meassure(final ExpressionsBasedModel model) {
        return Stopwatch.meassure(() -> NetlibBenchmark.solve(model));
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
