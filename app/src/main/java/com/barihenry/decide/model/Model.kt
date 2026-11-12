package com.barihenry.decide.model

import java.util.UUID
import kotlin.math.abs
import kotlin.math.sqrt

fun newId(): String = UUID.randomUUID().toString()

/**
 * One possible result of picking an option.
 *
 * @param score how good (+) or bad (-) this result would be for you. Suggested scale -10..+10.
 * @param probability your estimated chance of it happening, in percent (0..100).
 */
data class Outcome(
    val id: String = newId(),
    val description: String,
    val score: Double,
    val probability: Double,
)

/** A choice you could make. Options within a decision are treated as mutually exclusive. */
data class Option(
    val id: String = newId(),
    val name: String,
    val outcomes: List<Outcome> = emptyList(),
)

data class Decision(
    val id: String = newId(),
    val title: String,
    val options: List<Option> = emptyList(),
    val chosenOptionId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** One histogram bar: a distinct score and its (normalized, 0..1) probability. */
data class Bar(val score: Double, val probability: Double)

data class OptionStats(
    val expectedValue: Double,
    val stdDev: Double,
    /** Chance (0..1) of an outcome with a negative score. */
    val chanceOfBad: Double,
    val worst: Double,
    val best: Double,
    /** Raw sum of the probabilities as entered, in percent. */
    val enteredTotal: Double,
    /** Bars sorted by score; outcomes with equal scores are merged. */
    val bars: List<Bar>,
) {
    val sumsTo100: Boolean get() = abs(enteredTotal - 100.0) < 0.5
}

object Analysis {

    /**
     * Statistics of an option's outcome distribution, or null if it has no outcomes
     * with a positive probability.
     *
     * If the entered probabilities don't add up to 100%, they are treated as relative
     * weights and rescaled so they do.
     */
    fun stats(option: Option): OptionStats? {
        val outcomes = option.outcomes.filter { it.probability > 0.0 }
        val total = outcomes.sumOf { it.probability }
        if (outcomes.isEmpty() || total <= 0.0) return null

        val p = outcomes.map { it.probability / total }
        val s = outcomes.map { it.score }
        val ev = p.indices.sumOf { p[it] * s[it] }
        val variance = p.indices.sumOf { p[it] * (s[it] - ev) * (s[it] - ev) }
        val bars = outcomes.indices
            .groupBy { s[it] }
            .map { (score, idx) -> Bar(score, idx.sumOf { p[it] }) }
            .sortedBy { it.score }

        return OptionStats(
            expectedValue = ev,
            stdDev = sqrt(variance),
            chanceOfBad = p.indices.filter { s[it] < 0 }.sumOf { p[it] },
            worst = s.min(),
            best = s.max(),
            enteredTotal = total,
            bars = bars,
        )
    }

    /** Options ordered best-first by expected value; options without stats go last. */
    fun rank(decision: Decision): List<Pair<Option, OptionStats?>> =
        decision.options
            .map { it to stats(it) }
            .sortedWith(compareByDescending<Pair<Option, OptionStats?>> { it.second != null }
                .thenByDescending { it.second?.expectedValue ?: 0.0 })
}
