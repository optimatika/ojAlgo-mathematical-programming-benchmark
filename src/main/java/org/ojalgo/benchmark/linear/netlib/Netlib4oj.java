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
package org.ojalgo.benchmark.linear.netlib;

import org.ojalgo.concurrent.Parallelism;

public final class Netlib4oj extends AbstractNetlib {

    public static void main(final String[] args) {

        Configuration configuration = new Configuration(Contender.OJALGO_LP_PRIM_SPARSE, Contender.OJALGO_LP_PRIM_DENSE, Contender.OJALGO_LP_DUAL_SPARSE,
                Contender.OJALGO_LP_DUAL_DENSE);

        configuration.maxProbSize = 10_000;
        configuration.pathPrefix = "/optimisation/netlib/";
        configuration.refeenceSolver = null;
        configuration.parallelism = Parallelism.FOUR;

        AbstractNetlib.loadExpectedValues(configuration);

        AbstractNetlib.doBenchmark(configuration);
    }

}
