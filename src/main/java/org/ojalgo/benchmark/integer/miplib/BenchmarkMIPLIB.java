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
 * Used for published MIPLIB results. The 4 included solvers are:
 * <ul>
 * <li>ojAlgo
 * <li>The best Java (Open Source) alternative: SSC-LP
 * <li>The best Open Source (Native) alternative: SCIP
 * <li>One more...
 * </ul>
 */
public final class BenchmarkMIPLIB extends AbstractMIPLIB {

    public static void main(final String[] args) {

        Configuration configuration = new Configuration(Contender.GUROBI, Contender.CPLEX, Contender.XPRESS, Contender.COPT, Contender.SCIP, Contender.HIGHS,
                Contender.MOSEK);

        configuration.maxProbSize = 1_000;
        configuration.pathPrefix = "/optimisation/MIPLIB/";
        configuration.pathSuffix = ".mps";
        configuration.refeenceSolver = null;
        configuration.parallelism = Parallelism.FOUR;
        configuration.maxIterations = 3;

        AbstractMIPLIB.doBenchmark(configuration);
    }

}
