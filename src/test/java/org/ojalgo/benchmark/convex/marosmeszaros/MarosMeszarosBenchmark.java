/*
 * Copyright 1997-2021 Optimatika
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

import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import org.ojalgo.benchmark.AbstractBenchmark;
import org.ojalgo.optimisation.convex.CuteMarosMeszarosCase;
import org.ojalgo.optimisation.convex.CuteMarosMeszarosCase.ModelInfo;

public final class MarosMeszarosBenchmark extends AbstractBenchmark {

    static final String[] MODELS = new String[] { "CVXQP1_M", "CVXQP1_S", "CVXQP2_M", "CVXQP2_S", "CVXQP3_M", "CVXQP3_S", "DPKLO1", "DUAL1", "DUAL2", "DUAL3",
            "DUAL4", "DUALC1", "DUALC2", "DUALC5", "DUALC8", "GENHS28", "GOULDQP2", "GOULDQP3", "HS118", "HS21", "HS268", "HS35", "HS35MOD", "HS51", "HS52",
            "HS53", "HS76", "KSIP", "LOTSCHD", "MOSARQP2", "PRIMAL1", "PRIMAL2", "PRIMAL3", "PRIMALC1", "PRIMALC2", "PRIMALC5", "PRIMALC8", "QADLITTL",
            "QAFIRO", "QBANDM", "QBEACONF", "QBORE3D", "QBRANDY", "QCAPRI", "QE226", "QETAMACR", "QFFFFF80", "QFORPLAN", "QGROW15", "QGROW22", "QGROW7",
            "QISRAEL", "QPCBLEND", "QPCBOEI1", "QPCBOEI2", "QPCSTAIR", "QPTEST", "QRECIPE", "QSC205", "QSCAGR25", "QSCAGR7", "QSCFXM1", "QSCFXM2", "QSCORPIO",
            "QSCSD1", "QSCTAP1", "QSEBA", "QSHARE1B", "QSHARE2B", "QSTAIR", "S268", "TAME", "ZECEVIC2" };

    static final String[] SOLVERS = new String[] { Contender.CPLEX, Contender.OJALGO, Contender.J_OPTIMIZER };

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

        configuration.pathPrefix = "/optimisation/marosmeszaros/";
        configuration.maxWaitTime = 60_000L;

        for (Entry<String, ModelInfo> entry : CuteMarosMeszarosCase.MODEL_INFO.entrySet()) {
            configuration.values.put(entry.getKey(), entry.getValue().OPT);
        }

        AbstractBenchmark.doBenchmark(WORK, configuration);
    }

}
