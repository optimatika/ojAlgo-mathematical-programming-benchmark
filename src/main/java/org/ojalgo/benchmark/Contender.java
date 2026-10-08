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
package org.ojalgo.benchmark;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.ojalgo.matrix.task.iterative.ConjugateGradientSolver;
import org.ojalgo.matrix.task.iterative.JacobiPreconditioner;
import org.ojalgo.matrix.task.iterative.MINRESSolver;
import org.ojalgo.matrix.task.iterative.Preconditioner;
import org.ojalgo.matrix.task.iterative.QMRSolver;
import org.ojalgo.matrix.task.iterative.SSORPreconditioner;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.convex.ConvexSolver;
import org.ojalgo.optimisation.convex.ConvexSolver.Algorithm;
import org.ojalgo.optimisation.integer.IntegerSolver;
import org.ojalgo.optimisation.linear.LinearSolver;
import org.ojalgo.optimisation.solver.acm.SolverACM;
import org.ojalgo.optimisation.solver.choco.SolverChoco;
import org.ojalgo.optimisation.solver.clarabel.SolverClarabel;
import org.ojalgo.optimisation.solver.copt.SolverCOPT;
import org.ojalgo.optimisation.solver.cplex.SolverCPLEX;
import org.ojalgo.optimisation.solver.cpsat.SolverCPSAT;
import org.ojalgo.optimisation.solver.gurobi.SolverGurobi;
import org.ojalgo.optimisation.solver.highs.SolverHiGHS;
import org.ojalgo.optimisation.solver.hipparchus.SolverHipparchus;
import org.ojalgo.optimisation.solver.joptimizer.SolverJOptimizer;
import org.ojalgo.optimisation.solver.mosek.SolverMosek;
import org.ojalgo.optimisation.solver.ortools.SolverORTools;
import org.ojalgo.optimisation.solver.osqp.SolverOSQP;
import org.ojalgo.optimisation.solver.scip.SolverSCIP;
import org.ojalgo.optimisation.solver.ssclp.SolverSSCLP;
import org.ojalgo.optimisation.solver.xpress.SolverXpress;

/**
 * The solvers, and solver configurations, that can be benchmarked - each with a name, and the integration
 * that name stands for. Only the name is passed to the worker JVM, so every configuration has to be
 * registered here, including the ojAlgo variants used by experiments.
 */
public final class Contender {

    public static final String ACM = "ACM";
    public static final String CHOCO = "Choco";
    public static final String CLARABEL = "Clarabel";
    public static final String COPT = "COPT";
    public static final String CPLEX = "CPLEX";
    public static final String CPSAT = "CP-SAT";
    public static final String GUROBI = "Gurobi";
    public static final String HIGHS = "HiGHS";
    public static final String HIPPARCHUS = "Hipparchus";
    public static final String JOPTIMIZER = "JOptimizer";
    public static final String MOSEK = "Mosek";
    public static final String OJALGO_LP = "ojAlgo-LP";
    public static final String OJALGO_LP_DUAL_DENSE = "ojAlgo-LP-dual-D";
    public static final String OJALGO_LP_DUAL_SPARSE = "ojAlgo-LP-dual-S";
    public static final String OJALGO_LP_PRIM_DENSE = "ojAlgo-LP-prim-D";
    public static final String OJALGO_LP_PRIM_SPARSE = "ojAlgo-LP-prim-S";
    public static final String OJALGO_MIP = "ojAlgo-MIP";
    public static final String OJALGO_MIP_DUAL_DENSE = "ojAlgo-MIP-dual-D";
    public static final String OJALGO_MIP_DUAL_SPARSE = "ojAlgo-MIP-dual-S";
    public static final String OJALGO_MIP_PRIM_DENSE = "ojAlgo-MIP-prim-D";
    public static final String OJALGO_MIP_PRIM_SPARSE = "ojAlgo-MIP-prim-S";
    public static final String OJALGO_QP = "ojAlgo-QP";
    public static final String OJALGO_QP_ADMM = "ojAlgo-QP-ADMM";
    public static final String OJALGO_QP_ASET = "ojAlgo-QP-ASET";
    public static final String OJALGO_QP_ASET_NULLSPACE_DENSE = "ojAlgo-QP-ASET-NSP-D";
    public static final String OJALGO_QP_ASET_NULLSPACE_SPARSE = "ojAlgo-QP-ASET-NSP-S";
    public static final String OJALGO_QP_ASET_PLAIN_DENSE = "ojAlgo-QP-ASET-PLN-D";
    public static final String OJALGO_QP_ASET_PLAIN_SPARSE = "ojAlgo-QP-ASET-PLN-S";
    public static final String OJALGO_QP_CG_ID = "ojAlgo-QP-CG-id";
    public static final String OJALGO_QP_CG_JACOBI = "ojAlgo-QP-CG-jacobi";
    public static final String OJALGO_QP_CG_SSORP = "ojAlgo-QP-CG-ssorp";
    public static final String OJALGO_QP_DENSE_EXPERIMENTAL = "ojAlgo-QP-D-exp";
    public static final String OJALGO_QP_DENSE_STABLE = "ojAlgo-QP-D-stbl";
    public static final String OJALGO_QP_MINRES_ID = "ojAlgo-QP-MINRES-id";
    public static final String OJALGO_QP_MINRES_JACOBI = "ojAlgo-QP-MINRES-jacobi";
    public static final String OJALGO_QP_MINRES_SSORP = "ojAlgo-QP-MINRES-ssorp";
    public static final String OJALGO_QP_QMR_ID = "ojAlgo-QP-QMR-id";
    public static final String OJALGO_QP_QMR_JACOBI = "ojAlgo-QP-QMR-jacobi";
    public static final String OJALGO_QP_QMR_SSORP = "ojAlgo-QP-QMR-ssorp";
    public static final String OJALGO_QP_SPARSE_EXPERIMENTAL = "ojAlgo-QP-S-exp";
    public static final String OJALGO_QP_SPARSE_STABLE = "ojAlgo-QP-S-stbl";
    /**
     * Not included in any benchmark by default. It bundles its own libhighs, and in a reused worker JVM that
     * is the one the HiGHS integration would then bind to.
     */
    public static final String ORTOOLS = "OR-Tools";
    public static final String OSQP = "OSQP";
    public static final String SCIP = "SCIP";
    public static final String SSCLP = "SSC-LP";
    public static final String XPRESS = "Xpress";

