@file:OptIn(ExperimentalMaterial3Api::class)

package com.barihenry.decide.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.barihenry.decide.model.Impact
import com.barihenry.decide.model.Outcome
import com.barihenry.decide.model.happinessScore
import kotlin.math.roundToInt

@Composable
fun TextPromptDialog(
    title: String,
    question: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    initial: String = "",
    placeholder: String = "",
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(question)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(placeholder) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun scoreMeaning(s: Double): String = when {
    s <= -8 -> "disastrous"
    s <= -5 -> "very bad"
    s <= -2 -> "bad"
    s < 0 -> "slightly bad"
    s == 0.0 -> "neutral"
    s < 2 -> "slightly good"
    s < 5 -> "good"
    s < 8 -> "very good"
    else -> "fantastic"
}


/** Which question opened the outcome dialog. */
enum class OutcomePrompt { PREMORTEM, BACKCAST, EDIT }

private enum class Valence { GOOD, NEUTRAL, BAD }

/**
 * Asks the questions that define an outcome:
 *  - what happens (framed as a premortem or a backcast, per *How to Decide* ch. 8),
 *  - whether it's good or bad and how much it would affect your happiness a week, a month
 *    and a year later (the Happiness Test, ch. 7), which sets the score, and
 *  - how likely it is.
 *
 * A "set the score directly" switch falls back to a -10..+10 slider.
 */
@Composable
fun OutcomeDialog(
    optionName: String,
    existing: Outcome?,
    prompt: OutcomePrompt,
    /** Percent not yet taken by the option's other outcomes. */
    remainingPercent: Double,
    hasOtherOutcomes: Boolean,
    onDismiss: () -> Unit,
    onSave: (Outcome) -> Unit,
) {
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var valence by remember {
        mutableStateOf(
            when {
                existing == null -> if (prompt == OutcomePrompt.PREMORTEM) Valence.BAD else Valence.GOOD
                existing.score > 0 -> Valence.GOOD
                existing.score < 0 -> Valence.BAD
                else -> Valence.NEUTRAL
            }
        )
    }
    val start = existing?.impact
    var week by remember { mutableStateOf(start?.week ?: -1) }
    var month by remember { mutableStateOf(start?.month ?: -1) }
    var year by remember { mutableStateOf(start?.year ?: -1) }
    // Outcomes saved with a non-zero score but no Happiness Test answers were scored directly.
    var direct by remember { mutableStateOf(existing != null && existing.impact == null && existing.score != 0.0) }
    var slider by remember { mutableFloatStateOf(existing?.score?.toFloat()?.coerceIn(-10f, 10f) ?: 0f) }

    var probText by remember {
        mutableStateOf(
            existing?.probability?.let(::fmt)
                ?: if (hasOtherOutcomes && remainingPercent > 0) fmt(remainingPercent) else ""
        )
    }
    val prob = probText.trim().removeSuffix("%").trim().replace(',', '.').toDoubleOrNull()
    val probError = when {
        probText.isBlank() -> null
        prob == null || prob <= 0 || prob > 100 -> "Enter a number between 0 and 100"
        prob > remainingPercent + 0.05 ->
            "Only ${fmt(remainingPercent)}% is left. Outcomes can't add up to more than 100%; lower another one first."
        else -> null
    }

    val impactDone = week >= 0 && month >= 0 && year >= 0
    val score: Double? = when {
        direct -> slider.toDouble()
        valence == Valence.NEUTRAL -> 0.0
        impactDone -> happinessScore(valence == Valence.GOOD, Impact(week, month, year))
        else -> null
    }
    val canSave = description.isNotBlank() && prob != null && probError == null && score != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New outcome" else "Edit outcome") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when (prompt) {
                        OutcomePrompt.PREMORTEM ->
                            "Imagine it's some time from now and choosing \"$optionName\" went badly. What happened?"
                        OutcomePrompt.BACKCAST ->
                            "Imagine it's some time from now and choosing \"$optionName\" went well. What happened?"
                        OutcomePrompt.EDIT -> "If you choose \"$optionName\", what could happen?"
                    }
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = { Text(if (prompt == OutcomePrompt.PREMORTEM) "e.g. My new manager is awful" else "e.g. I love the work and get promoted") },
                    supportingText = {
                        if (prompt != OutcomePrompt.EDIT) Text("Think of reasons in your control and outside it.")
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (direct) {
                    Text("How good or bad would that be for you?")
                    ScoreLine(slider.toDouble())
                    Slider(
                        value = slider,
                        onValueChange = { slider = (it * 2).roundToInt() / 2f },
                        valueRange = -10f..10f,
                        steps = 39, // half-point increments
                    )
                    Caption("-10 = disastrous · 0 = neutral · +10 = fantastic")
                } else {
                    Text("Would that be good or bad for you?")
                    Choice(
                        labels = listOf("Bad", "Neutral", "Good"),
                        selected = when (valence) { Valence.BAD -> 0; Valence.NEUTRAL -> 1; Valence.GOOD -> 2 },
                        onSelect = { valence = listOf(Valence.BAD, Valence.NEUTRAL, Valence.GOOD)[it] },
                    )
                    if (valence != Valence.NEUTRAL) {
                        Text("How much would it affect your happiness…")
                        ImpactRow("a week later", week) { week = it }
                        ImpactRow("a month later", month) { month = it }
                        ImpactRow("a year later", year) { year = it }
                        Caption("0 = no effect · 5 = massive effect. Each answer counts in proportion to its length of time, so the year matters most.")
                    }
                    score?.let { ScoreLine(it) }
                }
                TextButton(onClick = {
                    if (!direct) slider = (score ?: 0.0).toFloat()
                    direct = !direct
                }) { Text(if (direct) "Use the happiness questions instead" else "Set the score directly instead") }

                Text("How likely is it to happen?")
                OutlinedTextField(
                    value = probText,
                    onValueChange = { probText = it },
                    suffix = { Text("%") },
                    singleLine = true,
                    isError = probError != null,
                    supportingText = { Text(probError ?: "${fmt(remainingPercent)}% not yet assigned to other outcomes") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    val base = existing ?: Outcome(description = "", score = 0.0, probability = 0.0)
                    onSave(
                        base.copy(
                            description = description.trim(),
                            score = score!!,
                            probability = prob!!,
                            impact = if (!direct && valence != Valence.NEUTRAL) Impact(week, month, year) else null,
                        )
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ScoreLine(score: Double) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Score ${signed(score)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("  (${scoreMeaning(score)})", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ImpactRow(label: String, value: Int, onChange: (Int) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Choice(labels = (0..Impact.MAX).map { it.toString() }, selected = value, onSelect = onChange)
    }
}

/** A row of mutually exclusive buttons; [selected] is an index, or -1 for none. */
@Composable
private fun Choice(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { i, label ->
            SegmentedButton(
                selected = i == selected,
                onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = labels.size),
                icon = {},
            ) { Text(label) }
        }
    }
}
