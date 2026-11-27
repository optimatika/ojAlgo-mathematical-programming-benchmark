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
 * Different from {@link BenchmarkMIPLIB} in that it:
 * <ul>
 * <li>Add a bit of parallelism to cut execution time in half.
 * <li>Replaced the other slow pure Java solver with a fast native code solver (that we can't use in published
 * results).
 * </ul>
 */
public final class EvaluateMIPLIB extends AbstractMIPLIB {

    public static void main(final String[] args) {

        Configuration configuration = AbstractMIPLIB.newConfiguration(Contender.OJALGO_MIP, Contender.CPLEX, Contender.SCIP, Contender.HIGHS);

        configuration.models.retainAll(EASY_SET);

        configuration.parallelism = Parallelism.TWO;

        AbstractBenchmark.doBenchmark(configuration);
    }

}
