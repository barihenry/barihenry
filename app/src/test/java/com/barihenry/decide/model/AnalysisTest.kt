package com.barihenry.decide.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisTest {
    private fun opt(name: String, vararg o: Pair<Double, Double>) =
        Option(name = name, outcomes = o.map { (score, prob) -> Outcome(description = "", score = score, probability = prob) })

    @Test
    fun expectedValueAndSpread() {
        val s = Analysis.stats(opt("a", 10.0 to 50.0, -10.0 to 50.0))!!
        assertEquals(0.0, s.expectedValue, 1e-9)
        assertEquals(10.0, s.stdDev, 1e-9)
        assertEquals(0.5, s.chanceOfBad, 1e-9)
        assertEquals(-10.0, s.worst, 1e-9)
        assertEquals(10.0, s.best, 1e-9)
        assertFalse(s.incomplete)
        assertFalse(s.overAllocated)
    }

    @Test
    fun upsideAndDownsideSumToExpectedValue() {
        val s = Analysis.stats(opt("a", 8.0 to 30.0, 3.0 to 45.0, -6.0 to 25.0))!!
        assertEquals(0.3 * 8 + 0.45 * 3, s.upside, 1e-9)
        assertEquals(0.25 * -6, s.downside, 1e-9)
        assertEquals(s.upside + s.downside, s.expectedValue, 1e-9)
    }

    @Test
    fun underHundredPercentCountsTheRestAsNeutral() {
        val s = Analysis.stats(opt("a", 4.0 to 30.0, -1.0 to 10.0))!!
        assertTrue(s.incomplete)
        assertFalse(s.overAllocated)
        assertEquals(40.0, s.enteredTotal, 1e-9)
        assertEquals(0.3 * 4 - 0.1 * 1, s.expectedValue, 1e-9)
        assertEquals(0.1, s.chanceOfBad, 1e-9)
        assertEquals(listOf(Bar(-1.0, 0.1), Bar(4.0, 0.3)), s.bars)
        // Variance includes the 60% neutral remainder at score 0.
        val ev = s.expectedValue
        val variance = 0.3 * (4 - ev) * (4 - ev) + 0.1 * (-1 - ev) * (-1 - ev) + 0.6 * ev * ev
        assertEquals(kotlin.math.sqrt(variance), s.stdDev, 1e-9)
    }

    @Test
    fun overHundredPercentIsFlagged() {
        val s = Analysis.stats(opt("a", 4.0 to 70.0, 1.0 to 50.0))!!
        assertTrue(s.overAllocated)
        assertEquals((70 * 4.0 + 50 * 1.0) / 120, s.expectedValue, 1e-9)
    }

    @Test
    fun equalScoresMergeIntoOneBar() {
        val s = Analysis.stats(opt("a", 2.0 to 20.0, 2.0 to 30.0, -1.0 to 50.0))!!
        assertEquals(listOf(Bar(-1.0, 0.5), Bar(2.0, 0.5)), s.bars)
    }

    @Test
    fun emptyOrZeroProbabilityGivesNull() {
        assertNull(Analysis.stats(opt("a")))
        assertNull(Analysis.stats(opt("a", 5.0 to 0.0)))
    }

    @Test
    fun rankingIsByExpectedValueWithIncompleteOptionsLast() {
        val d = Decision(
            title = "d",
            options = listOf(opt("empty"), opt("low", -2.0 to 100.0), opt("high", 3.0 to 100.0)),
        )
        assertEquals(listOf("high", "low", "empty"), Analysis.rank(d).map { it.first.name })
    }

    @Test
    fun happinessTestScore() {
        assertEquals(10.0, happinessScore(true, Impact(5, 5, 5)), 1e-9)
        assertEquals(-10.0, happinessScore(false, Impact(5, 5, 5)), 1e-9)
        assertEquals(0.0, happinessScore(true, Impact(0, 0, 0)), 1e-9)
        // Weights are proportional to days: a year counts 365/7 times as much as a week.
        val total = 7 + 365.0 / 12 + 365
        // A good meal: noticeable for a week, gone after that.
        assertEquals(7 * 1 / total * 2, happinessScore(true, Impact(1, 0, 0)), 1e-9)
        // A great vacation: big for a week, fading over the year.
        assertEquals((7 * 5 + 365.0 / 12 * 4 + 365 * 2) / total * 2, happinessScore(true, Impact(5, 4, 2)), 1e-9)
        assertEquals(365.0 / 7, Impact(0, 0, 1).magnitude / Impact(1, 0, 0).magnitude, 1e-9)
    }

    @Test
    fun freeroll() {
        assertTrue(Analysis.stats(opt("ask them out", 8.0 to 20.0, -0.5 to 80.0))!!.isFreeroll)
        assertFalse(Analysis.stats(opt("risky", 8.0 to 20.0, -3.0 to 80.0))!!.isFreeroll)
        assertFalse(Analysis.stats(opt("nothing to gain", 0.0 to 100.0))!!.isFreeroll)
    }

    @Test
    fun closeCall() {
        val paris = opt("Paris", 6.0 to 100.0)
        val rome = opt("Rome", 5.5 to 100.0)
        val cannery = opt("Trout cannery", -4.0 to 100.0)
        assertEquals(listOf(paris, rome), Analysis.closeCall(Decision(title = "trip", options = listOf(cannery, rome, paris))))
        assertEquals(emptyList<Option>(), Analysis.closeCall(Decision(title = "trip", options = listOf(paris, cannery))))
        assertEquals(emptyList<Option>(), Analysis.closeCall(Decision(title = "trip", options = listOf(paris))))
    }
}
