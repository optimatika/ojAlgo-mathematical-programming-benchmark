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
package org.ojalgo.benchmark.integer.miplib2017;

import org.junit.jupiter.api.Test;
import org.ojalgo.optimisation.Optimisation;
import org.ojalgo.optimisation.integer.IntegerStrategy;
import org.ojalgo.optimisation.integer.IntegerStrategy.ConfigurableStrategy;
import org.ojalgo.optimisation.integer.NodeKey;
import org.ojalgo.type.CalendarDateUnit;
import org.ojalgo.type.context.NumberContext;

public final class SpecificCase extends MIPLIB2017 {

    @Test
    void testGenIp002() {

        Optimisation.Options options = new Optimisation.Options();

        options.time_abort = 5L * CalendarDateUnit.MINUTE.toDurationInMillis();
        options.time_suffice = options.time_abort;

        ConfigurableStrategy strategy = IntegerStrategy.DEFAULT.withGapTolerance(NumberContext.of(4)).addPriorityDefinitions(NodeKey.MIN_OBJECTIVE,
                NodeKey.MAX_OBJECTIVE);

        MIPLIB2017.doOne("gen-ip002.mps.gz", options, strategy);
    }

    @Test
    void testGenIp054() {

        Optimisation.Options options = new Optimisation.Options();

        options.time_abort = 5L * CalendarDateUnit.MINUTE.toDurationInMillis();
        options.time_suffice = options.time_abort;

        ConfigurableStrategy strategy = IntegerStrategy.DEFAULT.withGapTolerance(NumberContext.of(4)).addPriorityDefinitions(NodeKey.MIN_OBJECTIVE,
                NodeKey.MAX_OBJECTIVE);

        MIPLIB2017.doOne("gen-ip054.mps.gz", options, strategy);
    }

}
