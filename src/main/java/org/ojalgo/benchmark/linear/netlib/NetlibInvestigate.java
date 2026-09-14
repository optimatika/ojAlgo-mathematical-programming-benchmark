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

import java.util.Set;

import org.ojalgo.concurrent.Parallelism;

public final class NetlibInvestigate extends AbstractNetlib {

    public static void main(final String[] args) {

        Configuration configuration = new Configuration(Contender.HIGHS, Contender.OJALGO_LP_DUAL_SPARSE, Contender.OJALGO_LP_DUAL_DENSE);

        configuration.maxProbSize = 11_000;
        configuration.pathPrefix = "/optimisation/netlib/";
        configuration.refeenceSolver = null;
        configuration.parallelism = Parallelism.ONE;
        configuration.investigate = Set.of("25FV47", "BANDM", "BNL1", "BNL2", "CRE-A", "CYCLE", "CZPROB", "D2Q06C", "DEGEN3", "FIT2D", "FORPLAN", "GANGES",
                "GREENBEA", "GREENBEB", "MAROS", "MAROS-R7", "NESM", "PDS-02", "PEROLD", "PILOT", "PILOT-JA", "PILOT-WE", "PILOT4", "PILOT87", "PILOTNOV",
                "QAP12", "QAP8", "SCSD8", "TRUSS", "WOOD1P", "WOODW", "AGG2", "GROW22", "SCTAP1", "STAIR", "ISRAEL");

        AbstractNetlib.loadExpectedValues(configuration);

        AbstractNetlib.doBenchmark(configuration);
    }

}
