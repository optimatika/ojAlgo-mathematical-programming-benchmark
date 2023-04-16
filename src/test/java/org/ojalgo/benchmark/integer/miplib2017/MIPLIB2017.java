/*
 * Copyright 1997-2023 Optimatika
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
package org.ojalgo.benchmark.integer.miplib2017;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.ojalgo.TestUtils;
import org.ojalgo.netio.BasicLogger;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.ExpressionsBasedModel.FileFormat;
import org.ojalgo.optimisation.Optimisation;
import org.ojalgo.optimisation.Optimisation.Result;
import org.ojalgo.optimisation.Optimisation.State;
import org.ojalgo.optimisation.integer.IntegerSolver;
import org.ojalgo.optimisation.integer.IntegerStrategy;
import org.ojalgo.optimisation.linear.LinearSolver;
import org.ojalgo.random.FrequencyMap;
import org.ojalgo.type.CalendarDateUnit;
import org.ojalgo.type.Stopwatch;
import org.ojalgo.type.context.NumberContext;
import org.opentest4j.AssertionFailedError;

public abstract class MIPLIB2017 {

    static final NumberContext ACCURACY = NumberContext.of(8);
    /**
     * Map file name to optimal value. To be included here the model should be in both the "benchmark" and
     * "easy" sets. In addition there should be a proven optimal solution listed (no infeasible models or with
     * just best bounds).
     */
    static final Map<String, BigDecimal> INSTANCES = new HashMap<>();
    static final Set<String> KNOWN_PROBLEMS = new HashSet<>();
    static final Map<String, IntegerStrategy> STRATEGIES = new HashMap<>();
    static final Stopwatch TIMER = new Stopwatch();

    static {

        // ParseFiles problems

        // RelaxedLP problems
        KNOWN_PROBLEMS.add("cod105.mps.gz");
        KNOWN_PROBLEMS.add("csched008.mps.gz");

        // FeasibleMIP problems
        KNOWN_PROBLEMS.add("enlight_hard.mps.gz");

        // OptimalMIP problems
        // KNOWN_PROBLEMS.add("gen-ip002.mps.gz");
        // KNOWN_PROBLEMS.add("gen-ip054.mps.gz");

        Set<String> benchmark = new HashSet<>();
        Set<String> easy = new HashSet<>();

        String line;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(TestUtils.getResource("MIPLIB2017", "benchmark-v2.test.txt")))) {

            while ((line = reader.readLine()) != null) {
                benchmark.add(line.trim().toLowerCase());
            }

        } catch (IOException cause) {
            throw new RuntimeException(cause);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(TestUtils.getResource("MIPLIB2017", "easy-v9.test.txt")))) {

            while ((line = reader.readLine()) != null) {
                easy.add(line.trim().toLowerCase());
            }

        } catch (IOException cause) {
            throw new RuntimeException(cause);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(TestUtils.getResource("MIPLIB2017", "miplib2017-v22.solu.txt")))) {

            while ((line = reader.readLine()) != null) {

                String[] parts = line.split("\\s+");

                if (parts.length == 3) {

                    String state = parts[0];
                    String name = parts[1];
                    String value = parts[2];

                    if (!"=opt=".equals(state)) {
                        continue;
                    }

                    String file = name + ".mps.gz";

                    if (!benchmark.contains(file) || !easy.contains(file) || KNOWN_PROBLEMS.contains(file)) {
                        continue;
                    }

                    BigDecimal optimal = new BigDecimal(value);

                    INSTANCES.put(file, optimal);
                }

            }

        } catch (IOException cause) {
            throw new RuntimeException(cause);
        }

    }

    private static Optimisation.Result execute(final String fileName, final boolean relaxed, final int sizeLimit, final boolean optimal,
            final Optimisation.Options options) {

        BigDecimal optimalValue = INSTANCES.get(fileName);

        BasicLogger.debug();
        BasicLogger.debug();
        BasicLogger.debug("{} {}", fileName, optimalValue);
        BasicLogger.debug("===========================================");
        BasicLogger.debug();

        try (InputStream input = new GZIPInputStream(TestUtils.getResource("MIPLIB2017", fileName))) {

            ExpressionsBasedModel model = ExpressionsBasedModel.parse(input, FileFormat.MPS);

            model.options.time_suffice = options.time_suffice;
            model.options.time_abort = options.time_abort;
            model.options.integer(options.integer());
            if (relaxed) {
                model.options.progress(LinearSolver.class);
            } else {
                model.options.progress(IntegerSolver.class);
            }

            int nbVariables = model.countVariables();
            int nbExpressions = model.countExpressions();
            boolean maximisation = model.getOptimisationSense() == Optimisation.Sense.MAX;

            BasicLogger.debug("There are {} variables and {} expressions.", nbVariables, nbExpressions);

            if (relaxed) {
                model.relax(false);
            }

            if (nbVariables > sizeLimit || nbExpressions > sizeLimit) {
                BasicLogger.debug("Skipping {} because of size limit: {}", fileName, sizeLimit);
                BasicLogger.debug();
                return Result.of(State.UNEXPLORED);
            }

            Optimisation.Result result = null;

            TIMER.reset();

            if (maximisation) {
                result = model.maximise();
            } else {
                result = model.minimise();
            }

            BasicLogger.debug();
            BasicLogger.debug("{} in {}, {} was {}", fileName, TIMER.stop(CalendarDateUnit.SECOND), optimalValue, result.toString());

            try {
                if (optimal) {
                    TestUtils.assertStateNotLessThanOptimal(result);
                } else {
                    TestUtils.assertStateNotLessThanFeasible(result);
                }
            } catch (AssertionFailedError cause) {
                BasicLogger.debug(State.FAILED);
                return result.withState(State.FAILED);
            }

            double expected = optimalValue.doubleValue();
            double actual = result.getValue();

            try {
                if (relaxed || optimal) {
                    if (maximisation) {
                        TestUtils.assertTrue(!ACCURACY.isDifferent(expected, actual) || actual > expected);
                    } else {
                        TestUtils.assertTrue(!ACCURACY.isDifferent(expected, actual) || actual < expected);
                    }
                }
            } catch (AssertionFailedError cause) {
                BasicLogger.debug(State.APPROXIMATE);
                return result.withState(State.APPROXIMATE);
            }

            return result;

        } catch (IOException cause) {
            throw new RuntimeException(cause);
        }
    }

    static void doOne(final String fileName, final Optimisation.Options options) {
        MIPLIB2017.execute(fileName, false, Integer.MAX_VALUE, true, options);
    }

    static void doRun(final boolean relaxed, final int sizeLimit, final boolean optimal, final Optimisation.Options options) {

        FrequencyMap<Optimisation.State> statistics = new FrequencyMap<>();

        for (String fileName : MIPLIB2017.INSTANCES.keySet()) {

            IntegerStrategy strategy = STRATEGIES.getOrDefault(fileName, IntegerStrategy.DEFAULT);

            Result result = MIPLIB2017.execute(fileName, relaxed, sizeLimit, optimal, options);

            statistics.increment(result.getState());
        }

        BasicLogger.debug();
        BasicLogger.debug();
        BasicLogger.debug("Statistics");
        BasicLogger.debug("===========================================");
        for (State key : statistics.elements()) {
            BasicLogger.debug("{} = {}", key, statistics.getFrequency(key));
        }
    }

}
