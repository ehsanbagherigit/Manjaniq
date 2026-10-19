package com.manjaniq

import com.manjaniq.ui.navigation.AppDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Regression guard for the class-initialisation order bug that crashed the
 * drawer on first composition: touching a subclass object *before* the
 * companion's list must still yield a fully populated, null-free list.
 */
class AppDestinationTest {

    @Test
    fun drawerItemsAreNonNullWhenSubclassIsTouchedFirst() {
        // Same access order as ManjaniqApp: a concrete destination first.
        val home = AppDestination.Home.route
        assertEquals("home", home)

        val items = AppDestination.drawerItems
        assertEquals(4, items.size)
        items.forEach { assertNotNull("drawer item must not be null", it) }
        assertEquals(listOf("home", "settings", "logs", "about"), items.map { it.route })
        assertEquals(AppDestination.Home, items.first())
    }
}
