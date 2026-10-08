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
package org.ojalgo.benchmark.convex.marosmeszaros;

import org.ojalgo.benchmark.AbstractBenchmark;
import org.ojalgo.benchmark.Contender;
import org.ojalgo.benchmark.convex.marosmeszaros.MarosMeszarosModels.ModelInfo;
import org.ojalgo.concurrent.Parallelism;

/**
 * The native QP solvers against each other - which is the best solver over all. ojAlgo is not included.
 */
public final class MarosMeszarosNativeSolvers extends AbstractMarosMeszaros {

    public static void main(final String[] args) {

        Configuration configuration = AbstractMarosMeszaros.newConfiguration(Contender.CLARABEL, Contender.OSQP, Contender.HIGHS, Contender.SCIP,
                Contender.CPLEX, Contender.GUROBI, Contender.COPT, Contender.XPRESS, Contender.MOSEK);

        AbstractMarosMeszaros.filter(configuration, ModelInfo::isPureQP);

        configuration.parallelism = Parallelism.EIGHT;

        AbstractBenchmark.doBenchmark(configuration);
    }

}
