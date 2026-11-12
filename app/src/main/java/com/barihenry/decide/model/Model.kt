package com.barihenry.decide.model

import java.util.UUID
import kotlin.math.sqrt

fun newId(): String = UUID.randomUUID().toString()

/**
 * Answers to the Happiness Test (Annie Duke, *How to Decide*, ch. 7): how much would this
 * outcome affect your happiness a week, a month, and a year later? Each is 0 (no effect)
 * to 5 (massive effect).
 */
data class Impact(val week: Int, val month: Int, val year: Int) {
    /**
     * Size of the outcome on a 0..10 scale. Longer-lasting effects count more
     * (weights 1, 2, 3 for week, month, year), so all-5s gives 10.
     */
    val magnitude: Double get() = (week + 2.0 * month + 3.0 * year) / 3.0

    companion object {
        const val MAX = 5
    }
}

/**
 * One possible result of picking an option.
 *
 * @param score how good (+) or bad (-) this result would be for you, on a -10..+10 scale.
 * @param probability your estimated chance of it happening, in percent (0..100).
 * @param impact the Happiness Test answers the score was derived from, or null if the
 *   score was set directly (or the outcome is neutral).
 */
data class Outcome(
    val id: String = newId(),
    val description: String,
    val score: Double,
    val probability: Double,
    val impact: Impact? = null,
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

/** One histogram bar: a distinct score and its probability (0..1). */
data class Bar(val score: Double, val probability: Double)

data class OptionStats(
    val expectedValue: Double,
    /** Probability-weighted contribution of the good outcomes (>= 0). EV = upside + downside. */
    val upside: Double,
    /** Probability-weighted contribution of the bad outcomes (<= 0). */
    val downside: Double,
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
    /** Mutually exclusive outcomes can't add up to more than 100%. */
    val overAllocated: Boolean get() = enteredTotal > 100.0 + EPS

    /**
     * Fine per the book: you list the *reasonable* outcomes, not every conceivable one,
     * so the total can be under 100%. The unlisted remainder is counted as neutral (score 0).
     */
    val incomplete: Boolean get() = enteredTotal < 100.0 - EPS

    /** Little or nothing to lose, something to gain (the book's "freeroll"). */
    val isFreeroll: Boolean get() = worst >= -FREEROLL_MAX_LOSS && best > 0

    companion object {
        private const val EPS = 0.5
        /** Outcomes no worse than this count as "insignificant" losses for a freeroll. */
        const val FREEROLL_MAX_LOSS = 1.0
    }
}

object Analysis {

    /** Options whose EVs are within this many points of the best are a "close call". */
    const val CLOSE_CALL_GAP = 1.0

    /**
     * Statistics of an option's outcome distribution, or null if it has no outcomes
     * with a positive probability.
     *
     * Probabilities are used as entered. If they add up to less than 100%, the unlisted
     * remainder (the long shots you didn't bother listing) counts as a neutral outcome with
     * score 0. If they add up to more than 100%, which is an input error, they're scaled down
     * to 100% so the stats are still usable.
     */
    fun stats(option: Option): OptionStats? {
        val outcomes = option.outcomes.filter { it.probability > 0.0 }
        val total = outcomes.sumOf { it.probability }
        if (outcomes.isEmpty() || total <= 0.0) return null

        val denominator = maxOf(total, 100.0)
        val p = outcomes.map { it.probability / denominator }
        val s = outcomes.map { it.score }
        val unlisted = 1.0 - p.sum() // neutral remainder, score 0
        val ev = p.indices.sumOf { p[it] * s[it] }
        val variance = p.indices.sumOf { p[it] * (s[it] - ev) * (s[it] - ev) } + unlisted * ev * ev
        val bars = outcomes.indices
            .groupBy { s[it] }
            .map { (score, idx) -> Bar(score, idx.sumOf { p[it] }) }
            .sortedBy { it.score }

        return OptionStats(
            expectedValue = ev,
            upside = p.indices.filter { s[it] > 0 }.sumOf { p[it] * s[it] },
            downside = p.indices.filter { s[it] < 0 }.sumOf { p[it] * s[it] },
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

    /**
     * "When a decision is hard, that means it's easy": if two or more options are within
     * [CLOSE_CALL_GAP] of the best EV, you can't be very wrong picking any of them.
     * Returns those options (best first), or an empty list if there's a clear winner.
     */
    fun closeCall(decision: Decision): List<Option> {
        val scored = rank(decision).mapNotNull { (o, s) -> s?.let { o to it } }
        if (scored.size < 2) return emptyList()
        val bestEv = scored.first().second.expectedValue
        val close = scored.filter { bestEv - it.second.expectedValue <= CLOSE_CALL_GAP + 1e-9 }
        return if (close.size >= 2) close.map { it.first } else emptyList()
    }
}

/** Score in -10..+10 from a good/bad judgement and Happiness Test answers. */
fun happinessScore(good: Boolean, impact: Impact): Double =
    (if (good) 1 else -1) * impact.magnitude