    /**
     * Suppliers rather than instances so that a solver's classes - and therefore its native libraries - are
     * only loaded when that solver is actually used. OR-Tools in particular bundles its own libhighs, which
     * the HiGHS integration would then bind to instead of the system one.
     */
    static final Map<String, Supplier<ExpressionsBasedModel.Integration<?>>> INTEGRATIONS = new HashMap<>();

    static {

        INTEGRATIONS.put(Contender.ACM, () -> SolverACM.INTEGRATION);
        INTEGRATIONS.put(Contender.CHOCO, () -> SolverChoco.INTEGRATION);
        INTEGRATIONS.put(Contender.HIPPARCHUS, () -> SolverHipparchus.INTEGRATION);
        INTEGRATIONS.put(Contender.CPLEX, () -> SolverCPLEX.INTEGRATION);
        INTEGRATIONS.put(Contender.CPSAT, () -> SolverCPSAT.INTEGRATION);
        INTEGRATIONS.put(Contender.ORTOOLS, () -> SolverORTools.INTEGRATION);
        INTEGRATIONS.put(Contender.GUROBI, () -> SolverGurobi.INTEGRATION);
        INTEGRATIONS.put(Contender.JOPTIMIZER, () -> SolverJOptimizer.INTEGRATION);
        INTEGRATIONS.put(Contender.MOSEK, () -> SolverMosek.INTEGRATION);

        INTEGRATIONS.put(Contender.OJALGO_LP, () -> LinearSolver.INTEGRATION);
        INTEGRATIONS.put(Contender.OJALGO_MIP, () -> IntegerSolver.INTEGRATION);

        INTEGRATIONS.put(Contender.CLARABEL, () -> SolverClarabel.INTEGRATION);
        INTEGRATIONS.put(Contender.HIGHS, () -> SolverHiGHS.INTEGRATION);
        INTEGRATIONS.put(Contender.OSQP, () -> SolverOSQP.INTEGRATION);

        INTEGRATIONS.put(Contender.SCIP, () -> SolverSCIP.INTEGRATION);
        INTEGRATIONS.put(Contender.SSCLP, () -> SolverSSCLP.INTEGRATION);

        INTEGRATIONS.put(Contender.COPT, () -> SolverCOPT.INTEGRATION);
        INTEGRATIONS.put(Contender.XPRESS, () -> SolverXpress.INTEGRATION);

        INTEGRATIONS.put(Contender.OJALGO_LP_DUAL_DENSE, () -> LinearSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.linear().dual();
            opt.sparse = Boolean.FALSE;
        }));
        INTEGRATIONS.put(Contender.OJALGO_LP_DUAL_SPARSE, () -> LinearSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.linear().dual();
            opt.sparse = Boolean.TRUE;
        }));
        INTEGRATIONS.put(Contender.OJALGO_LP_PRIM_DENSE, () -> LinearSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.linear().primal();
            opt.sparse = Boolean.FALSE;
        }));
        INTEGRATIONS.put(Contender.OJALGO_LP_PRIM_SPARSE, () -> LinearSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.linear().primal();
            opt.sparse = Boolean.TRUE;
        }));

        INTEGRATIONS.put(Contender.OJALGO_MIP_DUAL_DENSE, () -> IntegerSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.linear().dual();
            opt.sparse = Boolean.FALSE;
        }));
        INTEGRATIONS.put(Contender.OJALGO_MIP_DUAL_SPARSE, () -> IntegerSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.linear().dual();
            opt.sparse = Boolean.TRUE;
        }));
        INTEGRATIONS.put(Contender.OJALGO_MIP_PRIM_DENSE, () -> IntegerSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.linear().primal();
            opt.sparse = Boolean.FALSE;
        }));
        INTEGRATIONS.put(Contender.OJALGO_MIP_PRIM_SPARSE, () -> IntegerSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.linear().primal();
            opt.sparse = Boolean.TRUE;
        }));

        INTEGRATIONS.put(Contender.OJALGO_QP_DENSE_EXPERIMENTAL, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.FALSE;
            opt.experimental = true;
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_SPARSE_EXPERIMENTAL, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.TRUE;
            opt.experimental = true;
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_DENSE_STABLE, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.FALSE;
            opt.experimental = false;
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_SPARSE_STABLE, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.TRUE;
            opt.experimental = false;
        }));

        INTEGRATIONS.put(Contender.OJALGO_QP_CG_ID, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.TRUE;
            opt.convex().iterative(ConjugateGradientSolver::new, Preconditioner::newIdentity);
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_CG_JACOBI, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.TRUE;
            opt.convex().iterative(ConjugateGradientSolver::new, JacobiPreconditioner::new);
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_CG_SSORP, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.TRUE;
            opt.convex().iterative(ConjugateGradientSolver::new, SSORPreconditioner::new);
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_MINRES_ID, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.TRUE;
            opt.convex().iterative(MINRESSolver::new, Preconditioner::newIdentity);
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_MINRES_JACOBI, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.TRUE;
            opt.convex().iterative(MINRESSolver::new, JacobiPreconditioner::new);
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_MINRES_SSORP, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.TRUE;
            opt.convex().iterative(MINRESSolver::new, SSORPreconditioner::new);
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_QMR_ID, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.TRUE;
            opt.convex().iterative(QMRSolver::new, Preconditioner::newIdentity);
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_QMR_JACOBI, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.TRUE;
            opt.convex().iterative(QMRSolver::new, JacobiPreconditioner::new);
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_QMR_SSORP, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.sparse = Boolean.TRUE;
            opt.convex().iterative(QMRSolver::new, SSORPreconditioner::new);
        }));

        INTEGRATIONS.put(Contender.OJALGO_QP, () -> ConvexSolver.INTEGRATION);

        INTEGRATIONS.put(Contender.OJALGO_QP_ADMM, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.convex().algorithm(Algorithm.ADMM);
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_ASET, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.convex().algorithm(Algorithm.ACTIVE_SET);
        }));

        INTEGRATIONS.put(Contender.OJALGO_QP_ASET_NULLSPACE_DENSE, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.convex().algorithm(Algorithm.ACTIVE_SET);
            opt.convex().projection(Boolean.TRUE);
            opt.sparse = Boolean.FALSE;
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_ASET_NULLSPACE_SPARSE, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.convex().algorithm(Algorithm.ACTIVE_SET);
            opt.convex().projection(Boolean.TRUE);
            opt.sparse = Boolean.TRUE;
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_ASET_PLAIN_DENSE, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.convex().algorithm(Algorithm.ACTIVE_SET);
            opt.convex().projection(Boolean.FALSE);
            opt.sparse = Boolean.FALSE;
        }));
        INTEGRATIONS.put(Contender.OJALGO_QP_ASET_PLAIN_SPARSE, () -> ConvexSolver.INTEGRATION.withOptionsModifier(opt -> {
            opt.convex().algorithm(Algorithm.ACTIVE_SET);
            opt.convex().projection(Boolean.FALSE);
            opt.sparse = Boolean.TRUE;
        }));

    }

}
