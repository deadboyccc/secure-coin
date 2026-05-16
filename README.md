# secure-coin

> Cryptographically secure Yes/No decision maker for the JVM.  
> Single-bit `SecureRandom` extraction — zero modulo bias, mathematically guaranteed 50/50 distribution.

A single-source Kotlin CLI: [`src/main/kotlin/securedecision.kt`](src/main/kotlin/securedecision.kt).  
No third-party libraries — only the Kotlin standard library and `java.security.SecureRandom`.

---

## Table of contents

1. [Quick start](#quick-start)
2. [Source file overview](#source-file-overview)
3. [How it works](#how-it-works)
4. [Public API](#public-api)
5. [Entry point](#entry-point)
6. [Design decisions](#design-decisions)
7. [Requirements](#requirements)
8. [License](#license)

---

## Quick start

**Prerequisites:** JDK 21, no global Gradle install required (wrapper included).

```bash
# Build
./gradlew build

# Run the demo (three sample questions + fairness report)
./gradlew run
```

**Sample output**

```
=== Secure Decision Maker ===

Q: Should I deploy to production today?
A: NO

Q: Should I take the highway instead of the side road?
A: YES

Q: Should I refactor this module before the deadline?
A: YES

=== Fairness verification ===

┌─── Distribution report (100000 trials) ───────────────
│  YES : 49974  (49.9740 %)
│  NO  : 50026  (50.0260 %)
│  Δ from ideal 50/50 : 0.0260 %
└────────────────────────────────────────────────────────
```

At 100 000 trials, the empirical split is typically within ~0.05 % of 50/50.

---

## Source file overview

Everything lives in one file, organised into labelled sections:

| Section | Lines (approx.) | Purpose |
|---------|-----------------|---------|
| File-level KDoc | Top | Documents randomness source and fairness guarantee |
| Model | `Decision` | Type-safe Yes/No outcomes |
| Randomness | `rng`, `secureRandomBoolean()` | OS-backed entropy and single-bit extraction |
| Public API | `decide()`, `answerQuestion()` | Decision logic and formatted output |
| Statistics | `distributionReport()` | Empirical fairness verification |
| Entry point | `main()` | Demo runner |

### `Decision` — the outcome model

```kotlin
sealed interface Decision {
    val label: String
    data object Yes : Decision { override val label = "YES" }
    data object No  : Decision { override val label = "NO" }
}
```

- **`sealed interface`** — only `Yes` and `No` exist; the compiler enforces exhaustive `when` branches.
- **`data object`** — singleton outcomes with stable identity (Kotlin 1.9+).
- **`label`** — display string owned by each outcome, so callers never map enums to strings manually.

### `rng` — shared `SecureRandom`

```kotlin
private val rng: SecureRandom by lazy { SecureRandom() }
```

- **`SecureRandom`** — cryptographically strong; seeded from the OS entropy pool, not a reproducible PRNG.
- **`by lazy`** — the entropy pool is not touched until the first decision (useful for short-lived processes).
- **Single instance** — `SecureRandom` is thread-safe by specification; one shared instance is correct and efficient.

### `secureRandomBoolean()` — unbiased bit extraction

```kotlin
private fun secureRandomBoolean(): Boolean {
    val buf = ByteArray(1)
    rng.nextBytes(buf)
    return buf[0].toInt() and 1 == 1
}
```

1. Request one random byte (8 bits of OS entropy).
2. Isolate the least-significant bit with `and 1`.
3. Return `true` if the LSB is set, `false` otherwise.

Every bit in a `SecureRandom` byte is independently uniform, so **P(true) = P(false) = 0.5** exactly.  
The other seven bits are discarded to avoid stateful bit-buffering complexity.

### `decide()` — core decision

```kotlin
fun decide(): Decision = if (secureRandomBoolean()) Decision.Yes else Decision.No
```

Maps the boolean directly to `Decision.Yes` or `Decision.No` — no magic integers, no modulo arithmetic.

### `answerQuestion()` — presentation layer

```kotlin
fun answerQuestion(question: String): String {
    val decision = decide()
    return "Q: $question\nA: ${decision.label}"
}
```

Randomness stays in `decide()`; this function only formats output.

### `distributionReport()` — empirical verification

Runs `trials` calls to `decide()` (default 100 000), counts outcomes, and prints a boxed report with YES/NO counts, percentages, and delta from ideal 50/50.

Uses `require(trials > 0)` for input validation and `count { }` for idiomatic tallies.

### `main()` — demo entry point

1. Prints three sample questions via `answerQuestion()`.
2. Runs `distributionReport(trials = 100_000)` to verify fairness in practice.

Gradle runs this via `mainClass = SecuredecisionKt` (Kotlin’s JVM facade for top-level `main` in `securedecision.kt`).

---

## How it works

Most yes/no helpers use `Random.nextInt(2)` or `nextBoolean()`.  
`secure-coin` optimises for two properties: **randomness quality** and **mathematical fairness**.

### Randomness — `SecureRandom`

`java.security.SecureRandom` delegates to the OS entropy pool:

| Platform | Source |
|----------|--------|
| Linux    | `/dev/urandom` (non-blocking CSPRNG seeded from hardware events) |
| Windows  | `CryptGenRandom` |
| macOS    | `arc4random` |

`java.util.Random` and `kotlin.random.Random` are deterministic PRNGs — same seed, same sequence.  
`SecureRandom` is seeded from physical entropy that cannot be predicted or reproduced.

### Fairness — single-bit extraction

```
rng.nextBytes(buf)           // 8 bits of OS entropy
buf[0].toInt() and 1 == 1   // isolate the LSB
```

No `% 2`, no range rejection, no rounding — one bit, one decision, exact 50/50 probability.

---

## Public API

Use these from Kotlin (or JVM languages via `SecuredecisionKt`):

```kotlin
// Single decision
val answer: Decision = decide()   // Decision.Yes or Decision.No

// Formatted question + answer
println(answerQuestion("Should I deploy to production today?"))
// Q: Should I deploy to production today?
// A: YES

// Empirical distribution check
distributionReport(trials = 100_000)
```

| Function | Returns | Description |
|----------|---------|-------------|
| `decide()` | `Decision` | One cryptographically fair Yes/No outcome |
| `answerQuestion(question)` | `String` | Two-line `Q:` / `A:` formatted result |
| `distributionReport(trials)` | `Unit` | Prints statistical report to stdout |

---

## Entry point

| Task | Command |
|------|---------|
| Compile & package | `./gradlew build` |
| Run demo | `./gradlew run` |
| Install distribution | `./gradlew installDist` → `build/install/secure-coin/bin/secure-coin` |

**Toolchain (see `build.gradle.kts`):**

| Tool | Version |
|------|---------|
| Gradle | 9.5.1 (wrapper) |
| Kotlin | 2.3.21 |
| JDK | 21 |

---

## Design decisions

| Choice | Rationale |
|--------|-----------|
| `sealed interface Decision` | Exhaustive handling at compile time; leaner than a class when there is no shared behaviour |
| `data object` + `label` | Singleton outcomes; display text lives on the type, not at call sites |
| `by lazy { SecureRandom() }` | Defer entropy access; one thread-safe instance |
| Boolean LSB extraction | Exact P = 0.5; avoids modulo bias from `nextInt() % 2` |
| Discard upper 7 bits | Simpler than a bit buffer; negligible cost at this scale |
| `decide()` vs `answerQuestion()` | Separation of randomness and presentation |
| No external dependencies | Minimal attack surface; stdlib + `java.security` only |
| Single source file | Entire behaviour is auditable in one place |

---

## Requirements

- **JDK 21** (configured via Gradle JVM toolchain)
- **Gradle 9.5.1** — use `./gradlew`; no separate install needed

---

## License

This project is licensed under the **MIT License**.  
See [LICENSE](LICENSE) for the full text.

Copyright (c) 2026 Ahmed M. Ali
