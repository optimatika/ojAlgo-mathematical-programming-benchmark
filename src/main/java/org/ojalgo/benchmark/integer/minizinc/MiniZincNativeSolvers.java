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
package org.ojalgo.benchmark.integer.minizinc;

import org.ojalgo.benchmark.AbstractBenchmark;
import org.ojalgo.benchmark.Contender;

/**
 * The native solvers against each other - which models each of them can solve. ojAlgo is not included, and
 * neither is CPLEX: the models go up to 10k variables and constraints, and its Community Edition stops at 1k.
 * <p>
 * The results are what an easy set, one for all categories, is to be decided from - as the MIPLIB easy set
 * is decided from those of {@code MIPLIBNativeSolvers}.
 * <p>
 * The arguments are the categories to run, such as {@code scheduling} - all if there are none - so that the
 * categories can be run one at a time, each with output files of its own.
 */
public final class MiniZincNativeSolvers extends AbstractMiniZinc {

    public static void main(final String[] args) {

        Configuration configuration = AbstractMiniZinc.newConfiguration(Contender.SCIP, Contender.HIGHS, Contender.CPSAT);

        AbstractMiniZinc.retainCategories(configuration, args);

        // configuration.maxIterations = 1;

        // configuration.parallelism = Parallelism.ONE;
        // configuration.parallelism = Parallelism.TWO;
        // configuration.parallelism = Parallelism.THREE;

        AbstractBenchmark.doBenchmark(configuration);
    }

}
