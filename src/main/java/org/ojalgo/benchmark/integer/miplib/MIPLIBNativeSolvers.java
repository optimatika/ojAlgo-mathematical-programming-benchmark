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

import org.ojalgo.benchmark.AbstractBenchmark;
import org.ojalgo.benchmark.Contender;
import org.ojalgo.concurrent.Parallelism;

/**
 * The native solvers against each other - which models each of them can solve. ojAlgo is not included.
 * <p>
 * Whatever is installed on this machine, through the plain integrations. No library paths, no build variants:
 * those questions belong to the Docker image comparison - or set {@link Configuration#libraries} and
 * {@link Configuration#label}.
 * <p>
 * Models are selected by size rather than by name - every model in {@code MIPLIB.dat} with a variable count
 * in {@code [MIN_SIZE, MAX_SIZE]}. The available models run out somewhere below 20k.
 * <p>
 * This is what defines the {@link AbstractMIPLIB#EASY_SET}: run it on the models up to 1k, and the easy set
 * is the models that all of COPT, CPLEX, HiGHS, SCIP and Xpress solve.
 * <p>
 * One solve per pair, so the measure is how many models each solver gets through. The reported times are a
 * single sample each - fine for spotting order-of-magnitude differences, not for close comparisons.
 */
public final class MIPLIBNativeSolvers extends AbstractMIPLIB {

    public static void main(final String[] args) {

        Configuration configuration = AbstractMIPLIB.newConfiguration(Contender.CPLEX, Contender.SCIP, Contender.HIGHS, Contender.CPSAT, Contender.XPRESS,
                Contender.COPT, Contender.MOSEK);

        configuration.parallelism = Parallelism.EIGHT;

        AbstractBenchmark.doBenchmark(configuration);
    }

}
