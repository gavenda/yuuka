package dev.gavenda.yuuka.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionsTest {
    @Test
    fun `a higher part is newer`() {
        assertTrue(isNewerVersion("v5.1.0", "5.0.0"))
        assertTrue(isNewerVersion("v6.0.0", "5.9.9"))
        assertTrue(isNewerVersion("v5.0.1", "5.0.0"))
    }

    @Test
    fun `parts compare as numbers, not text`() {
        assertTrue(isNewerVersion("v5.10.0", "5.9.0"))
    }

    @Test
    fun `the same or an older release is not newer`() {
        assertFalse(isNewerVersion("v5.0.0", "5.0.0"))
        assertFalse(isNewerVersion("v4.5.1", "5.0.0"))
        assertFalse(isNewerVersion("v5.0", "5.0.0"))
    }

    @Test
    fun `a tag that is not a version is never newer`() {
        assertFalse(isNewerVersion("nightly", "5.0.0"))
        assertFalse(isNewerVersion("v5.1.0-beta", "5.0.0"))
    }
}
