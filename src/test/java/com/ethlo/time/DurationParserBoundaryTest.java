package com.ethlo.time;

/*-
 * #%L
 * Internet Time Utility
 * %%
 * Copyright (C) 2017 - 2026 Morten Haraldsen @ethlo
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.format.DateTimeParseException;

import org.junit.jupiter.api.Test;

/**
 * Sign, overflow, duplicate-token and truncation tests for {@link ITU#parseDuration(String)}.
 * Every failure case asserts the exact exception type and the exact reported position, and that
 * the position points at the offending character rather than past the remaining input.
 */
public class DurationParserBoundaryTest
{
    private static DateTimeParseException assertDurationFailureAt(final String input, final int expectedErrorIndex)
    {
        final DateTimeParseException exc = assertThrows(DateTimeParseException.class, () -> ITU.parseDuration(input));
        assertThat(exc.getErrorIndex()).isEqualTo(expectedErrorIndex);
        return exc;
    }

    // --- Sign handling ---

    @Test
    void negativeFractionalDuration()
    {
        final Duration duration = ITU.parseDuration("-PT2.5S");
        assertThat(duration.getSeconds()).isEqualTo(-3L);
        assertThat(duration.getNanos()).isEqualTo(500_000_000);
        assertThat(duration).isEqualTo(Duration.of(-3, 500_000_000));
    }

    @Test
    void negativeDaysDuration()
    {
        final Duration duration = ITU.parseDuration("-P1D");
        assertThat(duration.getSeconds()).isEqualTo(-86_400L);
        assertThat(duration.getNanos()).isEqualTo(0);
    }

    @Test
    void misplacedNegativeSignRejected()
    {
        final String input = "P-1D";
        final DateTimeParseException exc = assertDurationFailureAt(input, 1);
        assertThat(input.charAt(exc.getErrorIndex())).as("error must point at the misplaced sign").isEqualTo('-');
    }

    // --- Overflow ---

    @Test
    void secondsOverflowRejected()
    {
        final DateTimeParseException exc = assertDurationFailureAt("PT99999999999999999999S", 2);
        assertThat(exc.getMessage()).contains("too large");
    }

    @Test
    void weeksOverflowRejected()
    {
        final DateTimeParseException exc = assertDurationFailureAt("P15241578750190521W", 1);
        assertThat(exc.getMessage()).contains("too large");
    }

    @Test
    void maxLongSecondsAccepted()
    {
        final Duration duration = ITU.parseDuration("PT9223372036854775807S");
        assertThat(duration.getSeconds()).isEqualTo(Long.MAX_VALUE);
        assertThat(duration.getNanos()).isEqualTo(0);
    }

    // --- Duplicate and misplaced tokens ---

    @Test
    void duplicateHourUnitRejected()
    {
        final String input = "PT1H2H";
        final DateTimeParseException exc = assertDurationFailureAt(input, 5);
        assertThat(input.charAt(exc.getErrorIndex())).as("error must point at the repeated unit").isEqualTo('H');
    }

    @Test
    void duplicateDayUnitRejected()
    {
        final String input = "P1D2D";
        final DateTimeParseException exc = assertDurationFailureAt(input, 4);
        assertThat(input.charAt(exc.getErrorIndex())).as("error must point at the repeated unit").isEqualTo('D');
    }

    @Test
    void unitsOutOfOrderRejected()
    {
        final String input = "PT1M2H";
        final DateTimeParseException exc = assertDurationFailureAt(input, 3);
        assertThat(input.charAt(exc.getErrorIndex())).as("error must point at the out-of-order unit").isEqualTo('M');
    }

    // --- Missing tokens / truncation ---

    @Test
    void emptyDurationRejected()
    {
        assertDurationFailureAt("", -1);
    }

    @Test
    void missingValueAfterTRejected()
    {
        assertDurationFailureAt("PT", 2);
    }

    @Test
    void missingValueBeforeDotRejected()
    {
        final String input = "PT.5S";
        final DateTimeParseException exc = assertDurationFailureAt(input, 2);
        assertThat(input.charAt(exc.getErrorIndex())).as("error must point at the dot").isEqualTo('.');
    }

    // --- Unicode digits must not be treated as ASCII digits ---

    @Test
    void unicodeArabicIndicDigitsRejected()
    {
        final String input = "PT١٢S";
        final DateTimeParseException exc = assertDurationFailureAt(input, 2);
        assertThat(input.charAt(exc.getErrorIndex())).as("error must point at the first non-ASCII digit").isEqualTo('١');
    }
}
