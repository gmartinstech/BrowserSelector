package com.browserselector.android.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a URL pattern rule that maps URLs to specific browsers.
 */
@Entity(
    tableName = "url_rules",
    foreignKeys = [
        ForeignKey(
            entity = Browser::class,
            parentColumns = ["packageName"],
            childColumns = ["browserPackage"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("browserPackage")]
)
data class UrlRule(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val pattern: String,

    val browserPackage: String,

    val priority: Int = 0,

    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Creates a copy with updated priority.
     */
    fun withPriority(priority: Int): UrlRule = copy(priority = priority)
}
