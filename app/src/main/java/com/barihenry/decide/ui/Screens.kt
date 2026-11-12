@file:OptIn(ExperimentalMaterial3Api::class)

package com.barihenry.decide.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.barihenry.decide.model.Analysis
import com.barihenry.decide.model.Decision
import com.barihenry.decide.model.DecisionStore
import com.barihenry.decide.model.Option
import com.barihenry.decide.model.OptionStats
import com.barihenry.decide.model.Outcome

private val listPadding = PaddingValues(16.dp)

// ---------------------------------------------------------------------------------------------
// Home: list of decisions
// ---------------------------------------------------------------------------------------------

@Composable
fun HomeScreen(store: DecisionStore, onOpen: (String) -> Unit) {
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Decide") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New decision") },
            )
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = listPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (store.decisions.isEmpty()) {
                item {
                    Hint(
                        "Facing a choice? Add a decision, list your options, and for each option " +
                            "describe what could happen, how good or bad it would be, and how likely it is. " +
                            "You'll get a probability histogram and the expected value of every option."
                    )
                }
            }
            items(store.decisions.sortedByDescending { it.createdAt }, key = { it.id }) { d ->
                val ranked = Analysis.rank(d)
                val best = ranked.firstOrNull()?.takeIf { it.second != null }?.first
                val chosen = d.options.find { it.id == d.chosenOptionId }
                Card(onClick = { onOpen(d.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(d.title, style = MaterialTheme.typography.titleMedium)
                        val sub = buildString {
                            append("${d.options.size} option${if (d.options.size == 1) "" else "s"}")
                            when {
                                chosen != null -> append(" · chose: ${chosen.name}")
                                best != null -> append(" · best EV: ${best.name}")
                            }
                        }
                        Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (creating) {
        TextPromptDialog(
            title = "New decision",
            question = "What are you trying to decide?",
            placeholder = "e.g. Should I take the new job?",
            confirmLabel = "Create",
            onDismiss = { creating = false },
            onConfirm = {
                val d = Decision(title = it)
                store.add(d)
                creating = false
                onOpen(d.id)
            },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Decision: compare options
// ---------------------------------------------------------------------------------------------

@Composable
fun DecisionScreen(
    store: DecisionStore,
    decision: Decision,
    onOpenOption: (String) -> Unit,
    onBack: () -> Unit,
) {
    var addingOption by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val ranked = Analysis.rank(decision)
    val xRange = xRangeFor(ranked.map { it.second })
    val scored = ranked.filter { it.second != null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(decision.title, maxLines = 2) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Default.Edit, "Rename") }
                    IconButton(onClick = { deleting = true }) { Icon(Icons.Default.Delete, "Delete") }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = listPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (decision.options.isEmpty()) {
                item { Hint("What are your options? Add each choice you're considering. They're treated as mutually exclusive: you'll pick exactly one.") }
            }

            if (scored.isNotEmpty()) {
                item { SummaryCard(decision, scored.map { it.first to it.second!! }) }
            }

            items(ranked, key = { it.first.id }) { (option, stats) ->
                OptionCard(
                    option = option,
                    stats = stats,
                    xRange = xRange,
                    isBest = stats != null && option == scored.first().first,
                    isChosen = option.id == decision.chosenOptionId,
                    onOpen = { onOpenOption(option.id) },
                    onChoose = {
                        store.update(decision.id) {
                            it.copy(chosenOptionId = if (it.chosenOptionId == option.id) null else option.id)
                        }
                    },
                )
            }

            item {
                OutlinedButton(onClick = { addingOption = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add option")
                }
            }
        }
    }

    if (addingOption) {
        TextPromptDialog(
            title = "New option",
            question = if (decision.options.isEmpty()) "What's one option you have?" else "What's another option you have?",
            placeholder = "e.g. Take the job",
            confirmLabel = "Add",
            onDismiss = { addingOption = false },
            onConfirm = { name ->
                val o = Option(name = name)
                store.update(decision.id) { it.copy(options = it.options + o) }
                addingOption = false
                onOpenOption(o.id)
            },
        )
    }
    if (renaming) {
        TextPromptDialog(
            title = "Rename decision",
            question = "What are you trying to decide?",
            initial = decision.title,
            confirmLabel = "Save",
            onDismiss = { renaming = false },
            onConfirm = { t -> store.update(decision.id) { it.copy(title = t) }; renaming = false },
        )
    }
    if (deleting) {
        ConfirmDialog(
            text = "Delete \"${decision.title}\" and all its options?",
            onDismiss = { deleting = false },
            onConfirm = { deleting = false; onBack(); store.delete(decision.id) },
        )
    }
}

@Composable
private fun SummaryCard(decision: Decision, scored: List<Pair<Option, OptionStats>>) {
    val (best, bestStats) = scored.first()
    val chosen = decision.options.find { it.id == decision.chosenOptionId }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Highest expected value", style = MaterialTheme.typography.labelMedium)
            Text(
                "${best.name}  (EV ${fmt(bestStats.expectedValue)})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (scored.size > 1) {
                val (second, secondStats) = scored[1]
                val gap = bestStats.expectedValue - secondStats.expectedValue
                Text(
                    if (gap < 1e-9) "Tied with ${second.name}."
                    else "Ahead of ${second.name} by ${fmt(gap)} points.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                val safest = scored.minBy { it.second.chanceOfBad }
                if (safest.first != best && safest.second.chanceOfBad < bestStats.chanceOfBad) {
                    Text(
                        "Lowest chance of a bad outcome: ${safest.first.name} " +
                            "(${pct(safest.second.chanceOfBad)} vs ${pct(bestStats.chanceOfBad)}).",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (chosen != null) {
                Text(
                    if (chosen == best) "You chose ${chosen.name} ✓"
                    else "You chose ${chosen.name}, which isn't the highest-EV option.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun OptionCard(
    option: Option,
    stats: OptionStats?,
    xRange: ClosedFloatingPointRange<Double>,
    isBest: Boolean,
    isChosen: Boolean,
    onOpen: () -> Unit,
    onChoose: () -> Unit,
) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    option.name + if (isBest) "  ★" else "",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (stats != null) {
                    Text("EV ${fmt(stats.expectedValue)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            if (stats == null) {
                Text(
                    "No outcomes yet. Tap to describe what could happen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Histogram(stats, xRange = xRange, height = 56.dp, detailed = false)
                Text(
                    "Spread ±${fmt(stats.stdDev)} · bad outcome ${pct(stats.chanceOfBad)} · " +
                        "range ${fmt(stats.worst)} to ${fmt(stats.best)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!stats.sumsTo100) ProbabilityWarning(stats)
                if (isChosen) {
                    Button(onClick = onChoose) {
                        Icon(Icons.Default.Check, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Chosen")
                    }
                } else {
                    FilledTonalButton(onClick = onChoose) { Text("Choose this") }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Option: outcomes and their histogram
// ---------------------------------------------------------------------------------------------

@Composable
fun OptionScreen(store: DecisionStore, decision: Decision, option: Option, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<Outcome?>(null) }
    var addingOutcome by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val stats = Analysis.stats(option)

    fun save(outcomes: List<Outcome>) = store.updateOption(decision.id, option.id) { it.copy(outcomes = outcomes) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(option.name, maxLines = 2) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Default.Edit, "Rename") }
                    IconButton(onClick = { deleting = true }) { Icon(Icons.Default.Delete, "Delete") }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = listPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (option.outcomes.isEmpty()) {
                item {
                    Hint(
                        "If you choose \"${option.name}\", what could happen? Add each outcome you can think of, " +
                            "rate how good or bad it would be for you, and estimate how likely it is."
                    )
                }
            }
            if (stats != null) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Histogram(stats)
                            StatRow("Expected value", fmt(stats.expectedValue))
                            StatRow("Spread (std. dev.)", "±${fmt(stats.stdDev)}")
                            StatRow("Chance of a bad outcome", pct(stats.chanceOfBad))
                            StatRow("Worst / best case", "${fmt(stats.worst)} / ${fmt(stats.best)}")
                            if (!stats.sumsTo100) ProbabilityWarning(stats)
                        }
                    }
                }
            }
            items(option.outcomes, key = { it.id }) { o ->
                Card(onClick = { editing = o }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(o.description, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Score ${if (o.score > 0) "+" else ""}${fmt(o.score)} · ${fmt(o.probability)}% likely",
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    o.score > 0 -> MaterialTheme.colorScheme.primary
                                    o.score < 0 -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        IconButton(onClick = { save(option.outcomes - o) }) { Icon(Icons.Default.Delete, "Delete outcome") }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { addingOutcome = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add outcome")
                }
            }
        }
    }

    if (addingOutcome || editing != null) {
        val current = editing
        val othersTotal = option.outcomes.filter { it.id != current?.id }.sumOf { it.probability }
        OutcomeDialog(
            optionName = option.name,
            existing = current,
            remainingPercent = (100.0 - othersTotal).coerceAtLeast(0.0),
            onDismiss = { addingOutcome = false; editing = null },
            onSave = { o ->
                save(if (current == null) option.outcomes + o else option.outcomes.map { if (it.id == o.id) o else it })
                addingOutcome = false
                editing = null
            },
        )
    }
    if (renaming) {
        TextPromptDialog(
            title = "Rename option",
            question = "Option name",
            initial = option.name,
            confirmLabel = "Save",
            onDismiss = { renaming = false },
            onConfirm = { n -> store.updateOption(decision.id, option.id) { it.copy(name = n) }; renaming = false },
        )
    }
    if (deleting) {
        ConfirmDialog(
            text = "Delete the option \"${option.name}\"?",
            onDismiss = { deleting = false },
            onConfirm = {
                deleting = false
                onBack()
                store.update(decision.id) { d ->
                    d.copy(
                        options = d.options.filterNot { it.id == option.id },
                        chosenOptionId = d.chosenOptionId.takeIf { it != option.id },
                    )
                }
            },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Small shared pieces
// ---------------------------------------------------------------------------------------------

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProbabilityWarning(stats: OptionStats) {
    Text(
        "Probabilities add up to ${fmt(stats.enteredTotal)}%, not 100%. " +
            "They're treated as relative weights and scaled to 100%.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}
