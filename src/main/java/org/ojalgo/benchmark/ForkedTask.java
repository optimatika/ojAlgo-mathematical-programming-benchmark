package org.ojalgo.benchmark;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;

import org.ojalgo.benchmark.AbstractBenchmark.ResultsSet;
import org.ojalgo.concurrent.MethodDescriptor;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.ExpressionsBasedModel.FileFormat;
import org.ojalgo.optimisation.ExpressionsBasedModel.Integration;
import org.ojalgo.optimisation.Optimisation.Result;
import org.ojalgo.optimisation.Variable;
import org.ojalgo.type.CalendarDateUnit;
import org.ojalgo.type.Stopwatch.TimedResult;

public abstract class ForkedTask {

    public static final class ReturnValue implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * Whether all the solves agreed with each other - the same state and value every time.
         */
        public final boolean consistent;
        public final double density;
        public final int nbExpressions;
        public final int nbVariables;
        public final String result;
        /**
         * The middle of the last three solve times, ms - NaN if there is no result.
         */
        public final double time;
        /**
         * Whether the returned solution actually satisfies the constraints of the model that was solved. A
         * solver can report OPTIMAL and hand back a point that does not - and when the offending variables
         * carry little objective weight, comparing objective values alone will not reveal it.
         */
        public final boolean valid;

        ReturnValue(final String result, final double time, final boolean consistent, final int nbVariables, final int nbExpressions, final double density,
                final boolean valid) {
            super();
            this.result = result;
            this.time = time;
            this.consistent = consistent;
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
     * @param validate    Whether to check that the solution satisfies the model's constraints. Only
     *                    meaningful on a pair's first pass - that is where capability is decided - and the
     *                    check walks every expression in BigDecimal, so it is not worth repeating while
     *                    stabilising times.
     */
    public static ReturnValue execute(final String modelFilePath, final String contenderSolverName, final long maxWaitTime, final int threads,
            final int maxSolves, final String libraryPath, final boolean validate) {

        // ms - exact, so that sub-millisecond solves use up the budget too
        double instanceTime = Double.MAX_VALUE;
        double remainingTime = maxWaitTime / 2D;
        int nbSolves = 0;

        if (libraryPath != null && !libraryPath.isEmpty()) {
            System.load(libraryPath);
        }

        Supplier<Integration<?>> supplier = Contender.INTEGRATIONS.get(contenderSolverName);
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

            // Solving sets the variable values, and they would be used to warm-start the next solve
            List<Variable> variables = simplified.getVariables();
            List<BigDecimal> initialValues = variables.stream().map(Variable::getValue).toList();

            // Repeat until the budget is spent or the last three times agree - or as soon as a solve isn't optimal,
            // or doesn't agree with the others, as then the pair has failed and there is nothing more to measure
            do {

                for (int i = 0; i < variables.size(); i++) {
                    variables.get(i).setValue(initialValues.get(i));
                }

                TimedResult<Result> meassured = AbstractBenchmark.meassure(simplified, integration);

                instanceTime = meassured.duration.convertTo(CalendarDateUnit.MILLIS).measure;
                remainingTime -= instanceTime;
                nbSolves++;

                resultsSet.add(meassured);

            } while (nbSolves != maxSolves && instanceTime < remainingTime && resultsSet.result().getState().isOptimal() && !resultsSet.isStable());

        } catch (IOException cause) {
            throw new RuntimeException(cause);
        }

        Result result = resultsSet.result();

        if (result != null) {

            boolean valid = !validate || solved.validate(result, AbstractBenchmark.ACCURACY);

            return new ReturnValue(result.toString(), resultsSet.medianTime(), resultsSet.isConsistent(), nbVariables, nbExpressions, density, valid);

        } else {

            return new ReturnValue(null, Double.NaN, true, nbVariables, nbExpressions, density, false);
        }
    }

}
