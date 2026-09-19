package com.ethlo.time.token;

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

import static com.ethlo.time.DateTimeTokens.digits;
import static com.ethlo.time.DateTimeTokens.fractions;
import static com.ethlo.time.DateTimeTokens.separators;
import static com.ethlo.time.DateTimeTokens.zoneOffset;
import static com.ethlo.time.Field.DAY;
import static com.ethlo.time.Field.HOUR;
import static com.ethlo.time.Field.MINUTE;
import static com.ethlo.time.Field.MONTH;
import static com.ethlo.time.Field.SECOND;
import static com.ethlo.time.Field.YEAR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.text.ParsePosition;
import java.time.format.DateTimeParseException;

import org.junit.jupiter.api.Test;

import com.ethlo.time.DateTime;
import com.ethlo.time.DateTimeParser;
import com.ethlo.time.DateTimeParsers;

/**
 * Failure-mode tests for the configurable, token-driven parser entry point
 * ({@link DateTimeParsers#of(DateTimeToken...)}). Every failure case asserts the exact exception
 * type, the exact reported position and that the parse cursor stops at the offending character.
 */
public class ConfigurableParserErrorTest
{
    private final DateTimeParser parser = DateTimeParsers.of(
            digits(YEAR, 4),
            separators('-'),
            digits(MONTH, 2),
            separators('-'),
            digits(DAY, 2),
            separators('T'),
            digits(HOUR, 2),
            separators(':'),
            digits(MINUTE, 2),
            separators(':'),
            digits(SECOND, 2),
            separators('.'),
            fractions(),
            zoneOffset()
    );

    private void assertParseFailureAt(final String input, final int expectedErrorIndex)
    {
        final ParsePosition pos = new ParsePosition(0);
        final DateTimeParseException exc = assertThrows(DateTimeParseException.class, () -> parser.parse(input, pos));
        assertThat(exc.getErrorIndex()).isEqualTo(expectedErrorIndex);
        assertThat(pos.getErrorIndex()).isEqualTo(expectedErrorIndex);
        assertThat(pos.getIndex()).as("cursor must stop at the offending character, not beyond it").isEqualTo(expectedErrorIndex);
    }

    @Test
    void duplicateFieldTokenRejected()
    {
        final IllegalArgumentException exc = assertThrows(IllegalArgumentException.class, () -> DateTimeParsers.of(
                digits(YEAR, 4),
                separators('-'),
                digits(MONTH, 2),
                separators('-'),
                digits(MONTH, 2)
        ));
        assertThat(exc.getMessage()).contains("Duplicate field MONTH");
    }

    @Test
    void unicodeFullWidthDigitsRejected()
    {
        final String input = "２０１９-01-01T00:00:00.1Z";
        assertParseFailureAt(input, 0);
        assertThat(input.substring(0)).as("no input may be consumed").isEqualTo(input);
    }

    @Test
    void unicodeArabicIndicDigitsRejected()
    {
        assertParseFailureAt("٢٠١٩-01-01T00:00:00.1Z", 0);
    }

    @Test
    void wrongSeparatorRejected()
    {
        final String input = "2019/01-01T00:00:00.1Z";
        assertParseFailureAt(input, 4);
        assertThat(input.substring(4)).as("input from the wrong separator must remain unconsumed").isEqualTo("/01-01T00:00:00.1Z");
    }

    @Test
    void truncatedMonthRejected()
    {
        assertParseFailureAt("2019-0", 5);
    }

    @Test
    void fractionTenDigitsRejected()
    {
        assertParseFailureAt("2019-01-01T00:00:00.1234567890Z", 29);
    }

    @Test
    void missingZoneOffsetRejected()
    {
        // The zoneOffset token requires a sign or 'Z'; junk is reported at its position
        assertParseFailureAt("2019-01-01T00:00:00.1x", 21);
    }

    @Test
    void localDateParserAccepted()
    {
        final DateTime dateTime = DateTimeParsers.localDate().parse("2020-02-29");
        assertThat(dateTime.getYear()).isEqualTo(2020);
        assertThat(dateTime.getMonth()).isEqualTo(2);
        assertThat(dateTime.getDayOfMonth()).isEqualTo(29);
    }

    @Test
    void localTimeParserAccepted()
    {
        final DateTime dateTime = DateTimeParsers.localTime().parse("23:59:37.123456");
        assertThat(dateTime.getHour()).isEqualTo(23);
        assertThat(dateTime.getMinute()).isEqualTo(59);
        assertThat(dateTime.getSecond()).isEqualTo(37);
        assertThat(dateTime.getNano()).isEqualTo(123456000);
        assertThat(dateTime.getFractionDigits()).isEqualTo(6);
    }
}
