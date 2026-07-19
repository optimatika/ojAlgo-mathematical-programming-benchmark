/*
 * Copyright 1997-2025 Optimatika
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

import org.ojalgo.benchmark.qplib.QPLIBModels.ModelInfo;
import org.ojalgo.concurrent.Parallelism;

public final class QPLIBBenchmark extends AbstractQPLIB {

    public static void main(final String[] args) {

        Configuration configuration = new Configuration(Contender.OJALGO_QP, Contender.CLARABEL4J, Contender.HIGHS);

        configuration.maxProbSize = 10_000;
        configuration.pathPrefix = "/optimisation/QPLIB/";
        configuration.pathSuffix = ".lp";
        configuration.refeenceSolver = null;
        configuration.parallelism = Parallelism.EIGHT;

        AbstractQPLIB.loadExpectedValues(configuration);

        AbstractQPLIB.doBenchmark(configuration, info -> info.isContinuous() && info.isConvex() && info.isLinearlyConstrained() && info.isQP());
    }

}
