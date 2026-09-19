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

import java.text.ParsePosition;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

import org.junit.jupiter.api.Test;

/**
 * Boundary and failure-mode tests for the fixed-format RFC-3339 entry points ({@link ITU}).
 * Every failure case asserts the exact exception type, the exact reported position and that the
 * parse cursor stops at the offending character instead of consuming subsequent input.
 */
public class ParserBoundaryAndErrorTest
{
    private static void assertParseFailureAt(final String input, final int expectedErrorIndex)
    {
        final ParsePosition pos = new ParsePosition(0);
        final DateTimeParseException exc = assertThrows(DateTimeParseException.class, () -> ITU.parseLenient(input, ParseConfig.DEFAULT, pos));
        assertThat(exc.getErrorIndex()).isEqualTo(expectedErrorIndex);
        assertThat(pos.getErrorIndex()).isEqualTo(expectedErrorIndex);
        assertThat(pos.getIndex()).as("cursor must stop at the offending character, not beyond it").isEqualTo(expectedErrorIndex);
    }

    private static void assertDateTimeExceptionAndCursorUntouched(final String input)
    {
        final ParsePosition pos = new ParsePosition(0);
        final DateTimeException exc = assertThrows(DateTimeException.class, () -> ITU.parseDateTime(input, pos));
        assertThat(exc).isExactlyInstanceOf(DateTimeException.class);
        assertThat(pos.getIndex()).as("cursor must not advance on failure").isEqualTo(0);
        assertThat(pos.getErrorIndex()).isEqualTo(-1);
    }

    // --- Leap-year February ---

    @Test
    void leapDayAcceptedInLeapYear()
    {
        assertThat(ITU.parseDateTime("2020-02-29T00:00:00Z")).isEqualTo(OffsetDateTime.of(2020, 2, 29, 0, 0, 0, 0, ZoneOffset.UTC));
        assertThat(ITU.parseDateTime("2000-02-29T23:59:59Z")).isEqualTo(OffsetDateTime.of(2000, 2, 29, 23, 59, 59, 0, ZoneOffset.UTC));
    }

    @Test
    void leapDayRejectedInNonLeapYear()
    {
        assertDateTimeExceptionAndCursorUntouched("2019-02-29T00:00:00Z");
    }

    @Test
    void leapDayRejectedInCenturyNonLeapYear()
    {
        assertDateTimeExceptionAndCursorUntouched("2100-02-29T00:00:00Z");
    }

    // --- 24:00 boundary ---

    @Test
    void hour24Rejected()
    {
        assertDateTimeExceptionAndCursorUntouched("2019-01-01T24:00:00Z");
    }

    @Test
    void hour23Accepted()
    {
        assertThat(ITU.parseDateTime("2019-01-01T23:59:59Z").getHour()).isEqualTo(23);
    }

    // --- Fraction-of-second precision ---

    @Test
    void fractionNanoPrecisionAccepted()
    {
        final DateTime dateTime = ITU.parseLenient("2019-01-01T00:00:00.123456789Z");
        assertThat(dateTime.getNano()).isEqualTo(123456789);
        assertThat(dateTime.getFractionDigits()).isEqualTo(9);
    }

    @Test
    void fractionTenDigitsRejected()
    {
        final String input = "2019-01-01T00:00:00.1234567890Z";
        assertParseFailureAt(input, 29);
        assertThat(input.substring(29)).as("the 10th fraction digit and zone must remain unconsumed").isEqualTo("0Z");
    }

    @Test
    void fractionWithoutDigitsRejected()
    {
        assertParseFailureAt("2019-01-01T00:00:00.Z", 19);
    }

    // --- Timezone offset limits ---

    @Test
    void offsetPlusAndMinus18Accepted()
    {
        assertThat(ITU.parseDateTime("2019-01-01T00:00:00+18:00").getOffset()).isEqualTo(ZoneOffset.ofHours(18));
        assertThat(ITU.parseDateTime("2019-01-01T00:00:00-18:00").getOffset()).isEqualTo(ZoneOffset.ofHours(-18));
    }

    @Test
    void offsetBeyond18HoursRejected()
    {
        assertDateTimeExceptionAndCursorUntouched("2019-01-01T00:00:00+18:01");
        assertDateTimeExceptionAndCursorUntouched("2019-01-01T00:00:00-18:01");
        assertDateTimeExceptionAndCursorUntouched("2019-01-01T00:00:00+19:00");
    }

    @Test
    void offsetMinutesBeyond59Rejected()
    {
        assertDateTimeExceptionAndCursorUntouched("2019-01-01T00:00:00+12:60");
    }

    @Test
    void offsetNegativeZeroRejected()
    {
        assertParseFailureAt("2019-01-01T00:00:00-00:00", 19);
    }

    // --- Missing tokens / truncation ---

    @Test
    void missingSecondsRejected()
    {
        final ParsePosition pos = new ParsePosition(0);
        final String input = "2019-01-01T00:00";
        final DateTimeParseException exc = assertThrows(DateTimeParseException.class, () -> ITU.parseDateTime(input, pos));
        assertThat(exc.getErrorIndex()).isEqualTo(16);
        // The parse itself succeeds at minute granularity, so the cursor stops at end of input
        // and the failure is reported by the exception only
        assertThat(pos.getIndex()).isEqualTo(16);
        assertThat(pos.getErrorIndex()).isEqualTo(-1);
    }

    @Test
    void truncatedAfterTimeSeparatorRejected()
    {
        assertParseFailureAt("2019-01-01T00:00:", 16);
    }

    @Test
    void truncatedAfterDateTimeSeparatorRejected()
    {
        assertParseFailureAt("2019-01-01T", 11);
    }

    // --- Trailing junk ---

    @Test
    void trailingJunkAfterZuluRejected()
    {
        final String input = "2019-01-01T00:00:00Zjunk";
        assertParseFailureAt(input, 20);
        assertThat(input.substring(20)).as("trailing junk must remain unconsumed").isEqualTo("junk");
    }

    @Test
    void trailingJunkAfterOffsetRejected()
    {
        final String input = "2019-01-01T00:00:00+01:00extra";
        assertParseFailureAt(input, 25);
        assertThat(input.substring(25)).as("trailing junk must remain unconsumed").isEqualTo("extra");
    }

    // --- Unicode digits must not be treated as ASCII digits ---

    @Test
    void unicodeFullWidthDigitsRejected()
    {
        final String input = "２０１９-01-01T00:00:00Z";
        assertParseFailureAt(input, 0);
        assertThat(input.substring(0)).as("no input may be consumed").isEqualTo(input);
    }

    @Test
    void unicodeArabicIndicFractionDigitsRejected()
    {
        assertParseFailureAt("2019-01-01T00:00:00.١٢٣Z", 19);
    }
}
