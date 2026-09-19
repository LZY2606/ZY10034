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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * Deterministic (fixed-seed) round-trip tests. Legal components are generated at random and run
 * through parse-format-parse and format-parse-format on both the fixed-format entry point
 * ({@link ITU}) and the configurable token-driven parser ({@link DateTimeParsers#of}), comparing
 * structural values and canonical text. On failure the seed and the minimal component set needed
 * to reproduce are printed. Each test must complete within 5 seconds.
 */
public class SeededRoundTripTest
{
    private static final long SEED = 20260919L;
    private static final int ITERATIONS = 2_000;
    private static final int[] POW10 = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000, 1_000_000_000};

    private static final class Components
    {
        private final int year;
        private final int month;
        private final int day;
        private final int hour;
        private final int minute;
        private final int second;
        private final int fractionDigits;
        private final int nanos;
        private final int offsetSeconds;

        private Components(final Random random, final int minFractionDigits)
        {
            this.year = random.nextInt(10_000);
            this.month = 1 + random.nextInt(12);
            this.day = 1 + random.nextInt(28);
            this.hour = random.nextInt(24);
            this.minute = random.nextInt(60);
            this.second = random.nextInt(60);
            this.fractionDigits = minFractionDigits + random.nextInt(10 - minFractionDigits);
            this.nanos = fractionDigits == 0 ? 0 : random.nextInt(POW10[fractionDigits]) * POW10[9 - fractionDigits];
            this.offsetSeconds = (random.nextInt(2 * 1_080 + 1) - 1_080) * 60;
        }

        private OffsetDateTime toOffsetDateTime()
        {
            return OffsetDateTime.of(year, month, day, hour, minute, second, nanos, ZoneOffset.ofTotalSeconds(offsetSeconds));
        }

        private String toText()
        {
            final StringBuilder sb = new StringBuilder(String.format("%04d-%02d-%02dT%02d:%02d:%02d", year, month, day, hour, minute, second));
            if (fractionDigits > 0)
            {
                final String fraction = Integer.toString(nanos / POW10[9 - fractionDigits]);
                sb.append('.');
                for (int i = fraction.length(); i < fractionDigits; i++)
                {
                    sb.append('0');
                }
                sb.append(fraction);
            }
            if (offsetSeconds == 0)
            {
                sb.append('Z');
            }
            else
            {
                final int abs = Math.abs(offsetSeconds);
                sb.append(offsetSeconds < 0 ? '-' : '+');
                sb.append(String.format("%02d:%02d", abs / 3_600, (abs % 3_600) / 60));
            }
            return sb.toString();
        }

        @Override
        public String toString()
        {
            return "seed=" + SEED + ", year=" + year + ", month=" + month + ", day=" + day
                    + ", hour=" + hour + ", minute=" + minute + ", second=" + second
                    + ", fractionDigits=" + fractionDigits + ", nanos=" + nanos
                    + ", offsetSeconds=" + offsetSeconds;
        }
    }

    @Test
    void formatParseFormatFixedFormat()
    {
        final Random random = new Random(SEED);
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () ->
        {
            for (int i = 0; i < ITERATIONS; i++)
            {
                final Components c = new Components(random, 0);
                try
                {
                    final OffsetDateTime expected = c.toOffsetDateTime();
                    final String text = ITU.format(expected, c.fractionDigits);
                    final OffsetDateTime parsed = ITU.parseDateTime(text);
                    final String reformatted = ITU.format(parsed, c.fractionDigits);
                    assertThat(parsed).isEqualTo(expected);
                    assertThat(reformatted).isEqualTo(text);
                }
                catch (AssertionError | RuntimeException e)
                {
                    System.out.println("formatParseFormatFixedFormat failed at iteration " + i + ": " + c);
                    throw e;
                }
            }
        });
    }

    @Test
    void parseFormatParseFixedFormat()
    {
        final Random random = new Random(SEED);
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () ->
        {
            for (int i = 0; i < ITERATIONS; i++)
            {
                final Components c = new Components(random, 0);
                try
                {
                    final String text = c.toText();
                    final OffsetDateTime parsed = ITU.parseDateTime(text);
                    final String formatted = ITU.format(parsed, c.fractionDigits);
                    final OffsetDateTime reparsed = ITU.parseDateTime(formatted);
                    assertThat(formatted).isEqualTo(text);
                    assertThat(reparsed).isEqualTo(parsed);
                    assertThat(parsed.getYear()).isEqualTo(c.year);
                    assertThat(parsed.getMonthValue()).isEqualTo(c.month);
                    assertThat(parsed.getDayOfMonth()).isEqualTo(c.day);
                    assertThat(parsed.getHour()).isEqualTo(c.hour);
                    assertThat(parsed.getMinute()).isEqualTo(c.minute);
                    assertThat(parsed.getSecond()).isEqualTo(c.second);
                    assertThat(parsed.getNano()).isEqualTo(c.nanos);
                    assertThat(parsed.getOffset()).isEqualTo(ZoneOffset.ofTotalSeconds(c.offsetSeconds));
                }
                catch (AssertionError | RuntimeException e)
                {
                    System.out.println("parseFormatParseFixedFormat failed at iteration " + i + ": " + c);
                    throw e;
                }
            }
        });
    }

    @Test
    void configurableParserMatchesFixedParser()
    {
        final DateTimeParser configurable = DateTimeParsers.of(
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
        final DateTimeParser fixed = DateTimeParsers.rfc3339();
        final Random random = new Random(SEED);
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () ->
        {
            for (int i = 0; i < ITERATIONS; i++)
            {
                // The configurable format under test always carries a fraction, so at least 1 digit
                final Components c = new Components(random, 1);
                try
                {
                    final String text = c.toText();
                    final ParsePosition pos = new ParsePosition(0);
                    final DateTime configurableResult = configurable.parse(text, pos);
                    final DateTime fixedResult = fixed.parse(text);
                    assertThat(pos.getIndex()).isEqualTo(text.length());
                    assertThat(configurableResult.getYear()).isEqualTo(c.year);
                    assertThat(configurableResult.getMonth()).isEqualTo(c.month);
                    assertThat(configurableResult.getDayOfMonth()).isEqualTo(c.day);
                    assertThat(configurableResult.getHour()).isEqualTo(c.hour);
                    assertThat(configurableResult.getMinute()).isEqualTo(c.minute);
                    assertThat(configurableResult.getSecond()).isEqualTo(c.second);
                    assertThat(configurableResult.getNano()).isEqualTo(c.nanos);
                    assertThat(configurableResult.getFractionDigits()).isEqualTo(c.fractionDigits);
                    assertThat(configurableResult.getOffset().isPresent()).isTrue();
                    assertThat(configurableResult.getOffset().get().getTotalSeconds()).isEqualTo(c.offsetSeconds);
                    assertThat(configurableResult.getOffset().get().getTotalSeconds()).isEqualTo(fixedResult.getOffset().get().getTotalSeconds());
                }
                catch (AssertionError | RuntimeException e)
                {
                    System.out.println("configurableParserMatchesFixedParser failed at iteration " + i + ": " + c);
                    throw e;
                }
            }
        });
    }

    @Test
    void durationNormalizeParseRoundTrip()
    {
        final Random random = new Random(SEED);
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () ->
        {
            for (int i = 0; i < ITERATIONS; i++)
            {
                final long seconds = Math.floorMod(random.nextLong(), 1_000_000_000_000_000L) * (random.nextBoolean() ? 1 : -1);
                final int nanos = random.nextInt(1_000_000_000);
                try
                {
                    final Duration expected = Duration.of(seconds, nanos);
                    final String text = expected.normalized();
                    final Duration parsed = ITU.parseDuration(text);
                    final String reformatted = parsed.normalized();
                    assertThat(parsed).isEqualTo(expected);
                    assertThat(reformatted).isEqualTo(text);
                }
                catch (AssertionError | RuntimeException e)
                {
                    System.out.println("durationNormalizeParseRoundTrip failed at iteration " + i
                            + ": seed=" + SEED + ", seconds=" + seconds + ", nanos=" + nanos);
                    throw e;
                }
            }
        });
    }
}
