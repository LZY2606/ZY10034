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
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.text.ParsePosition;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * Deterministic (fixed-seed) round-trip tests. Legal components are generated from a fixed seed and
 * pushed through parse-format-parse and format-parse-parse cycles, comparing structural values and
 * canonical text. Both the fixed-format entry point ({@link ITU}) and the configurable
 * token-driven parser ({@link com.ethlo.time.token.ConfigurableDateTimeParser}) are covered.
 * <p>
 * On failure the seed and the minimal field set needed to reproduce are part of the assertion message.
 */
class SeededRoundTripTest
{
    private static final long SEED = 20240929L;
    private static final int ITERATIONS = 500;

    private static final int[] POW10 = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000, 1_000_000_000};

    private final DateTimeParser configurableParser = DateTimeParsers.of(
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

    private static class Components
    {
        int year;
        int month;
        int day;
        int hour;
        int minute;
        int second;
        int fractionDigits;
        int nano;
        int offsetSeconds;

        String describe()
        {
            return String.format("seed=%d, year=%d, month=%d, day=%d, hour=%d, minute=%d, second=%d, fractionDigits=%d, nano=%d, offsetSeconds=%d",
                    SEED, year, month, day, hour, minute, second, fractionDigits, nano, offsetSeconds);
        }

        String canonicalText()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append(String.format("%04d-%02d-%02dT%02d:%02d:%02d", year, month, day, hour, minute, second));
            if (fractionDigits > 0)
            {
                sb.append('.').append(String.format("%0" + fractionDigits + "d", nano / POW10[9 - fractionDigits]));
            }
            if (offsetSeconds == 0)
            {
                sb.append('Z');
            }
            else
            {
                final int abs = Math.abs(offsetSeconds);
                sb.append(offsetSeconds < 0 ? '-' : '+').append(String.format("%02d:%02d", abs / 3600, (abs % 3600) / 60));
            }
            return sb.toString();
        }

        OffsetDateTime toOffsetDateTime()
        {
            return OffsetDateTime.of(year, month, day, hour, minute, second, nano, ZoneOffset.ofTotalSeconds(offsetSeconds));
        }
    }

    private static Components randomComponents(final Random random, final int minFractionDigits)
    {
        final Components c = new Components();
        c.year = random.nextInt(10_000);
        c.month = 1 + random.nextInt(12);
        c.day = 1 + random.nextInt(LocalDate.of(c.year, c.month, 1).lengthOfMonth());
        c.hour = random.nextInt(24);
        c.minute = random.nextInt(60);
        c.second = random.nextInt(60);
        c.fractionDigits = minFractionDigits + random.nextInt(10 - minFractionDigits);
        c.nano = c.fractionDigits == 0 ? 0 : random.nextInt(POW10[c.fractionDigits]) * POW10[9 - c.fractionDigits];
        // Offsets in 30-minute steps within the -18:00 to +18:00 limit
        c.offsetSeconds = (random.nextInt(73) - 36) * 30 * 60;
        return c;
    }

    @Test
    void formatParseFormatFixedFormat()
    {
        System.out.println("formatParseFormatFixedFormat: seed=" + SEED + ", iterations=" + ITERATIONS);
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () ->
        {
            final Random random = new Random(SEED);
            for (int i = 0; i < ITERATIONS; i++)
            {
                final Components c = randomComponents(random, 0);
                final OffsetDateTime value = c.toOffsetDateTime();

                final String text1 = ITU.format(value, c.fractionDigits);
                final OffsetDateTime parsed = ITU.parseDateTime(text1);
                final String text2 = ITU.format(parsed, c.fractionDigits);

                assertThat(parsed).as(c.describe()).isEqualTo(value);
                assertThat(text2).as(c.describe()).isEqualTo(text1);
            }
        });
    }

    @Test
    void parseFormatParseFixedFormat()
    {
        System.out.println("parseFormatParseFixedFormat: seed=" + SEED + ", iterations=" + ITERATIONS);
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () ->
        {
            final Random random = new Random(SEED);
            for (int i = 0; i < ITERATIONS; i++)
            {
                final Components c = randomComponents(random, 0);
                final String text = c.canonicalText();

                final OffsetDateTime parsed1 = ITU.parseDateTime(text);
                final String formatted = ITU.format(parsed1, c.fractionDigits);
                final OffsetDateTime parsed2 = ITU.parseDateTime(formatted);

                assertThat(formatted).as(c.describe()).isEqualTo(text);
                assertThat(parsed2).as(c.describe()).isEqualTo(parsed1);
            }
        });
    }

    @Test
    void parseFormatParseConfigurableParser()
    {
        System.out.println("parseFormatParseConfigurableParser: seed=" + SEED + ", iterations=" + ITERATIONS);
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () ->
        {
            final Random random = new Random(SEED);
            for (int i = 0; i < ITERATIONS; i++)
            {
                final Components c = randomComponents(random, 1);
                final String text = c.canonicalText();

                final ParsePosition pos = new ParsePosition(0);
                final DateTime parsed = configurableParser.parse(text, pos);

                assertThat(pos.getIndex()).as("cursor must consume the whole input: " + c.describe()).isEqualTo(text.length());
                assertThat(parsed.getYear()).as(c.describe()).isEqualTo(c.year);
                assertThat(parsed.getMonth()).as(c.describe()).isEqualTo(c.month);
                assertThat(parsed.getDayOfMonth()).as(c.describe()).isEqualTo(c.day);
                assertThat(parsed.getHour()).as(c.describe()).isEqualTo(c.hour);
                assertThat(parsed.getMinute()).as(c.describe()).isEqualTo(c.minute);
                assertThat(parsed.getSecond()).as(c.describe()).isEqualTo(c.second);
                assertThat(parsed.getNano()).as(c.describe()).isEqualTo(c.nano);
                assertThat(parsed.getFractionDigits()).as(c.describe()).isEqualTo(c.fractionDigits);
                assertThat(parsed.getOffset()).as(c.describe()).hasValueSatisfying(offset -> assertThat(offset.getTotalSeconds()).isEqualTo(c.offsetSeconds));

                // Round-trip the parsed value through the fixed formatter and back through the configurable parser
                final String formatted = ITU.format(parsed.toOffsetDatetime(), c.fractionDigits);
                final DateTime reparsed = configurableParser.parse(formatted, new ParsePosition(0));
                assertThat(reparsed.toOffsetDatetime()).as(c.describe()).isEqualTo(parsed.toOffsetDatetime());
                assertThat(formatted).as(c.describe()).isEqualTo(text);
            }
        });
    }
}
