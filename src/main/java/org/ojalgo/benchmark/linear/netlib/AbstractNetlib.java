package org.ojalgo.benchmark.linear.netlib;

import java.io.IOException;
import java.math.BigDecimal;

import org.ojalgo.benchmark.AbstractBenchmark;
import org.ojalgo.netio.BasicLogger;
import org.ojalgo.netio.TextLineReader;

abstract class AbstractNetlib extends AbstractBenchmark {

    private static final String RESOURCE_DIR = "optimisation/netlib/";

    /**
     * Optimal objective values for every model the {@code optimisation-models} artifact ships. See the
     * header of {@code NETLIB.solu} for where each value comes from - most are the published ones, nine had
     * to be computed because the Netlib README leaves them out.
     */
    private static void loadExpectedValues(final Configuration configuration) {

        try (TextLineReader reader = new TextLineReader(Thread.currentThread().getContextClassLoader().getResourceAsStream(RESOURCE_DIR + "NETLIB.solu"))) {

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

    /**
     * All models listed in {@code NETLIB.dat} - the large ones are listed but not shipped, and are left out
     * when the benchmark starts.
     */
    static Configuration newConfiguration(final String... solvers) {

        Configuration configuration = new Configuration(solvers);

        configuration.pathPrefix = "/" + RESOURCE_DIR;
        configuration.pathSuffix = ".SIF";

        configuration.models.addAll(AbstractBenchmark.readIndex(RESOURCE_DIR + "NETLIB.dat"));

        AbstractNetlib.loadExpectedValues(configuration);

        return configuration;
    }

}
