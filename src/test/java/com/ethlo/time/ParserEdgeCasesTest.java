package com.ethlo.time;

/*-
 * #%L
 * Internet Time Utility
 * %%
 * Copyright (C) 2017 - 2025 Morten Haraldsen @ethlo
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
import java.time.format.DateTimeParseException;

import org.junit.jupiter.api.Test;

/**
 * Boundary and error-position tests for the fixed-format parser ({@link ITUParser} via {@link ITU})
 * and the duration parser ({@link com.ethlo.time.internal.ItuDurationParser}).
 * <p>
 * Every failure case asserts three things:
 * <ul>
 *     <li>the exact exception type</li>
 *     <li>the exact reported error position (never a range)</li>
 *     <li>that the parse cursor stops at the error and does not swallow subsequent characters</li>
 * </ul>
 */
class ParserEdgeCasesTest
{
    private static DateTimeParseException assertFixedParseFails(final String text, final int expectedErrorIndex)
    {
        final ParsePosition pos = new ParsePosition(0);
        final DateTimeParseException exc = assertThrows(DateTimeParseException.class, () -> ITU.parseLenient(text, ParseConfig.DEFAULT, pos));
        assertThat(exc.getErrorIndex()).as("error index for %s", text).isEqualTo(expectedErrorIndex);
        // The cursor must stop at the error, not beyond it, so trailing characters are left unconsumed
        assertThat(pos.getErrorIndex()).as("ParsePosition error index for %s", text).isEqualTo(expectedErrorIndex);
        assertThat(pos.getIndex()).as("ParsePosition index for %s", text).isEqualTo(expectedErrorIndex);
        assertThat(pos.getIndex()).as("cursor must not consume past end of input for %s", text).isLessThanOrEqualTo(text.length());
        return exc;
    }

    // --- Leap-year February ---

    @Test
    void leapDayValidInLeapYear()
    {
        final ParsePosition pos = new ParsePosition(0);
        final DateTime result = ITU.parseLenient("2024-02-29", ParseConfig.DEFAULT, pos);
        assertThat(result.getYear()).isEqualTo(2024);
        assertThat(result.getMonth()).isEqualTo(2);
        assertThat(result.getDayOfMonth()).isEqualTo(29);
        assertThat(pos.getIndex()).isEqualTo(10);
    }

    @Test
    void leapDayValidInYearDivisibleBy400()
    {
        final DateTime result = ITU.parseLenient("2000-02-29", ParseConfig.DEFAULT);
        assertThat(result.getDayOfMonth()).isEqualTo(29);
    }

    @Test
    void leapDayInvalidInCommonYear()
    {
        final ParsePosition pos = new ParsePosition(0);
        assertThrows(DateTimeException.class, () -> ITU.parseLenient("2023-02-29", ParseConfig.DEFAULT, pos));
        // Validation failure: the cursor must not report consumed input
        assertThat(pos.getIndex()).isZero();
    }

    @Test
    void leapDayInvalidInCenturyYearNotDivisibleBy400()
    {
        final ParsePosition pos = new ParsePosition(0);
        assertThrows(DateTimeException.class, () -> ITU.parseLenient("1900-02-29", ParseConfig.DEFAULT, pos));
        assertThat(pos.getIndex()).isZero();
    }

    // --- 24:00 boundary ---

    @Test
    void hour24Rejected()
    {
        final ParsePosition pos = new ParsePosition(0);
        final DateTimeException exc = assertThrows(DateTimeException.class, () -> ITU.parseLenient("2012-11-11T24:00:00", ParseConfig.DEFAULT, pos));
        assertThat(exc).hasMessageContaining("HourOfDay");
        assertThat(pos.getIndex()).isZero();
    }

    @Test
    void hour23Minute59Second59Valid()
    {
        final ParsePosition pos = new ParsePosition(0);
        final DateTime result = ITU.parseLenient("2012-11-11T23:59:59Z", ParseConfig.DEFAULT, pos);
        assertThat(result.getHour()).isEqualTo(23);
        assertThat(result.getMinute()).isEqualTo(59);
        assertThat(result.getSecond()).isEqualTo(59);
        assertThat(pos.getIndex()).isEqualTo(20);
    }

    // --- Fraction-of-second precision ---

    @Test
    void fractionSingleDigit()
    {
        final DateTime result = ITU.parseLenient("2012-11-11T12:22:11.5Z", ParseConfig.DEFAULT);
        assertThat(result.getNano()).isEqualTo(500_000_000);
        assertThat(result.getFractionDigits()).isEqualTo(1);
    }

    @Test
    void fractionNineDigits()
    {
        final DateTime result = ITU.parseLenient("2012-11-11T12:22:11.123456789Z", ParseConfig.DEFAULT);
        assertThat(result.getNano()).isEqualTo(123_456_789);
        assertThat(result.getFractionDigits()).isEqualTo(9);
    }

    @Test
    void fractionTenDigitsRejected()
    {
        // The 10th fraction digit ends at index 29; 'Z' at index 30 must remain unconsumed
        assertFixedParseFails("2012-11-11T12:22:11.1234567890Z", 29);
    }

    // --- Offset limits ---

    @Test
    void positiveOffsetAtMaximum()
    {
        final DateTime result = ITU.parseLenient("2012-11-11T12:22:11+18:00", ParseConfig.DEFAULT);
        assertThat(result.getOffset()).hasValueSatisfying(offset -> assertThat(offset.getTotalSeconds()).isEqualTo(18 * 3600));
    }

    @Test
    void negativeOffsetAtMaximum()
    {
        final DateTime result = ITU.parseLenient("2012-11-11T12:22:11-18:00", ParseConfig.DEFAULT);
        assertThat(result.getOffset()).hasValueSatisfying(offset -> assertThat(offset.getTotalSeconds()).isEqualTo(-18 * 3600));
    }

