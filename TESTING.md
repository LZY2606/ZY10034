# Testing notes

## New tests (2026-09-20)

Three test classes were added to strengthen coverage of truncated input, error positions,
and parse/format round-trips:

- `com.ethlo.time.ParserEdgeCasesTest` (30 tests) — fixed-format parser (`ITUParser` via `ITU`)
  and duration parser (`ItuDurationParser`): leap-year February, 24:00 boundary, fraction
  precision (1/9/10 digits), ±18:00 offset limits, `-00:00` rejection, missing tokens,
  duplicate duration units, duration sign/overflow, trailing junk, and rejection of
  Unicode (Arabic-Indic, fullwidth) digits. Every failure case asserts the exact exception
  type, the exact error index, and that the parse cursor stops at the error without
  swallowing subsequent characters.
- `com.ethlo.time.token.ConfigurableParserEdgeCasesTest` (8 tests) — the same style of
  assertions for the token-driven `ConfigurableDateTimeParser` entry point, including
  duplicate-field token rejection and trailing characters left unconsumed on success.
- `com.ethlo.time.SeededRoundTripTest` (3 tests) — fixed seed (`20240929`), 500 iterations
  of randomly generated legal components through parse-format-parse and format-parse-format
  for both the fixed-format (`ITU`) and configurable parser entry points. Failures print the
  seed and the minimal field set; each test is bounded to 5 seconds.

## Bug fix uncovered by the new tests

`ItuDurationParser` reported a stale error position when a duration overflowed
(`ArithmeticException` from `Math.addExact`/`multiplyExact` was translated in the caller,
where the cursor no longer reflected the overflowing digit/unit). The translation now happens
inside `readUntilNonDigit`, so `PT99999999999999999999S` reports index 20 (the overflowing
digit) and `PT9999999999999999H` reports index 18 (the `H` unit). Covered by
`ParserEdgeCasesTest#durationOverflowOnDigitAccumulation` and
`ParserEdgeCasesTest#durationOverflowOnUnitMultiplication`, both of which failed before the fix.

## Mutation check

Mutation applied (and afterwards reverted): make the parse cursor advance one extra position
when a parse fails —

- `ITUParser#parseLenient(String, ParseConfig, ParsePosition)`: `position.setIndex(position.getErrorIndex() + 1)`
- `ConfigurableDateTimeParser#parse`: `parsePosition.setIndex(exc.getErrorIndex() + 1)`

Result: 14 of the new tests failed (requirement: at least 2):

- `ParserEdgeCasesTest`: `fractionTenDigitsRejected`, `trailingJunkAfterZulu`,
  `trailingJunkAfterNumericOffset`, `negativeZeroOffsetRejected`,
  `missingMinutesAfterHourSeparator`, `missingDateTimeSeparator`,
  `arabicIndicDigitsInYearRejected`, `fullwidthDigitsInYearRejected`,
  `arabicIndicDigitsInFractionRejected`
- `ConfigurableParserEdgeCasesTest`: `unicodeDigitsInSecondRejected`,
  `unicodeDigitsInMonthRejected`, `truncatedInputMissingMinuteToken`,
  `missingSeparatorToken`, `truncatedZoneOffset`

The production code was restored after the check; `git diff` shows no changes to
`ITUParser.java` or `ConfigurableDateTimeParser.java`.

## Verification

- Preparation: `mvn -q -DskipTests package`
- Acceptance: `mvn -q test` (from the repository root, exit code 0)
