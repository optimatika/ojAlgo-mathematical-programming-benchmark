package org.ojalgo.benchmark;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.function.Supplier;

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
        /**
         * Whether the returned solution actually satisfies the constraints of the model that was solved. A
         * solver can report OPTIMAL and hand back a point that does not - and when the offending variables
         * carry little objective weight, comparing objective values alone will not reveal it.
         */
        public final boolean valid;

        ReturnValue(final String result, final double time, final int nbVariables, final int nbExpressions, final double density, final boolean valid) {
            super();
            this.result = result;
            this.time = time;
            this.nbVariables = nbVariables;
            this.nbExpressions = nbExpressions;
            this.density = density;
            this.valid = valid;
        }

    }

    static final MethodDescriptor DESCRIPTOR = MethodDescriptor.of(ForkedTask.class, "execute", String.class, String.class, long.class, int.class, int.class,
            String.class, boolean.class);

    /**
     * @param maxSolves   The number of times to solve the model. Anything less than 1 means "as many as fit
     *                    within half of {@code maxWaitTime}, or until the times stabilise" - repeat that many
     *                    times to get a reliable measurement. Pass 1 to solve once, which is all that's
     *                    needed when the question is whether the solver manages at all.
     * @param libraryPath Absolute path to the native library to use, or empty to let the integration find one
     *                    itself. Loading it here, before the integration initialises, is what makes it the
     *                    one the integration binds to.
     * @param validate    Whether to check that the solution satisfies the model's constraints. Only meaningful
     *                    on a pair's first pass - that is where capability is decided - and the check walks
     *                    every expression in BigDecimal, so it is not worth repeating while stabilising times.
     */
    public static ReturnValue execute(final String modelFilePath, final String contenderSolverName, final long maxWaitTime, final int threads,
            final int maxSolves, final String libraryPath, final boolean validate) {

        long instanceTime = Long.MAX_VALUE;
        long remainingTime = maxWaitTime / 2L;
        int nbSolves = 0;

        if (libraryPath != null && !libraryPath.isEmpty()) {
            System.load(libraryPath);
        }

        Supplier<Integration<?>> supplier = AbstractBenchmark.INTEGRATIONS.get(contenderSolverName);
        Integration<?> integration = supplier != null ? supplier.get() : null;

        ResultsSet resultsSet = new ResultsSet();

        int nbVariables = 0;
        int nbExpressions = 0;
        double density = Double.NaN;

        ExpressionsBasedModel solved = null;

        String classLoaderPath = modelFilePath.startsWith("/") ? modelFilePath.substring(1) : modelFilePath;
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(classLoaderPath)) {

            FileFormat format = classLoaderPath.endsWith(".lp") ? FileFormat.LP : FileFormat.MPS;
            ExpressionsBasedModel parsedMPS = ExpressionsBasedModel.parse(input, format);

            nbVariables = parsedMPS.countVariables();
            nbExpressions = parsedMPS.countExpressions();
            density = parsedMPS.objective().density();

            ExpressionsBasedModel simplified = parsedMPS.simplify();
            solved = simplified;

            simplified.options.parallelism(threads);

            do {

                TimedResult<Result> meassured = AbstractBenchmark.meassure(simplified, integration);

                instanceTime = meassured.duration.toDurationInMillis();
                remainingTime -= instanceTime;
                nbSolves++;

                resultsSet.add(meassured);

            } while (nbSolves != maxSolves && instanceTime < remainingTime && !resultsSet.isStable());

        } catch (IOException cause) {
            throw new RuntimeException(cause);
        }

        TimedResult<Result> fastest = resultsSet.fastest;

        if (fastest != null) {

            boolean valid = !validate || solved.validate(fastest.result, AbstractBenchmark.ACCURACY);

            return new ReturnValue(fastest.result.toString(), fastest.duration.measure, nbVariables, nbExpressions, density, valid);

        } else {

            return new ReturnValue(null, Double.NaN, nbVariables, nbExpressions, density, false);
        }
    }

}
