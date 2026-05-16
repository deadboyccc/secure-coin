import java.security.SecureRandom

/**
 * A cryptographically secure Yes/No decision maker.
 *
 * Randomness source: [SecureRandom] backed by the OS entropy pool
 * (/dev/urandom on Linux, CryptGenRandom on Windows, arc4random on macOS).
 * This is the highest-quality randomness available on the JVM without
 * dedicated hardware (HSM / TRNG).
 *
 * Fairness guarantee: we extract exactly ONE bit from the entropy stream.
 * A single bit is mathematically guaranteed to be 0 or 1 with P = 0.5 each,
 * so there is zero modulo-bias — unlike the naive nextInt() % 2, which
 * can skew for asymmetric ranges.
 */

// ── Sealed hierarchy ──────────────────────────────────────────────────────────

/** Represents the two possible outcomes of a binary decision. */
sealed class Decision {
    object Yes : Decision()
    object No  : Decision()
}

// ── Core randomness ───────────────────────────────────────────────────────────

/**
 * A lazily-initialised, application-wide [SecureRandom] instance.
 *
 * [SecureRandom] is thread-safe, so a single shared instance is both
 * correct and efficient — no need for per-thread instances or pooling.
 *
 * We use `lazy` so the OS entropy pool is not accessed until the first
 * actual use (important for short-lived CLI tools that might exit before
 * ever calling the generator).
 */
private val secureRandom: SecureRandom by lazy { SecureRandom() }

/**
 * Returns a single cryptographically secure random bit (0 or 1).
 *
 * Strategy:
 *  1. Ask [SecureRandom] for one random byte (8 bits of entropy).
 *  2. Isolate the least-significant bit with a bitwise AND (and 0x01).
 *     Every bit in a SecureRandom byte is independently and uniformly
 *     distributed, so the LSB is an unbiased coin flip.
 *  3. Discard the remaining 7 bits — we only need one decision per call,
 *     and reusing bits across calls would require state and add complexity
 *     for no real performance gain at this scale.
 *
 * @return 0 (No) or 1 (Yes)
 */
private fun secureRandomBit(): Int {
    val singleByte = ByteArray(1)
    secureRandom.nextBytes(singleByte)   // fills with OS-level entropy
    return singleByte[0].toInt() and 0x01 // isolate the LSB
}

// ── Public API ────────────────────────────────────────────────────────────────

/**
 * Makes a single cryptographically fair binary decision.
 *
 * The mapping is explicit and deterministic given the bit:
 *   - bit == 1  →  Decision.Yes
 *   - bit == 0  →  Decision.No
 *
 * Both branches have exactly P = 0.5 by construction.
 */
fun decide(): Decision = when (secureRandomBit()) {
    1    -> Decision.Yes
    else -> Decision.No
}

/**
 * Answers a question with a [Decision], returning a human-readable string.
 *
 * @param question The question to answer (used purely for display).
 */
fun answerQuestion(question: String): String {
    val decision = decide()
    val answer = when (decision) {
        is Decision.Yes -> "YES"
        is Decision.No  -> "NO"
    }
    return "Q: $question\nA: $answer"
}

// ── Statistics helper ─────────────────────────────────────────────────────────

/**
 * Runs [trials] decisions and prints a distribution report.
 *
 * Useful for empirically verifying the 50/50 guarantee.
 * At 100 000 trials the empirical split should be within ~0.2 % of 50 %.
 *
 * @param trials Number of decisions to simulate (default 100 000).
 */
fun distributionReport(trials: Int = 100_000) {
    require(trials > 0) { "trials must be positive, got $trials" }

    var yesCount = 0
    var noCount  = 0

    repeat(trials) {
        when (decide()) {
            is Decision.Yes -> yesCount++
            is Decision.No  -> noCount++
        }
    }

    val yesPct = yesCount * 100.0 / trials
    val noPct  = noCount  * 100.0 / trials
    val delta  = Math.abs(yesPct - 50.0)

    println(
        """
        |┌─── Distribution report ($trials trials) ───────────────
        |│  YES : $yesCount  (${"%.4f".format(yesPct)} %)
        |│  NO  : $noCount   (${"%.4f".format(noPct)} %)
        |│  Δ from ideal 50/50 : ${"%.4f".format(delta)} %
        |└────────────────────────────────────────────────────────
        """.trimMargin()
    )
}

// ── Entry point ───────────────────────────────────────────────────────────────

fun main() {
    println("=== Secure Decision Maker ===\n")

    // 1. Answer a few concrete questions.
    val questions = listOf(
        "Should I deploy to production today?",
        "Should I take the highway instead of the side road?",
        "Should I refactor this module before the deadline?"
    )

    questions.forEach { println(answerQuestion(it) + "\n") }

    // 2. Empirically verify the distribution is fair.
    println("=== Fairness verification ===\n")
    distributionReport(trials = 100_000)
}
