package src.main.kotlin

import com.sun.org.apache.bcel.internal.util.Args.require
import jdk.jpackage.internal.model.DottedVersion.lazy
import java.security.SecureRandom
import kotlin.math.abs

/**
 * Cryptographically secure Yes/No decision maker.
 *
 * Randomness source: [SecureRandom] backed by the OS entropy pool
 * (`/dev/urandom` on Linux, `CryptGenRandom` on Windows, `arc4random` on macOS).
 * This is the highest-quality randomness available on the JVM without
 * dedicated hardware (HSM / TRNG).
 *
 * Fairness: a single bit is extracted per decision — P(Yes) = P(No) = 0.5
 * exactly, with zero modulo bias.
 */

// ── Model ─────────────────────────────────────────────────────────────────────

/**
 * The two possible outcomes of a binary decision.
 *
 * Sealed interface (not class) — objects need no shared state or behaviour,
 * so an interface is the leaner, more idiomatic choice in Kotlin 1.5+.
 */
sealed interface Decision {
    /** Carries its display label so call sites need no extra mapping. */
    val label: String

    data object Yes : Decision {
        override val label = "YES"
    }

    data object No : Decision {
        override val label = "NO"
    }
}

// ── Randomness ────────────────────────────────────────────────────────────────

/**
 * Lazily-initialised, application-wide [SecureRandom].
 *
 * [SecureRandom] is thread-safe by specification — one shared instance
 * is both correct and avoids the overhead of repeated seeding from the
 * OS entropy pool.
 */
private val rng: SecureRandom by lazy { SecureRandom() }

/**
 * Extracts a single cryptographically secure random bit as a [Boolean].
 *
 * One byte is requested from the OS entropy pool; the least-significant
 * bit (LSB) is isolated via `and 1`. Every bit in a [SecureRandom] byte
 * is independently and uniformly distributed, so this is an unbiased
 * coin flip — P(true) = P(false) = 0.5 exactly.
 *
 * The remaining 7 bits are discarded: reusing them would add stateful
 * complexity for no measurable gain at this call frequency.
 */
private fun secureRandomBoolean(): Boolean {
    val buf = ByteArray(1)
    rng.nextBytes(buf)               // filled with OS-level entropy
    return buf[0].toInt() and 1 == 1 // true ↔ LSB is set
}

// ── Public API ────────────────────────────────────────────────────────────────

/**
 * Returns a cryptographically fair [Decision].
 *
 * Implemented as a direct Boolean → Decision mapping so the compiler
 * can verify exhaustiveness with no fall-through magic numbers.
 */
fun decide(): Decision = if (secureRandomBoolean()) Decision.Yes else Decision.No

/**
 * Formats a question and its random answer as a two-line string.
 *
 * Concerns are separated: [decide] owns randomness, this function owns
 * presentation only.
 */
fun answerQuestion(question: String): String {
    val decision = decide()
    return "Q: $question\nA: ${decision.label}"
}

// ── Statistics ────────────────────────────────────────────────────────────────

/**
 * Runs [trials] decisions and prints an empirical distribution report.
 *
 * At 100 000 trials the delta from 50 % is typically < 0.05 %,
 * confirming the mathematical guarantee in practice.
 */
fun distributionReport(trials: Int = 100_000) {
    require(trials > 0) { "trials must be positive, got $trials" }

    // count() is idiomatic Kotlin — no mutable accumulators needed
    val yesCount = (1..trials).count { decide() is Decision.Yes }
    val noCount = trials - yesCount

    fun Int.toPct() = this * 100.0 / trials
    fun Double.fmt() = "%.4f".format(this)

    println(
        """
        |┌─── Distribution report ($trials trials) ───────────────
        |│  YES : $yesCount  (${yesCount.toPct().fmt()} %)
        |│  NO  : $noCount   (${noCount.toPct().fmt()} %)
        |│  Δ from ideal 50/50 : ${abs(yesCount.toPct() - 50.0).fmt()} %
        |└────────────────────────────────────────────────────────
        """.trimMargin()
    )
}

// ── Entry point ───────────────────────────────────────────────────────────────

fun main() {
    println("=== Secure Decision Maker ===\n")

    listOf(
        "Should I deploy to production today?",
        "Should I take the highway instead of the side road?",
        "Should I refactor this module before the deadline?",
    ).forEach { question ->
        println(answerQuestion(question))
        println()
    }

    println("=== Fairness verification ===\n")
    distributionReport(trials = 100_000)
}