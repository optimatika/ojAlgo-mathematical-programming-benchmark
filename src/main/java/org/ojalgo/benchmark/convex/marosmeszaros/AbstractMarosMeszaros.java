package org.ojalgo.benchmark.convex.marosmeszaros;

import java.util.function.Predicate;

import org.ojalgo.benchmark.AbstractBenchmark;
import org.ojalgo.benchmark.convex.marosmeszaros.MarosMeszarosModels.ModelInfo;

abstract class AbstractMarosMeszaros extends AbstractBenchmark {

    private static final String RESOURCE_DIR = "optimisation/marosmeszaros/";

    /**
     * Keeps only the models whose metadata, from {@code 00README.CSV}, satisfies the predicate. Models
     * without metadata are dropped.
     */
    static void filter(final Configuration configuration, final Predicate<ModelInfo> predicate) {
        configuration.models.removeIf(model -> {
            ModelInfo info = MarosMeszarosModels.getModelInfo(model);
            return info == null || !predicate.test(info);
        });
    }

    /**
     * All models listed in {@code filenames.txt} - the larger ones are listed but not shipped, and are left
     * out when the benchmark starts - with the optimal values from {@code 00README.CSV} as expected values.
     */
    static Configuration newConfiguration(final String... solvers) {

        Configuration configuration = new Configuration(solvers);

        configuration.pathPrefix = "/" + RESOURCE_DIR;
        configuration.pathSuffix = ".SIF";

        for (String fileName : AbstractBenchmark.readIndex(RESOURCE_DIR + "filenames.txt")) {

            if (!fileName.endsWith(".SIF")) {
                continue;
            }

            String model = fileName.substring(0, fileName.length() - 4);

            configuration.models.add(model);
            // Keyed by the names used here - the README's are different (no underscores)
            ModelInfo info = MarosMeszarosModels.getModelInfo(model);
            if (info != null && info.OPT != null) {
                configuration.values.put(model, info.OPT);
            }
        }

        return configuration;
    }

}
