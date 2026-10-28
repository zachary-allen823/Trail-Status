package com.trailstatus.dfw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class TrailforksParserTest {
    // Unit tests run with the app module as the working directory.
    private val trails = TrailforksParser.parse(File("../../fixtures/region_list.html").readText())

    @Test
    fun parsesEveryRow() {
        assertEquals(7, trails.size)
        val ns = trails.first { it.name.startsWith("Northshore") }
        assertEquals(
            Trail(
                name = "Northshore Trails @ Grapevine Lake",
                url = "https://www.trailforks.com/region/northshore-trails--grapevine-lake/",
                level = StatusLevel.OPEN,
                statusText = "All Clear / Green",
                updatedEpochSec = 1792857000,
            ),
            ns,
        )
        assertEquals("Example Yellow Trail & Park", trails.first { it.level == StatusLevel.MINOR }.name)
    }

    @Test
    fun matchesFriendlyNames() {
        assertEquals("Northshore Trails @ Grapevine Lake", TrailMatcher.find(trails, "North Shore")?.name)
        assertEquals("Erwin Park", TrailMatcher.find(trails, "erwin park")?.name)
        assertEquals(StatusLevel.CLOSED, TrailMatcher.find(trails, "Erwin Park")?.level)
        assertEquals("Erwin Park Skill Park", TrailMatcher.find(trails, "Erwin Park Skill")?.name)
        assertNull(TrailMatcher.find(trails, "Nonexistent"))
    }

    @Test
    fun shortensNames() {
        assertEquals("Northshore", TrailMatcher.shortName("Northshore Trails @ Grapevine Lake"))
        assertEquals("Big Cedar Wilderness", TrailMatcher.shortName("Big Cedar Wilderness Trails"))
        assertEquals("Erwin Park", TrailMatcher.shortName("Erwin Park"))
    }
}
