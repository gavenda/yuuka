package dev.gavenda.yuuka.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.gavenda.yuuka.data.local.dao.OutboxDao
import dev.gavenda.yuuka.data.local.entity.OutboxEntity

/**
 * The unsent queue, in a database of its own.
 *
 * [YuukaDatabase] is a cache of the API and says so: it falls back to a
 * destructive migration, because a schema change there costs nothing but a full
 * sync. The outbox cannot live under that rule. A row in it is a change the
 * user made that the server has never seen, and dropping the table on an app
 * update would throw away work — quietly, and exactly on the upgrade where a
 * user is most likely to have been offline.
 *
 * So it is separate, small, and migrated properly. When this schema changes,
 * write the migration; do not add a destructive fallback here.
 */
@Database(entities = [OutboxEntity::class], version = 1, exportSchema = true)
abstract class OutboxDatabase : RoomDatabase() {
    abstract fun outboxDao(): OutboxDao
}
