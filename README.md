# secure-coin

> Cryptographically secure Yes/No decision maker for the JVM.  
> Single-bit `SecureRandom` extraction — zero modulo bias, mathematically guaranteed 50/50 distribution.

---

## How it works

Most random yes/no implementations reach for `Random.nextInt(2)` or `nextBoolean()`.  
`secure-coin` goes further on two axes: **randomness quality** and **mathematical fairness**.

### Randomness — `SecureRandom`

`java.security.SecureRandom` delegates to the OS entropy pool, not an algorithmic PRNG:

| Platform | Source |
|----------|--------|
| Linux    | `/dev/urandom` (non-blocking CSPRNG seeded from hardware events) |
| Windows  | `CryptGenRandom` |
| macOS    | `arc4random` |

`java.util.Random` and `kotlin.random.Random` are deterministic; given the same seed they produce the same sequence. `SecureRandom` does not — it is seeded from physical entropy that cannot be predicted or reproduced.

### Fairness — single-bit extraction

```
secureRandom.nextBytes(singleByte)     // 8 bits of OS entropy
singleByte[0].toInt() and 0x01        // isolate the LSB
```

Every bit in a `SecureRandom` byte is independently and uniformly distributed.  
Isolating one bit gives exactly **P(Yes) = P(No) = 0.5** — no modulo arithmetic, no rounding, no asymmetry.

The remaining 7 bits are discarded to avoid stateful bit-reuse complexity.

---

## Usage

```kotlin
// Single decision
val answer: Decision = decide()   // Decision.Yes or Decision.No

// Answer a question
println(answerQuestion("Should I deploy to production today?"))
// Q: Should I deploy to production today?
// A: YES

// Verify the distribution empirically
distributionReport(trials = 100_000)
```

### Sample output

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

0.026 % delta from perfect 50/50 across 100 000 live trials.

---

## Design decisions

| Choice | Rationale |
|--------|-----------|
| `sealed class Decision` | Exhaustive `when` — compiler enforces all branches are handled; no stringly-typed outcomes |
| `by lazy { SecureRandom() }` | Entropy pool not touched until first call; single shared instance is thread-safe by spec |
| Single-bit LSB extraction | Mathematically zero-bias; simpler than bit-buffering the other 7 bits |
| No external dependencies | Zero attack surface; pure stdlib + `java.security` |

---

## Running

```bash
# Compile
kotlinc SecureDecision.kt -include-runtime -d secure-coin.jar

# Run
java -jar secure-coin.jar
```

Requires JDK 11+ and Kotlin 1.6+.

---

## License

MIT
