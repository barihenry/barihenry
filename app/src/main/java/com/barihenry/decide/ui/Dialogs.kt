package com.barihenry.decide.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.barihenry.decide.model.Outcome
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

private fun scoreMeaning(s: Float): String = when {
    s <= -8 -> "disastrous"
    s <= -5 -> "very bad"
    s <= -2 -> "bad"
    s < 0 -> "slightly bad"
    s == 0f -> "neutral"
    s < 2 -> "slightly good"
    s < 5 -> "good"
    s < 8 -> "very good"
    else -> "fantastic"
}

/**
 * Asks the three questions that define an outcome: what happens, how good/bad it is
 * (score on a -10..+10 slider), and how likely it is (percent).
 */
@Composable
fun OutcomeDialog(
    optionName: String,
    existing: Outcome?,
    remainingPercent: Double,
    onDismiss: () -> Unit,
    onSave: (Outcome) -> Unit,
) {
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var score by remember { mutableFloatStateOf(existing?.score?.toFloat()?.coerceIn(-10f, 10f) ?: 0f) }
    var probText by remember {
        mutableStateOf(existing?.probability?.let(::fmt) ?: if (remainingPercent > 0) fmt(remainingPercent) else "")
    }
    val prob = probText.trim().removeSuffix("%").trim().replace(',', '.').toDoubleOrNull()
    val probValid = prob != null && prob > 0 && prob <= 100

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New outcome" else "Edit outcome") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("If you choose \"$optionName\", what could happen?")
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = { Text("e.g. I enjoy the work and get promoted") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("How good or bad would that be for you?")
                Row {
                    Text(
                        "${if (score > 0) "+" else ""}${fmt(score.toDouble())}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("  (${scoreMeaning(score)})", style = MaterialTheme.typography.titleMedium)
                }
                Slider(
                    value = score,
                    onValueChange = { score = (it * 2).roundToInt() / 2f },
                    valueRange = -10f..10f,
                    steps = 39, // half-point increments
                )
                Text(
                    "-10 = disastrous · 0 = neutral · +10 = fantastic",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text("How likely is it to happen?")
                OutlinedTextField(
                    value = probText,
                    onValueChange = { probText = it },
                    suffix = { Text("%") },
                    singleLine = true,
                    isError = probText.isNotBlank() && !probValid,
                    supportingText = {
                        Text(
                            if (probText.isNotBlank() && !probValid) "Enter a number between 0 and 100"
                            else "${fmt(remainingPercent)}% not yet assigned to other outcomes"
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = description.isNotBlank() && probValid,
                onClick = {
                    val base = existing ?: Outcome(description = "", score = 0.0, probability = 0.0)
                    onSave(base.copy(description = description.trim(), score = score.toDouble(), probability = prob!!))
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
