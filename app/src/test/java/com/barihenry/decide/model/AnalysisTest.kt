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
        assertTrue(s.sumsTo100)
    }

    @Test
    fun probabilitiesNotSummingTo100AreRescaled() {
        val s = Analysis.stats(opt("a", 4.0 to 30.0, 1.0 to 10.0))!!
        assertFalse(s.sumsTo100)
        assertEquals(40.0, s.enteredTotal, 1e-9)
        assertEquals(0.75 * 4 + 0.25 * 1, s.expectedValue, 1e-9)
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
            options = listOf(
                opt("empty"),
                opt("low", -2.0 to 100.0),
                opt("high", 3.0 to 100.0),
            ),
        )
        assertEquals(listOf("high", "low", "empty"), Analysis.rank(d).map { it.first.name })
    }
}
