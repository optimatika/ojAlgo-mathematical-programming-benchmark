package org.ojalgo.benchmark;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;

import org.ojalgo.benchmark.AbstractBenchmark.ResultsSet;
import org.ojalgo.concurrent.MethodDescriptor;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.ExpressionsBasedModel.FileFormat;
import org.ojalgo.optimisation.ExpressionsBasedModel.Integration;
import org.ojalgo.optimisation.Optimisation.Result;
import org.ojalgo.type.Stopwatch.TimedResult;

public abstract class ForkedTask {

    public static final class ReturnValue implements Serializable {

        private static final long serialVersionUID = 1L;

        public final int nbExpressions;
        public final int nbVariables;

        public final String result;
        public final double time;

        ReturnValue(final String result, final double time, final int nbVariables, final int nbExpressions) {
            super();
            this.result = result;
            this.time = time;
            this.nbVariables = nbVariables;
            this.nbExpressions = nbExpressions;
        }

    }

    static final MethodDescriptor DESCRIPTOR = MethodDescriptor.of(ForkedTask.class, "execute", String.class, String.class);

    public static ReturnValue execute(final String modelFilePath, final String contenderSolverName) {

        ExpressionsBasedModel.clearIntegrations();
        Integration<?> integration = AbstractBenchmark.INTEGRATIONS.get(contenderSolverName);
        if (integration != null) {
            ExpressionsBasedModel.addIntegration(integration);
        }

        ResultsSet resultsSet = new ResultsSet();

        int nbVariables = 0;
        int nbExpressions = 0;

        try (InputStream input = AbstractBenchmark.class.getResourceAsStream(modelFilePath)) {

            ExpressionsBasedModel parsedMPS = ExpressionsBasedModel.parse(input, FileFormat.MPS);

            nbVariables = parsedMPS.countVariables();
            nbExpressions = parsedMPS.countExpressions();

            ExpressionsBasedModel simplified = parsedMPS.simplify();

            while (!resultsSet.isStable()) {
                resultsSet.add(AbstractBenchmark.meassure(simplified));
            }

        } catch (IOException cause) {
            throw new RuntimeException(cause);
        }

        TimedResult<Result> fastest = resultsSet.fastest;

        if (fastest != null) {

            return new ReturnValue(fastest.result.toString(), fastest.duration.measure, nbVariables, nbExpressions);

        } else {

            return new ReturnValue(null, Double.NaN, nbVariables, nbExpressions);
        }
    }

}
