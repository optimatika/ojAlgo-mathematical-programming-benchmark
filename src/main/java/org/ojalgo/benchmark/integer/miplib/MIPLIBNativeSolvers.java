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
package org.ojalgo.benchmark.integer.miplib;

import org.ojalgo.concurrent.Parallelism;

/**
 * HiGHS against SCIP on the larger MIPLIB models - the ones the "easy set" leaves out.
 * <p>
 * Whatever HiGHS and SCIP are installed on this machine, through the plain integrations. No library paths, no
 * build variants: those questions belong to the Docker image comparison, which is a separate exercise.
 * <p>
 * Models are selected by size rather than by name - every model in {@code MIPLIB.dat} with a variable count
 * in {@code [MIN_SIZE, MAX_SIZE]}. {@code MIPLIBTheEasySet} covers everything below 1k variables, and the
 * available models run out somewhere below 20k, so that range is the whole of what is left.
 * <p>
 * One solve per pair, so the measure is how many models each solver gets through. The reported times are a
 * single cold sample each - fine for spotting order-of-magnitude differences, not for close comparisons.
 */
public final class MIPLIBNativeSolvers extends AbstractMIPLIB {

    /**
     * Available models by variable count: 205 below 1k, 204 in 1k-5k, 74 in 5k-20k, none above.
     */
    private static final int MIN_SIZE = 1_000;
    private static final int MAX_SIZE = 2_000;

    /**
     * Wall clock is dominated by the pairs that never finish - roughly {@code failures x TIMEOUT / workers} -
     * and on models this size a good share will be failures. That product, not the solving, is what decides
     * how long a run takes.
     */
    private static final long TIMEOUT = 1_000L * 60L * 3L;

    public static void main(final String[] args) {

        Configuration configuration = new Configuration(Contender.HIGHS, Contender.SCIP);

        configuration.pathPrefix = "/optimisation/MIPLIB/";
        configuration.pathSuffix = ".mps";
        configuration.refeenceSolver = null;

        // Empty means every model in MIPLIB.dat, then filtered by size.
        configuration.minProbSize = MIN_SIZE;
        configuration.maxProbSize = MAX_SIZE;

        configuration.maxIterations = 1;
        configuration.maxWaitTime = TIMEOUT;
        configuration.parallelism = Parallelism.TWO;

        configuration.outputPath = "./src/main/resources/miplib_native_" + MIN_SIZE + "_" + MAX_SIZE + "_output.csv";

        AbstractMIPLIB.doBenchmark(configuration);
    }

}
