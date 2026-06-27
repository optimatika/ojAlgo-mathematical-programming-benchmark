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

abstract class AbstractNetlibBenchmark extends AbstractBenchmark {

    private static Set<ModelSolverPair> createWorkSet(final Configuration filter) {

        Set<ModelSolverPair> retVal = new HashSet<>();

        try (TextLineReader reader = new TextLineReader(TestUtils.getResource("optimisation", "netlib", "NETLIB.dat"))) {

            reader.forEach(line -> {

                try (InputStream input = TestUtils.getResource("optimisation", "netlib", line + ".SIF")) {

                    ExpressionsBasedModel model = ExpressionsBasedModel.parse(input, FileFormat.MPS);

                    ExpressionsBasedModel.Description description = model.describe();

                    if ((filter.investigate.isEmpty() || filter.investigate.contains(line)) && description.nbVariables >= filter.minProbSize
                            && description.nbVariables <= filter.maxProbSize && description.countConstraints() <= filter.maxProbSize) {
                        for (String solver : filter.solvers) {
                            retVal.add(new ModelSolverPair(line, solver));
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

        return retVal;
    }

    static void doBenchmark(final Configuration configuration) {

        Set<ModelSolverPair> allWork = AbstractNetlibBenchmark.createWorkSet(configuration);

        AbstractBenchmark.doBenchmark(allWork, configuration);
    }

}
