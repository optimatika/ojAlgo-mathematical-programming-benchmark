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
package org.ojalgo.benchmark.integer.miplib;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import org.ojalgo.benchmark.AbstractBenchmark;
import org.ojalgo.netio.BasicLogger;
import org.ojalgo.netio.TextLineReader;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.ExpressionsBasedModel.FileFormat;

abstract class AbstractMIPLIB extends AbstractBenchmark {

    private static final Set<String> EXCLUDED = new HashSet<>();
    private static final String RESOURCE_DIR = "optimisation/MIPLIB/";

    private static Set<ModelSolverPair> createWorkSet(final Configuration configuration) {

        Set<ModelSolverPair> retVal = new HashSet<>();

        try (TextLineReader reader = new TextLineReader(Thread.currentThread().getContextClassLoader().getResourceAsStream(RESOURCE_DIR + "MIPLIB.dat"))) {

            reader.forEach(line -> {

                if (EXCLUDED.contains(line)) {
                    return;
                }

                try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(RESOURCE_DIR + line + ".mps")) {

                    ExpressionsBasedModel model = ExpressionsBasedModel.parse(input, FileFormat.MPS);

                    ExpressionsBasedModel.Description description = model.describe();

                    if ((configuration.investigate.isEmpty() || configuration.investigate.contains(line))
                            && description.nbVariables >= configuration.minProbSize && description.nbVariables <= configuration.maxProbSize
                            && description.countConstraints() <= configuration.maxProbSize) {
                        for (String solver : configuration.solvers) {
                            retVal.add(new ModelSolverPair(line, solver));
                        }
                    }

                } catch (IOException cause) {
                    BasicLogger.debug("Problem with model {}!", line);
                    throw new RuntimeException(cause);
                } catch (RuntimeException cause) {
                    BasicLogger.debug("Skipping model {} ({})", line, cause.getMessage());
                }

            });

        } catch (IOException cause) {
            BasicLogger.debug("Problem reading list of models!");
            throw new RuntimeException(cause);
        }

        return retVal;
    }

    static void doBenchmark(final Configuration configuration) {

        Set<ModelSolverPair> allWork = AbstractMIPLIB.createWorkSet(configuration);

        AbstractBenchmark.doBenchmark(allWork, configuration);
    }

    static void loadExpectedValues(final Configuration configuration) {

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Thread.currentThread().getContextClassLoader().getResourceAsStream(RESOURCE_DIR + "miplib.solu")))) {

            String line;
            while ((line = reader.readLine()) != null) {

                String[] parts = line.split("\\s+");

                if (parts.length >= 2 && ("=inf=".equals(parts[0]) || "=unbd=".equals(parts[0]))) {
                    EXCLUDED.add(parts[1]);
                } else if (parts.length == 3 && "=opt=".equals(parts[0])) {
                    configuration.values.put(parts[1], new BigDecimal(parts[2]));
                }
            }

        } catch (IOException cause) {
            throw new RuntimeException(cause);
        }
    }

}