    @Test
    void positiveOffsetBeyondMaximum()
    {
        final ParsePosition pos = new ParsePosition(0);
        final DateTimeException exc = assertThrows(DateTimeException.class, () -> ITU.parseLenient("2012-11-11T12:22:11+18:01", ParseConfig.DEFAULT, pos));
        assertThat(exc).hasMessageContaining("-18:00 to +18:00");
        assertThat(pos.getIndex()).isZero();
    }

    @Test
    void negativeOffsetBeyondMaximum()
    {
        final ParsePosition pos = new ParsePosition(0);
        final DateTimeException exc = assertThrows(DateTimeException.class, () -> ITU.parseLenient("2012-11-11T12:22:11-18:01", ParseConfig.DEFAULT, pos));
        assertThat(exc).hasMessageContaining("-18:00 to +18:00");
        assertThat(pos.getIndex()).isZero();
    }

    @Test
    void negativeZeroOffsetRejected()
    {
        // RFC 3339: "-00:00" denotes an unknown local offset and is not allowed
        assertFixedParseFails("2012-11-11T12:22:11-00:00", 19);
    }

    // --- Missing tokens / truncation ---

    @Test
    void missingMinutesAfterHourSeparator()
    {
        assertFixedParseFails("2012-11-11T12:", 14);
    }

    @Test
    void missingDateTimeSeparator()
    {
        assertFixedParseFails("2012-11-11X12:22:11", 10);
    }

    // --- Trailing junk ---

    @Test
    void trailingJunkAfterZulu()
    {
        // 'Z' consumed at index 19, the junk starting at index 20 must be reported, not swallowed
        assertFixedParseFails("2012-11-11T12:22:11Zjunk", 20);
    }

    @Test
    void trailingJunkAfterNumericOffset()
    {
        // "+08:00" ends at index 24, junk starts at index 25
        assertFixedParseFails("2012-11-11T12:22:11+08:00xyz", 25);
    }

    // --- Unicode digits must not be treated as ASCII digits ---

    @Test
    void arabicIndicDigitsInYearRejected()
    {
        // ٢٠١٢ is 2012 in Arabic-Indic digits (U+0662..) and must not parse as a year
        assertFixedParseFails("٢٠١٢-11-11", 0);
    }

    @Test
    void fullwidthDigitsInYearRejected()
    {
        // ２０１２ is 2012 in fullwidth digits (U+FF12..) and must not parse as a year
        assertFixedParseFails("２０１２-11-11", 0);
    }

    @Test
    void arabicIndicDigitsInFractionRejected()
    {
        // The fraction ١٢٣ contains no ASCII digits, so the error is reported at the fraction separator
        assertFixedParseFails("2012-11-11T12:22:11.١٢٣Z", 19);
    }

    // --- Duration: sign, duplicates, overflow, trailing junk ---

    @Test
    void durationNegativeWithFraction()
    {
        final Duration duration = ITU.parseDuration("-PT2.5S");
        // -2.5 seconds is normalized to -3 seconds + 500 ms
        assertThat(duration.getSeconds()).isEqualTo(-3);
        assertThat(duration.getNanos()).isEqualTo(500_000_000);
    }

    @Test
    void durationNegativeDays()
    {
        final Duration duration = ITU.parseDuration("-P1D");
        assertThat(duration.getSeconds()).isEqualTo(-86_400);
        assertThat(duration.getNanos()).isZero();
    }

    @Test
    void durationMisplacedNegativeSign()
    {
        final DateTimeParseException exc = assertThrows(DateTimeParseException.class, () -> ITU.parseDuration("P-1D"));
        assertThat(exc.getErrorIndex()).isEqualTo(1);
    }

    @Test
    void durationDuplicateHourUnit()
    {
        // The second 'H' is at index 5; the error must be reported there, not at the end of input
        final DateTimeParseException exc = assertThrows(DateTimeParseException.class, () -> ITU.parseDuration("PT1H2H"));
        assertThat(exc.getErrorIndex()).isEqualTo(5);
    }

    @Test
    void durationDuplicateWeekUnit()
    {
        final DateTimeParseException exc = assertThrows(DateTimeParseException.class, () -> ITU.parseDuration("P1W2W"));
        assertThat(exc.getErrorIndex()).isEqualTo(4);
    }

    @Test
    void durationOverflowOnUnitMultiplication()
    {
        // Multiplying the value by seconds-per-hour overflows at the 'H' unit (index 18)
        final DateTimeParseException exc = assertThrows(DateTimeParseException.class, () -> ITU.parseDuration("PT9999999999999999H"));
        assertThat(exc).hasMessageContaining("too large");
        assertThat(exc.getErrorIndex()).isEqualTo(18);
    }

    @Test
    void durationOverflowOnDigitAccumulation()
    {
        // The 19th consecutive digit (index 20) overflows the long accumulator
        final DateTimeParseException exc = assertThrows(DateTimeParseException.class, () -> ITU.parseDuration("PT99999999999999999999S"));
        assertThat(exc).hasMessageContaining("too large");
        assertThat(exc.getErrorIndex()).isEqualTo(20);
    }

    @Test
    void durationMissingValueAfterT()
    {
        final DateTimeParseException exc = assertThrows(DateTimeParseException.class, () -> ITU.parseDuration("PT"));
        assertThat(exc.getErrorIndex()).isEqualTo(2);
    }

    @Test
    void durationTrailingJunkUnit()
    {
        // 'X' at index 4 is an invalid unit; the error must point at it, not past it
        final DateTimeParseException exc = assertThrows(DateTimeParseException.class, () -> ITU.parseDuration("PT1HX"));
        assertThat(exc.getErrorIndex()).isEqualTo(4);
    }
}
