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
package org.ojalgo.benchmark.qplib;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Predicate;

import org.ojalgo.benchmark.AbstractBenchmark;
import org.ojalgo.benchmark.qplib.QPLIBModels.ModelInfo;
import org.ojalgo.netio.BasicLogger;
import org.ojalgo.netio.TextLineReader;

abstract class AbstractQPLIB extends AbstractBenchmark {

    private static final String RESOURCE_DIR = "optimisation/QPLIB/";

    static Set<ModelSolverPair> createWorkSet(final Configuration configuration, final Predicate<ModelInfo> filter) {

        Set<ModelSolverPair> retVal = new HashSet<>();

        try (TextLineReader reader = new TextLineReader(Thread.currentThread().getContextClassLoader().getResourceAsStream(RESOURCE_DIR + "QPLIB.dat"))) {

            reader.forEach(line -> {

                ModelInfo info = QPLIBModels.getModelInfo(line);

                if (info == null) {
                    BasicLogger.debug("No metadata for model {}!", line);
                    return;
                }

                if (info.nvars < configuration.minProbSize || info.nvars > configuration.maxProbSize || info.ncons > configuration.maxProbSize) {
                    return;
                }

                if (!configuration.investigate.isEmpty() && !configuration.investigate.contains(line)) {
                    return;
                }

                if (filter != null && !filter.test(info)) {
                    return;
                }

                for (String solver : configuration.solvers) {
                    retVal.add(new ModelSolverPair(line, solver));
                }
            });

        } catch (IOException cause) {
            BasicLogger.debug("Problem reading list of models!");
            throw new RuntimeException(cause);
        }

        return retVal;
    }

    static void doBenchmark(final Configuration configuration, final Predicate<ModelInfo> filter) {

        Set<ModelSolverPair> allWork = AbstractQPLIB.createWorkSet(configuration, filter);

        AbstractBenchmark.doBenchmark(allWork, configuration);
    }

    static void loadExpectedValues(final Configuration configuration) {
        for (Entry<String, ModelInfo> entry : QPLIBModels.getModelInfo().entrySet()) {
            if (entry.getValue().solobjvalue != null) {
                configuration.values.put(entry.getKey(), entry.getValue().solobjvalue);
            }
        }
    }

}
