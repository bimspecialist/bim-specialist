package com.bimspecialist.measure3d.core

import android.content.Context

data class FieldProject(val id: String, val name: String, val createdAtMillis: Long)

/** Projects, selection and display units share one transactional database. */
class ProjectStore(context: Context) {
    private val db = FieldDatabase.get(context)

    fun load(): List<FieldProject> = db.projects()
    fun selectedId(): String = db.selectedProject()
    fun select(id: String) = db.selectProject(id)
    fun create(rawName: String): FieldProject = db.createProject(rawName)
    fun unit(): DisplayUnit = db.unit()
    fun saveUnit(unit: DisplayUnit) = db.saveUnit(unit)
}
