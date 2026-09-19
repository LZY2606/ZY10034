package com.ethlo.time.token;

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
 * Error-position and cursor tests for the token-driven {@link ConfigurableDateTimeParser}.
 * Every failure case asserts the exact exception type, the exact reported position,
 * and that the parse cursor stops at the error without swallowing subsequent characters.
 */
class ConfigurableParserEdgeCasesTest
{
    private final DateTimeParser rfc3339Parser = DateTimeParsers.of(
            digits(YEAR, 4),
            separators('-'),
            digits(MONTH, 2),
            separators('-'),
            digits(DAY, 2),
            separators('T', 't', ' '),
            digits(HOUR, 2),
            separators(':'),
            digits(MINUTE, 2),
            separators(':'),
            digits(SECOND, 2),
            separators('.'),
            fractions(),
            zoneOffset()
    );

    private void assertParseFails(final DateTimeParser parser, final String text, final int expectedErrorIndex)
    {
        final ParsePosition pos = new ParsePosition(0);
        final DateTimeParseException exc = assertThrows(DateTimeParseException.class, () -> parser.parse(text, pos));
        assertThat(exc.getErrorIndex()).as("error index for %s", text).isEqualTo(expectedErrorIndex);
        assertThat(pos.getErrorIndex()).as("ParsePosition error index for %s", text).isEqualTo(expectedErrorIndex);
        // The cursor must stop at the error, not beyond it, so trailing characters are left unconsumed
        assertThat(pos.getIndex()).as("ParsePosition index for %s", text).isEqualTo(expectedErrorIndex);
    }

    @Test
    void parseFullDateTimeWithOffset()
    {
        final ParsePosition pos = new ParsePosition(0);
        final String input = "2012-11-11T12:22:11.123+02:30";
        final DateTime result = rfc3339Parser.parse(input, pos);
        assertThat(result.getYear()).isEqualTo(2012);
        assertThat(result.getMonth()).isEqualTo(11);
        assertThat(result.getDayOfMonth()).isEqualTo(11);
        assertThat(result.getHour()).isEqualTo(12);
        assertThat(result.getMinute()).isEqualTo(22);
        assertThat(result.getSecond()).isEqualTo(11);
        assertThat(result.getNano()).isEqualTo(123_000_000);
        assertThat(result.getFractionDigits()).isEqualTo(3);
        assertThat(result.getOffset()).hasValueSatisfying(offset -> assertThat(offset.getTotalSeconds()).isEqualTo(9_000));
        assertThat(pos.getIndex()).isEqualTo(input.length());
    }

    @Test
    void unicodeDigitsInSecondRejected()
    {
        // ١١ is 11 in Arabic-Indic digits (U+0661) and must not parse as ASCII seconds
        assertParseFails(rfc3339Parser, "2012-11-11T12:22:١١.123+02:30", 17);
    }

    @Test
    void unicodeDigitsInMonthRejected()
    {
        // ١١ is 11 in Arabic-Indic digits and must not parse as ASCII month
        assertParseFails(rfc3339Parser, "2012-١١-11T12:22:11.123+02:30", 5);
    }

    @Test
    void truncatedInputMissingMinuteToken()
    {
        // Input ends right where the two minute digits are expected
        assertParseFails(rfc3339Parser, "2012-11-11T12:", 14);
    }

    @Test
    void truncatedZoneOffset()
    {
        // "+02" is too short for a zone offset; the error is reported at the sign
        assertParseFails(rfc3339Parser, "2012-11-11T12:22:11.123+02", 23);
    }

    @Test
    void missingSeparatorToken()
    {
        // '_' is not one of the allowed date/time separators
        assertParseFails(rfc3339Parser, "2012-11-11_12:22:11.123+02:30", 10);
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
        assertThat(exc).hasMessageContaining("Duplicate field MONTH");
    }

    @Test
    void trailingCharactersLeftUnconsumedOnSuccess()
    {
        final DateTimeParser dateParser = DateTimeParsers.of(
                digits(YEAR, 4),
                separators('-'),
                digits(MONTH, 2),
                separators('-'),
                digits(DAY, 2)
        );
        final ParsePosition pos = new ParsePosition(0);
        final DateTime result = dateParser.parse("2012-11-11,rest-of-input", pos);
        assertThat(result.getDayOfMonth()).isEqualTo(11);
        // The parser must stop after the date and leave ",rest-of-input" unconsumed
        assertThat(pos.getIndex()).isEqualTo(10);
    }
}
