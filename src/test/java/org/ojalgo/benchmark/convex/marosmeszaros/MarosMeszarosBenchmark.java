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
package org.ojalgo.benchmark.convex.marosmeszaros;

import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import org.ojalgo.benchmark.AbstractBenchmark;
import org.ojalgo.concurrent.Parallelism;
import org.ojalgo.optimisation.convex.CuteMarosMeszarosCase;
import org.ojalgo.optimisation.convex.CuteMarosMeszarosCase.ModelInfo;

public final class MarosMeszarosBenchmark extends AbstractBenchmark {

    static final String[] ONE_MODEL = new String[] { "MOSARQP2" };

    static final String[] SOME_MODELS = new String[] { "CVXQP1_M", "CVXQP1_S", "CVXQP2_M", "CVXQP2_S", "CVXQP3_M", "CVXQP3_S", "DPKLO1", "DUAL1", "DUAL2",
            "DUAL3", "DUAL4", "DUALC1", "DUALC2", "DUALC5", "DUALC8", "GENHS28", "GOULDQP2", "GOULDQP3", "HS118", "HS21", "HS268", "HS35", "HS35MOD", "HS51",
            "HS52", "HS53", "HS76", "KSIP", "LOTSCHD", "MOSARQP2", "PRIMAL1", "PRIMAL2", "PRIMAL3", "PRIMALC1", "PRIMALC2", "PRIMALC5", "PRIMALC8", "QADLITTL",
            "QAFIRO", "QBANDM", "QBEACONF", "QBORE3D", "QBRANDY", "QCAPRI", "QE226", "QETAMACR", "QFFFFF80", "QFORPLAN", "QGROW15", "QGROW22", "QGROW7",
            "QISRAEL", "QPCBLEND", "QPCBOEI1", "QPCBOEI2", "QPCSTAIR", "QPTEST", "QRECIPE", "QSC205", "QSCAGR25", "QSCAGR7", "QSCFXM1", "QSCFXM2", "QSCORPIO",
            "QSCSD1", "QSCTAP1", "QSEBA", "QSHARE1B", "QSHARE2B", "QSTAIR", "S268", "TAME", "ZECEVIC2" };

    static final String[] ALL_MODELS = new String[] { "AUG2D", "AUG2DC", "AUG2DCQP", "AUG2DQP", "AUG3D", "AUG3DC", "AUG3DCQP", "AUG3DQP", "BOYD1", "BOYD2",
            "CONT-050", "CONT-100", "CONT-101", "CONT-200", "CONT-201", "CONT-300", "CVXQP1_L", "CVXQP1_M", "CVXQP1_S", "CVXQP2_L", "CVXQP2_M", "CVXQP2_S",
            "CVXQP3_L", "CVXQP3_M", "CVXQP3_S", "DPKLO1", "DTOC3", "DUAL1", "DUAL2", "DUAL3", "DUAL4", "DUALC1", "DUALC2", "DUALC5", "DUALC8", "EXDATA",
            "GENHS28", "GOULDQP2", "GOULDQP3", "HS21", "HS35", "HS35MOD", "HS51", "HS52", "HS53", "HS76", "HS118", "HS268", "HUES-MOD", "HUESTIS", "KSIP",
            "LASER", "LISWET1", "LISWET2", "LISWET3", "LISWET4", "LISWET5", "LISWET6", "LISWET7", "LISWET8", "LISWET9", "LISWET10", "LISWET11", "LISWET12",
            "LOTSCHD", "MOSARQP1", "MOSARQP2", "POWELL20", "PRIMAL1", "PRIMAL2", "PRIMAL3", "PRIMAL4", "PRIMALC1", "PRIMALC2", "PRIMALC5", "PRIMALC8",
            "Q25FV47", "QADLITTL", "QAFIRO", "QBANDM", "QBEACONF", "QBORE3D", "QBRANDY", "QCAPRI", "QE226", "QETAMACR", "QFFFFF80", "QFORPLAN", "QGFRDXPN",
            "QGROW7", "QGROW15", "QGROW22", "QISRAEL", "QPCBLEND", "QPCBOEI1", "QPCBOEI2", "QPCSTAIR", "QPILOTNO", "QPTEST", "QRECIPE", "QSC205", "QSCAGR7",
            "QSCAGR25", "QSCFXM1", "QSCFXM2", "QSCFXM3", "QSCORPIO", "QSCRS8", "QSCSD1", "QSCSD6", "QSCSD8", "QSCTAP1", "QSCTAP2", "QSCTAP3", "QSEBA",
            "QSHARE1B", "QSHARE2B", "QSHELL", "QSHIP04L", "QSHIP04S", "QSHIP08L", "QSHIP08S", "QSHIP12L", "QSHIP12S", "QSIERRA", "QSTAIR", "QSTANDAT", "S268",
            "STADAT1", "STADAT2", "STADAT3", "STCQP1", "STCQP2", "TAME", "UBH1", "VALUES", "YAO", "ZECEVIC2" };

    static final String[] SOLVERS = new String[] { Contender.OJALGO, Contender.CLARABEL4J, Contender.HIPPARCHUS, Contender.CPLEX };

    //    static final String[] SOLVERS = new String[] { Contender.OJALGO_DENSE_EXPERIMENTAL, Contender.OJALGO_DENSE_STABLE, Contender.OJALGO_SPARSE_EXPERIMENTAL,
    //            Contender.OJALGO_SPARSE_STABLE };

    //    static final String[] SOLVERS = new String[] { Contender.OJALGO_QP_CG_ID, Contender.OJALGO_QP_CG_JACOBI, Contender.OJALGO_QP_CG_SSORP,
    //            Contender.OJALGO_QP_MINRES_ID, Contender.OJALGO_QP_MINRES_JACOBI, Contender.OJALGO_QP_MINRES_SSORP, Contender.OJALGO_QP_QMR_ID,
    //            Contender.OJALGO_QP_QMR_JACOBI, Contender.OJALGO_QP_QMR_SSORP };

    static final Set<ModelSolverPair> WORK = new HashSet<>();

    private static int MAX_DIM = 1_000;
    private static int MIN_DIM = 1;

    static {

        for (String mod : ALL_MODELS) {
            ModelInfo modelInfo = CuteMarosMeszarosCase.getModelInfo(mod);

            // if (modelInfo.isPureQP() && modelInfo.M <= MAX_DIM && modelInfo.N <= MAX_DIM && modelInfo.N >= MIN_DIM) {
            if (modelInfo.isPureQP() && modelInfo.isSmall()) {
                for (String sol : SOLVERS) {
                    WORK.add(new ModelSolverPair(mod, sol));
                }
            }
        }
    }

    public static void main(final String[] args) {

        Configuration configuration = new Configuration();

        configuration.pathPrefix = "/optimisation/marosmeszaros/";
        configuration.refeenceSolver = Contender.CPLEX;
        configuration.parallelism = Parallelism.ONE;

        for (Entry<String, ModelInfo> entry : CuteMarosMeszarosCase.getModelInfo().entrySet()) {
            configuration.values.put(entry.getKey(), entry.getValue().OPT);
        }

        AbstractBenchmark.doBenchmark(WORK, configuration);
    }

}
