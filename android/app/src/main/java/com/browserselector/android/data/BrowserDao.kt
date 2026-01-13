package com.browserselector.android.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.browserselector.android.model.Browser
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Browser entities.
 */
@Dao
interface BrowserDao {

    @Query("SELECT * FROM browsers ORDER BY name ASC")
    fun getAllBrowsers(): Flow<List<Browser>>

    @Query("SELECT * FROM browsers ORDER BY name ASC")
    suspend fun getAllBrowsersList(): List<Browser>

    @Query("SELECT * FROM browsers WHERE enabled = 1 ORDER BY name ASC")
    fun getEnabledBrowsers(): Flow<List<Browser>>

    @Query("SELECT * FROM browsers WHERE enabled = 1 ORDER BY name ASC")
    suspend fun getEnabledBrowsersList(): List<Browser>

    @Query("SELECT * FROM browsers WHERE packageName = :packageName")
    suspend fun getBrowser(packageName: String): Browser?

    @Query("SELECT * FROM browsers WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultBrowser(): Browser?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBrowser(browser: Browser)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBrowsers(browsers: List<Browser>)

    @Update
    suspend fun updateBrowser(browser: Browser)

    @Delete
    suspend fun deleteBrowser(browser: Browser)

    @Query("DELETE FROM browsers WHERE packageName = :packageName")
    suspend fun deleteBrowserByPackage(packageName: String)

    @Query("UPDATE browsers SET enabled = :enabled WHERE packageName = :packageName")
    suspend fun setEnabled(packageName: String, enabled: Boolean)

    @Query("UPDATE browsers SET isDefault = 0")
    suspend fun clearDefaultBrowser()

    @Query("UPDATE browsers SET isDefault = 1 WHERE packageName = :packageName")
    suspend fun setDefaultBrowser(packageName: String)

    @Query("SELECT COUNT(*) FROM browsers")
    suspend fun getBrowserCount(): Int
}
