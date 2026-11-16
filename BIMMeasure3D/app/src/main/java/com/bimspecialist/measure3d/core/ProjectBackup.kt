package com.bimspecialist.measure3d.core

import android.content.Context

data class BackupContents(val projects: Int, val measurements: Int)

/**
 * A single SQLite transaction restores every project and measurement.
 * It is never allowed to partially replace the current database.
 */
object ProjectBackup {
    fun export(context: Context): ByteArray =
        SnapshotCodec.encode(FieldDatabase.get(context).snapshot())

    fun inspect(bytes: ByteArray): BackupContents {
        val data = SnapshotCodec.decode(bytes)
        return BackupContents(data.projects.size, data.measurements.size)
    }

    fun restore(context: Context, bytes: ByteArray): BackupContents {
        val data = SnapshotCodec.decode(bytes)
        FieldDatabase.get(context).restore(data)
        return BackupContents(data.projects.size, data.measurements.size)
    }
}
