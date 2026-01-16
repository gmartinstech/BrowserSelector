package com.browserselector.android.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for UrlRule model.
 */
class UrlRuleTest {

    // ==================== withPriority Tests ====================

    @Test
    fun `withPriority returns copy with updated priority`() {
        val rule = UrlRule(
            id = 1,
            pattern = "*.google.com",
            browserPackage = "com.android.chrome",
            priority = 0
        )

        val updatedRule = rule.withPriority(10)

        // Original should be unchanged
        assertEquals(0, rule.priority)

        // New copy should have updated value
        assertEquals(10, updatedRule.priority)

        // Other properties should remain the same
        assertEquals(rule.id, updatedRule.id)
        assertEquals(rule.pattern, updatedRule.pattern)
        assertEquals(rule.browserPackage, updatedRule.browserPackage)
        assertEquals(rule.createdAt, updatedRule.createdAt)
    }

    @Test
    fun `withPriority handles negative priorities`() {
        val rule = UrlRule(
            id = 1,
            pattern = "example.com",
            browserPackage = "org.mozilla.firefox",
            priority = 5
        )

        val updatedRule = rule.withPriority(-1)
        assertEquals(-1, updatedRule.priority)
    }

    @Test
    fun `withPriority handles zero priority`() {
        val rule = UrlRule(
            id = 1,
            pattern = "example.com",
            browserPackage = "org.mozilla.firefox",
            priority = 100
        )

        val updatedRule = rule.withPriority(0)
        assertEquals(0, updatedRule.priority)
    }

    @Test
    fun `withPriority handles large priority values`() {
        val rule = UrlRule(
            id = 1,
            pattern = "example.com",
            browserPackage = "org.mozilla.firefox",
            priority = 0
        )

        val updatedRule = rule.withPriority(Int.MAX_VALUE)
        assertEquals(Int.MAX_VALUE, updatedRule.priority)
    }

    // ==================== Default Values Tests ====================

    @Test
    fun `UrlRule has correct default id`() {
        val rule = UrlRule(
            pattern = "*.example.com",
            browserPackage = "com.android.chrome"
        )

        assertEquals(0, rule.id)
    }

    @Test
    fun `UrlRule has correct default priority`() {
        val rule = UrlRule(
            pattern = "*.example.com",
            browserPackage = "com.android.chrome"
        )

        assertEquals(0, rule.priority)
    }

    @Test
    fun `UrlRule createdAt is set to current time by default`() {
        val beforeCreation = System.currentTimeMillis()
        val rule = UrlRule(
            pattern = "*.example.com",
            browserPackage = "com.android.chrome"
        )
        val afterCreation = System.currentTimeMillis()

        assertTrue(rule.createdAt >= beforeCreation)
        assertTrue(rule.createdAt <= afterCreation)
    }

    @Test
    fun `UrlRule stores pattern correctly`() {
        val pattern = "*.google.com/search/*"
        val rule = UrlRule(
            pattern = pattern,
            browserPackage = "com.android.chrome"
        )

        assertEquals(pattern, rule.pattern)
    }

    @Test
    fun `UrlRule stores browserPackage correctly`() {
        val browserPackage = "org.mozilla.firefox"
        val rule = UrlRule(
            pattern = "example.com",
            browserPackage = browserPackage
        )

        assertEquals(browserPackage, rule.browserPackage)
    }

    // ==================== Data Class Behavior Tests ====================

    @Test
    fun `UrlRule equality works correctly`() {
        val timestamp = System.currentTimeMillis()
        val rule1 = UrlRule(
            id = 1,
            pattern = "example.com",
            browserPackage = "com.android.chrome",
            priority = 5,
            createdAt = timestamp
        )
        val rule2 = UrlRule(
            id = 1,
            pattern = "example.com",
            browserPackage = "com.android.chrome",
            priority = 5,
            createdAt = timestamp
        )

        assertEquals(rule1, rule2)
    }

    @Test
    fun `UrlRule inequality with different id`() {
        val timestamp = System.currentTimeMillis()
        val rule1 = UrlRule(
            id = 1,
            pattern = "example.com",
            browserPackage = "com.android.chrome",
            priority = 5,
            createdAt = timestamp
        )
        val rule2 = UrlRule(
            id = 2,
            pattern = "example.com",
            browserPackage = "com.android.chrome",
            priority = 5,
            createdAt = timestamp
        )

        assertNotEquals(rule1, rule2)
    }

    @Test
    fun `UrlRule inequality with different pattern`() {
        val timestamp = System.currentTimeMillis()
        val rule1 = UrlRule(
            id = 1,
            pattern = "example.com",
            browserPackage = "com.android.chrome",
            createdAt = timestamp
        )
        val rule2 = UrlRule(
            id = 1,
            pattern = "different.com",
            browserPackage = "com.android.chrome",
            createdAt = timestamp
        )

        assertNotEquals(rule1, rule2)
    }

    @Test
    fun `UrlRule copy preserves all values`() {
        val rule = UrlRule(
            id = 42,
            pattern = "*.github.com",
            browserPackage = "com.brave.browser",
            priority = 100,
            createdAt = 1234567890L
        )

        val copy = rule.copy()

        assertEquals(rule.id, copy.id)
        assertEquals(rule.pattern, copy.pattern)
        assertEquals(rule.browserPackage, copy.browserPackage)
        assertEquals(rule.priority, copy.priority)
        assertEquals(rule.createdAt, copy.createdAt)
    }
}
