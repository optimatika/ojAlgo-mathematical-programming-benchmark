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

import java.util.HashSet;
import java.util.Set;

import org.ojalgo.benchmark.AbstractBenchmark;
import org.ojalgo.benchmark.convex.marosmeszaros.MarosMeszarosModels.ModelInfo;
import org.ojalgo.concurrent.Parallelism;

/**
 * Used for published MarosMeszaros results. The 4 included solvers are:
 * <ul>
 * <li>ojAlgo
 * <li>The best Java (Open Source) alternative: SSC-LP
 * <li>The best Open Source (Native) alternative: SCIP
 * <li>One more...
 * </ul>
 */
public final class BenchmarkMarosMeszaros extends AbstractMarosMeszaros {

    static final String[] SOLVERS = { Contender.OJALGO_QP, Contender.CLARABEL, Contender.GUROBI, Contender.COPT };

    static final Set<ModelSolverPair> WORK = new HashSet<>();

    private static int MAX_DIM = 1_000;
    private static int MIN_DIM = 1;

    static {

        for (String mod : ALL_MODELS) {
            ModelInfo modelInfo = MarosMeszarosModels.getModelInfo(mod);

            if (modelInfo.isPureQP() && modelInfo.M <= MAX_DIM && modelInfo.N <= MAX_DIM && modelInfo.N >= MIN_DIM) {
                // if (modelInfo.isPureQP() && modelInfo.isSmall()) {
                for (String sol : SOLVERS) {
                    WORK.add(new ModelSolverPair(mod, sol));
                }
            }
        }
    }

    public static void main(final String[] args) {

        Configuration configuration = new Configuration();

        configuration.pathPrefix = "/optimisation/marosmeszaros/";
        configuration.refeenceSolver = null;
        configuration.parallelism = Parallelism.TWO;
        configuration.maxIterations = 3;

        // Keyed by the names used here - the README's are different (no underscores)
        for (String model : ALL_MODELS) {
            configuration.values.put(model, MarosMeszarosModels.getModelInfo(model).OPT);
        }

        AbstractBenchmark.doBenchmark(WORK, configuration);
    }

}
