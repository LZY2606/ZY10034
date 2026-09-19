# Testing Notes

## New test suites

| File | Tests | Focus |
| --- | --- | --- |
| `src/test/java/com/ethlo/time/ParserBoundaryAndErrorTest.java` | 19 | Fixed-format RFC-3339 entry (`ITU`): leap-year February, 24:00 boundary, fraction precision (0/9/10 digits), ±18:00 offset limits, `-00:00`, missing/truncated tokens, trailing junk, Unicode digits |
| `src/test/java/com/ethlo/time/DurationParserBoundaryTest.java` | 13 | `ITU.parseDuration`: sign placement, negative fractions, `long` overflow, duplicate/out-of-order units, empty/truncated input, Unicode digits |
| `src/test/java/com/ethlo/time/token/ConfigurableParserErrorTest.java` | 9 | Configurable token parser (`DateTimeParsers.of`): duplicate field tokens, Unicode digits, wrong/missing separators, truncation, 10-digit fractions |
| `src/test/java/com/ethlo/time/SeededRoundTripTest.java` | 4 | Fixed-seed (`20260919`) randomized round-trips: format-parse-format and parse-format-parse via `ITU`, configurable parser vs fixed parser, duration `normalized()` round-trip. Each bounded to 5 s via `assertTimeoutPreemptively`; on failure the seed and minimal component set are printed |

Every failure-case test asserts three things: the exact exception type, the exact reported
position (exception `errorIndex` and/or `ParsePosition` index/errorIndex), and that the parse
cursor stops at the offending character so subsequent input is not swallowed. Positions are
asserted as exact indices, not ranges.

## Mutation check (performed 2026-09-19, reverted)

**Mutation:** in `ITUParser.parseLenient(String, ParseConfig, ParsePosition)`
(`src/main/java/com/ethlo/time/internal/fixed/ITUParser.java`), the failure path was changed from
`position.setIndex(position.getErrorIndex())` to `position.setIndex(position.getErrorIndex() + 1)`,
i.e. the parse cursor advances one extra character when a parse fails.

**Result:** 9 of the new tests failed (requirement: at least 2):

- `ParserBoundaryAndErrorTest.fractionTenDigitsRejected`
- `ParserBoundaryAndErrorTest.fractionWithoutDigitsRejected`
- `ParserBoundaryAndErrorTest.offsetNegativeZeroRejected`
- `ParserBoundaryAndErrorTest.trailingJunkAfterOffsetRejected`
- `ParserBoundaryAndErrorTest.trailingJunkAfterZuluRejected`
- `ParserBoundaryAndErrorTest.truncatedAfterDateTimeSeparatorRejected`
- `ParserBoundaryAndErrorTest.truncatedAfterTimeSeparatorRejected`
- `ParserBoundaryAndErrorTest.unicodeArabicIndicFractionDigitsRejected`
- `ParserBoundaryAndErrorTest.unicodeFullWidthDigitsRejected`

The production code was restored to its original state after the check (`git diff src/main` is
empty), and the full suite was re-run green with `mvn -q test`.
