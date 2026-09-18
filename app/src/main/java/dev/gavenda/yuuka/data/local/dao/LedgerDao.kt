package dev.gavenda.yuuka.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import dev.gavenda.yuuka.data.local.entity.AccountEntity
import dev.gavenda.yuuka.data.local.entity.AccountTypeEntity
import dev.gavenda.yuuka.data.local.entity.CategoryEntity
import dev.gavenda.yuuka.data.local.entity.RoundUpRuleEntity
import dev.gavenda.yuuka.data.local.entity.SettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY name")
    fun observeAll(): Flow<List<AccountEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<AccountEntity>)

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

    @Query("DELETE FROM categories")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(categories: List<CategoryEntity>) {
        clear()
        insertAll(categories)
    }
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 0")
    fun observe(): Flow<SettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: SettingsEntity)
}

@Dao
interface RoundUpRuleDao {
    @Query("SELECT * FROM round_up_rule WHERE id = 0")
    fun observe(): Flow<RoundUpRuleEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: RoundUpRuleEntity)
}
