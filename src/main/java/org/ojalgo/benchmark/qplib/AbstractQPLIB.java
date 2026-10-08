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

import java.util.Map.Entry;
import java.util.function.Predicate;

import org.ojalgo.benchmark.AbstractBenchmark;
import org.ojalgo.benchmark.qplib.QPLIBModels.ModelInfo;

abstract class AbstractQPLIB extends AbstractBenchmark {

    private static final String RESOURCE_DIR = "optimisation/QPLIB/";

    /**
     * Keeps only the models whose metadata, from {@code instancedata.csv}, satisfies the predicate. Models
     * without metadata are dropped.
     */
    static void filter(final Configuration configuration, final Predicate<ModelInfo> predicate) {
        configuration.models.removeIf(model -> {
            ModelInfo info = QPLIBModels.getModelInfo(model);
            return info == null || !predicate.test(info);
        });
    }

    /**
     * All models listed in {@code QPLIB.dat}, with the objective values from {@code instancedata.csv} as
     * expected values - where there is one.
     */
    static Configuration newConfiguration(final String... solvers) {

        Configuration configuration = new Configuration(solvers);

        configuration.pathPrefix = "/" + RESOURCE_DIR;
        configuration.pathSuffix = ".lp";

        configuration.models.addAll(AbstractBenchmark.readIndex(RESOURCE_DIR + "QPLIB.dat"));

        for (Entry<String, ModelInfo> entry : QPLIBModels.getModelInfo().entrySet()) {
            if (entry.getValue().solobjvalue != null) {
                configuration.values.put(entry.getKey(), entry.getValue().solobjvalue);
            }
        }

        return configuration;
    }

}
