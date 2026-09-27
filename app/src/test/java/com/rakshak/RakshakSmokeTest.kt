package com.rakshak

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RakshakSmokeTest — placeholder test confirming the test source set is wired up
 * correctly and the build system can run tests.
 *
 * This test will always pass. It is the canary: if it fails, the test infrastructure
 * itself is broken (not the application code).
 */
class RakshakSmokeTest {

    @Test
    fun `smoke test — test infrastructure is operational`() {
        // This test exists to verify that the JUnit test runner can discover and
        // execute tests. It will ALWAYS pass by design.
        assertTrue("Test infrastructure is operational", true)
    }
}
