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
package org.ojalgo.benchmark.linear.netlib;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import org.ojalgo.TestUtils;
import org.ojalgo.benchmark.AbstractBenchmark;
import org.ojalgo.netio.BasicLogger;
import org.ojalgo.netio.TextLineReader;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.ExpressionsBasedModel.FileFormat;

public final class NetlibBenchmark extends AbstractBenchmark {

    static final int MAX_NB_VARS = 587;
    static final int MIN_NB_VARS = 587;

    static final String[] SOLVERS = { Contender.CPLEX, Contender.OJALGO_EXP_SPARSE, Contender.OJALGO_EXP_DENSE, Contender.OJALGO_STD_SPARSE,
            Contender.OJALGO_STD_DENSE, Contender.ORTOOLS, Contender.HIPPARCHUS };
    static final Set<ModelSolverPair> WORK = new HashSet<>();

    static {

        // /ojAlgo/src/test/resources/optimisation/netlib/NETLIB.dat
        try (TextLineReader reader = new TextLineReader(TestUtils.getResource("optimisation", "netlib", "NETLIB.dat"))) {

            reader.forEach(line -> {

                try (InputStream input = TestUtils.getResource("optimisation", "netlib", line + ".SIF")) {

                    ExpressionsBasedModel model = ExpressionsBasedModel.parse(input, FileFormat.MPS);

                    ExpressionsBasedModel.Description description = model.describe();

                    if (description.nbVariables >= MIN_NB_VARS && description.nbVariables <= MAX_NB_VARS && description.countConstraints() <= MAX_NB_VARS) {
                        for (String solver : SOLVERS) {
                            WORK.add(new ModelSolverPair(line, solver));
                        }
                    }

                } catch (IOException cause) {
                    BasicLogger.debug("Problem with model {}!", line);
                    throw new RuntimeException(cause);
                }

            });

        } catch (IOException cause) {
            BasicLogger.debug("Problem reading list of models!");
            throw new RuntimeException(cause);
        }
    }

    public static void main(final String[] args) {

        Configuration configuration = new Configuration();

        configuration.pathPrefix = "/optimisation/netlib/";

        configuration.refeenceSolver = null;

        AbstractBenchmark.doBenchmark(WORK, configuration);
    }

}
