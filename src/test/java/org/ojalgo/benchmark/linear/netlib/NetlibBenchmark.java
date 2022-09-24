/*
 * Copyright 1997-2022 Optimatika
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

import java.util.HashSet;
import java.util.Set;

import org.ojalgo.benchmark.AbstractBenchmark;

public final class NetlibBenchmark extends AbstractBenchmark {

    static final String[] MODELS = { "VTP-BASE", "TUFF", "STOCFOR1", "STAIR", "SHARE2B", "SHARE1B", "SEBA", "SCTAP1", "SCSD1", "SCORPION", "SCFXM2", "SCFXM1",
            "SCAGR7", "SCAGR25", "SC50B", "SC50A", "SC205", "SC105", "RECIPELP", "PILOT4", "LOTFI", "KB2", "ISRAEL", "GROW7", "GROW22", "GROW15", "FORPLAN",
            "FINNIS", "FFFFF800", "ETAMACRO", "E226", "DEGEN2", "CAPRI", "BRANDY", "BORE3D", "BOEING2", "BOEING1", "BLEND", "BEACONFD", "BANDM", "AGG3", "AGG2",
            "AGG", "AFIRO", "ADLITTLE" };

    static final String[] SOLVERS = { Contender.CPLEX, Contender.OJALGO, Contender.ORTOOLS };

    static final Set<ModelSolverPair> WORK = new HashSet<>();

    static {

        for (String mod : MODELS) {
            for (String sol : SOLVERS) {
                WORK.add(new ModelSolverPair(mod, sol));
            }
        }
    }

    public static void main(final String[] args) {

        Configuration configuration = new Configuration();

        configuration.pathPrefix = "/optimisation/netlib/";

        AbstractBenchmark.doBenchmark(WORK, configuration);
    }

}
