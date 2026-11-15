package com.bimspecialist.measure3d.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class FieldProject(val id: String, val name: String, val createdAtMillis: Long)

class ProjectStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("bim_measure_projects_v1", Context.MODE_PRIVATE)
    private val key = "projects"
    private val selectedKey = "selected"
    private val defaultProject = FieldProject("general", "General", 0L)

    fun load(): List<FieldProject> = try {
        val raw = JSONArray(prefs.getString(key, "[]") ?: "[]")
        val found = buildList {
            for (i in 0 until raw.length()) {
                val item = raw.optJSONObject(i) ?: continue
                val id = item.optString("id")
                val name = item.optString("name")
                if (id.isNotBlank() && name.isNotBlank() && id != defaultProject.id) {
                    add(FieldProject(id, name, item.optLong("createdAtMillis")))
                }
            }
        }
        listOf(defaultProject) + found.distinctBy { it.id }.take(30)
    } catch (_: Exception) {
        listOf(defaultProject)
    }

    fun selectedId(): String {
        val id = prefs.getString(selectedKey, defaultProject.id) ?: defaultProject.id
        return id.takeIf { candidate -> load().any { it.id == candidate } } ?: defaultProject.id
    }

    fun select(id: String) {
        require(load().any { it.id == id }) { "Unknown project" }
        prefs.edit().putString(selectedKey, id).apply()
    }

    fun create(rawName: String): FieldProject {
        val name = rawName.trim()
        require(name.length in 2..40) { "Project name must contain 2 to 40 characters" }
        val current = load()
        require(current.size < 31) { "Project limit reached" }
        require(current.none { it.name.equals(name, ignoreCase = true) }) { "Project already exists" }
        val project = FieldProject(UUID.randomUUID().toString(), name, System.currentTimeMillis())
        val data = JSONArray()
        (current.filter { it.id != defaultProject.id } + project).forEach { p ->
            data.put(JSONObject().put("id", p.id).put("name", p.name)
                .put("createdAtMillis", p.createdAtMillis))
        }
        check(prefs.edit().putString(key, data.toString()).commit()) { "Cannot save project" }
        select(project.id)
        return project
    }
}
