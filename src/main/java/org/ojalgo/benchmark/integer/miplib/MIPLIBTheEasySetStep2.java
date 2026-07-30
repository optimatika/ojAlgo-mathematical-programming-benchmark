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

import java.util.Set;

import org.ojalgo.concurrent.Parallelism;

/**
 * A subset of the 94 MIPLIB models in ojAlgo's "easy set" that have caused problems.
 */
public final class MIPLIBTheEasySetStep2 extends AbstractMIPLIB {

    public static final Set<String> MODELS = Set.of("blend2", "bm23", "lseu", "misc02", "misc03", "opt1217", "enigma", "p0291", "22433", "p0548", "pk1",
            "neos-3610040-iskar", "neos-3610173-itata", "sentoy");

    public static void main(final String[] args) {

        Configuration configuration = new Configuration(Contender.OJALGO_MIP, Contender.SCIP, Contender.HIGHS, Contender.CPLEX);

        configuration.investigate = MODELS;

        configuration.pathPrefix = "/optimisation/MIPLIB/";
        configuration.pathSuffix = ".mps";
        configuration.refeenceSolver = Contender.CPLEX;
        configuration.parallelism = Parallelism.TWO;
        configuration.maxProbSize = 1_000;

        AbstractMIPLIB.doBenchmark(configuration);
    }

}
