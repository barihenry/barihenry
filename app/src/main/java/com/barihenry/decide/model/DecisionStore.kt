package com.barihenry.decide.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Holds all decisions in memory (as Compose state) and persists them to a JSON file. */
class DecisionStore(private val file: File) {

    var decisions: List<Decision> by mutableStateOf(load())
        private set

    fun decision(id: String): Decision? = decisions.find { it.id == id }

    fun add(decision: Decision) = set(decisions + decision)

    fun delete(id: String) = set(decisions.filterNot { it.id == id })

    fun update(id: String, change: (Decision) -> Decision) =
        set(decisions.map { if (it.id == id) change(it) else it })

    fun updateOption(decisionId: String, optionId: String, change: (Option) -> Option) =
        update(decisionId) { d -> d.copy(options = d.options.map { if (it.id == optionId) change(it) else it }) }

    private fun set(new: List<Decision>) {
        decisions = new
        save()
    }

    private fun load(): List<Decision> = try {
        if (!file.exists()) emptyList() else {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { decisionFromJson(arr.getJSONObject(it)) }
        }
    } catch (e: Exception) {
        // Keep the unreadable file around rather than silently overwriting it.
        file.renameTo(File(file.parentFile, file.name + ".corrupt"))
        emptyList()
    }

    private fun save() {
        val arr = JSONArray()
        decisions.forEach { arr.put(decisionToJson(it)) }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }

    private fun decisionToJson(d: Decision) = JSONObject().apply {
        put("id", d.id)
        put("title", d.title)
        put("createdAt", d.createdAt)
        d.chosenOptionId?.let { put("chosenOptionId", it) }
        put("options", JSONArray().apply { d.options.forEach { put(optionToJson(it)) } })
    }

    private fun optionToJson(o: Option) = JSONObject().apply {
        put("id", o.id)
        put("name", o.name)
        put("outcomes", JSONArray().apply {
            o.outcomes.forEach {
                put(JSONObject().apply {
                    put("id", it.id)
                    put("description", it.description)
                    put("score", it.score)
                    put("probability", it.probability)
                })
            }
        })
    }

    private fun decisionFromJson(j: JSONObject) = Decision(
        id = j.getString("id"),
        title = j.getString("title"),
        createdAt = j.optLong("createdAt"),
        chosenOptionId = if (j.has("chosenOptionId")) j.getString("chosenOptionId") else null,
        options = j.getJSONArray("options").let { arr -> (0 until arr.length()).map { optionFromJson(arr.getJSONObject(it)) } },
    )

    private fun optionFromJson(j: JSONObject) = Option(
        id = j.getString("id"),
        name = j.getString("name"),
        outcomes = j.getJSONArray("outcomes").let { arr ->
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Outcome(
                    id = o.getString("id"),
                    description = o.getString("description"),
                    score = o.getDouble("score"),
                    probability = o.getDouble("probability"),
                )
            }
        },
    )
}
