package dev.gavenda.yuuka.data.local.dao

import androidx.room.*
import dev.gavenda.yuuka.data.local.entity.*
import kotlinx.coroutines.flow.Flow

/*
 * Two shapes of write live here now.
 *
 * `replaceAll` is the sync: the API answered, and what it said is the whole
 * truth for that list. `upsert`/`deleteById` are the offline-first half — the
 * row the user just made, written before the server has seen it, so the screens
 * observing these flows update at once. The server's answer replaces it when
 * the outbox drains.
 */

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY name")
    fun observeAll(): Flow<List<AccountEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<AccountEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: AccountEntity)

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun byId(id: String): AccountEntity?

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM accounts")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(accounts: List<AccountEntity>) {
        clear()
        insertAll(accounts)
    }
}

@Dao
interface AccountTypeDao {
    @Query("SELECT * FROM account_types ORDER BY sortOrder")
    fun observeAll(): Flow<List<AccountTypeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(types: List<AccountTypeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(type: AccountTypeEntity)

    @Query("SELECT * FROM account_types WHERE id = :id")
    suspend fun byId(id: String): AccountTypeEntity?

    @Query("DELETE FROM account_types WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM account_types")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(types: List<AccountTypeEntity>) {
        clear()
        insertAll(types)
    }
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun byId(id: String): CategoryEntity?

    /** Deleting a category never destroys history: the transactions that wore it fall back to uncategorised. */
    @Query("UPDATE transactions SET categoryId = NULL, categoryName = NULL, categoryColor = NULL WHERE categoryId = :id")
    suspend fun uncategorise(id: String)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: String)

    @Transaction
    suspend fun deleteAndUncategorise(id: String) {
        uncategorise(id)
        deleteById(id)
    }

    @Query("DELETE FROM categories")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(categories: List<CategoryEntity>) {
        clear()
        insertAll(categories)
    }
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tags: List<TagEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tag: TagEntity)

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun byId(id: String): TagEntity?

    /** Only the label comes off — the transactions stay, so just the links go. */
    @Query("DELETE FROM transaction_tags WHERE tagId = :id")
    suspend fun unlink(id: String)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteById(id: String)

    @Transaction
    suspend fun deleteAndUnlink(id: String) {
        unlink(id)
        deleteById(id)
    }

    @Query("DELETE FROM tags")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(tags: List<TagEntity>) {
        clear()
        insertAll(tags)
    }
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 0")
    fun observe(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 0")
    suspend fun current(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: SettingsEntity)
}

@Dao
interface RoundUpRuleDao {
    @Query("SELECT * FROM round_up_rule WHERE id = 0")
    fun observe(): Flow<RoundUpRuleEntity?>

    @Query("SELECT * FROM round_up_rule WHERE id = 0")
    suspend fun current(): RoundUpRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: RoundUpRuleEntity)
}
