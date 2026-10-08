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
 * Used for published MIPLIB results. The 4 included solvers are:
 * <ul>
 * <li>ojAlgo
 * <li>The best Java (Open Source) alternative: SSC-LP
 * <li>The best Open Source (Native) alternative: SCIP
 * <li>One more: HiGHS
 * </ul>
 * The models are the {@link AbstractMIPLIB#EASY_SET}.
 * <p>
 * Published results are always produced with parallelism 1 - a single worker. Anything more is only to get
 * results faster.
 */
public final class BenchmarkMIPLIB extends AbstractMIPLIB {

    public static void main(final String[] args) {

        Configuration configuration = AbstractMIPLIB.newConfiguration(Contender.OJALGO_MIP, Contender.SSCLP, Contender.SCIP, Contender.HIGHS);

        configuration.models.retainAll(EASY_SET);

        configuration.parallelism = Parallelism.FOUR;
        configuration.maxIterations = 3;

        AbstractBenchmark.doBenchmark(configuration);
    }

}
