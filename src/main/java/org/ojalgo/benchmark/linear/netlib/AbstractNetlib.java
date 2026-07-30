package org.ojalgo.benchmark.linear.netlib;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import org.ojalgo.benchmark.AbstractBenchmark;
import org.ojalgo.netio.BasicLogger;
import org.ojalgo.netio.TextLineReader;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.ExpressionsBasedModel.FileFormat;

abstract class AbstractNetlib extends AbstractBenchmark {

    private static Set<ModelSolverPair> createWorkSet(final Configuration filter) {

        Set<ModelSolverPair> retVal = new HashSet<>();

        try (TextLineReader reader = new TextLineReader(Thread.currentThread().getContextClassLoader().getResourceAsStream("optimisation/netlib/NETLIB.dat"))) {

            reader.forEach(line -> {

                try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream("optimisation/netlib/" + line + ".SIF")) {

                    if (input == null) {
                        // NETLIB.dat lists models the artifact doesn't ship - the large ones.
                        return;
                    }

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

        Set<ModelSolverPair> allWork = AbstractNetlib.createWorkSet(configuration);

        AbstractBenchmark.doBenchmark(allWork, configuration);
    }

    /**
     * Optimal objective values for every model the {@code optimisation-models} artifact ships. See the
     * header of {@code NETLIB.solu} for where each value comes from - most are the published ones, nine had
     * to be computed because the Netlib README leaves them out.
     */
    static void loadExpectedValues(final Configuration configuration) {

        try (TextLineReader reader = new TextLineReader(
                Thread.currentThread().getContextClassLoader().getResourceAsStream("optimisation/netlib/NETLIB.solu"))) {

            reader.forEach(line -> {
                if (!line.startsWith("#")) {
                    String[] fields = line.split("\\s+");
                    if (fields.length >= 2) {
                        configuration.values.put(fields[0], new BigDecimal(fields[1]));
                    }
                }
            });

        } catch (IOException cause) {
            BasicLogger.debug("Problem reading expected values!");
            throw new RuntimeException(cause);
        }
    }

}
