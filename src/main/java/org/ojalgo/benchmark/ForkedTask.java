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

        public final double density;
        public final int nbExpressions;
        public final int nbVariables;
        public final String result;
        public final double time;

        ReturnValue(final String result, final double time, final int nbVariables, final int nbExpressions, final double density) {
            super();
            this.result = result;
            this.time = time;
            this.nbVariables = nbVariables;
            this.nbExpressions = nbExpressions;
            this.density = density;
        }

    }

    static final MethodDescriptor DESCRIPTOR = MethodDescriptor.of(ForkedTask.class, "execute", String.class, String.class, long.class);

    public static ReturnValue execute(final String modelFilePath, final String contenderSolverName, final long maxWaitTime) {

        long instanceTime = Long.MAX_VALUE;
        long remainingTime = maxWaitTime / 2L;

        Integration<?> integration = AbstractBenchmark.INTEGRATIONS.get(contenderSolverName);

        ResultsSet resultsSet = new ResultsSet();

        int nbVariables = 0;
        int nbExpressions = 0;
        double density = Double.NaN;

        String classLoaderPath = modelFilePath.startsWith("/") ? modelFilePath.substring(1) : modelFilePath;
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(classLoaderPath)) {

            FileFormat format = classLoaderPath.endsWith(".lp") ? FileFormat.LP : FileFormat.MPS;
            ExpressionsBasedModel parsedMPS = ExpressionsBasedModel.parse(input, format);

            nbVariables = parsedMPS.countVariables();
            nbExpressions = parsedMPS.countExpressions();
            density = parsedMPS.objective().density();

            ExpressionsBasedModel simplified = parsedMPS.simplify();

            do {

                TimedResult<Result> meassured = AbstractBenchmark.meassure(simplified, integration);

                instanceTime = meassured.duration.toDurationInMillis();
                remainingTime -= instanceTime;

                resultsSet.add(meassured);

            } while (instanceTime < remainingTime && !resultsSet.isStable());

        } catch (IOException cause) {
            throw new RuntimeException(cause);
        }

        TimedResult<Result> fastest = resultsSet.fastest;

        if (fastest != null) {

            return new ReturnValue(fastest.result.toString(), fastest.duration.measure, nbVariables, nbExpressions, density);

        } else {

            return new ReturnValue(null, Double.NaN, nbVariables, nbExpressions, density);
        }
    }

}
