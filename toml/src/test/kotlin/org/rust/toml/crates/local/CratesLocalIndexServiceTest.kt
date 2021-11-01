/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.crates.local

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class) // TODO: drop the annotation when issue with Gradle test scanning go away
class CratesLocalIndexServiceTest : BasePlatformTestCase() {
    fun `test index has many crates`() {
        assertTrue(cratesService.getAllCrateNames().unwrap().size > 50_000)
    }

    fun `test index has tokio`() {
        assertNotNull(cratesService.getCrate("tokio"))
    }

    fun `test tokio first published version`() {
        assertEquals(
            cratesService.getCrate("tokio").unwrap()?.versions?.get(0)?.version,
            "0.0.0"
        )
    }

    fun `test tokio version is yanked`() {
        assertTrue(
            cratesService.getCrate("tokio")
                .unwrap()
                ?.versions
                ?.find { it.version == "1.0.0" }
                ?.isYanked == true
        )
    }

    fun `test tokio features`() {
        assertEquals(
            cratesService.getCrate("tokio")
                .unwrap()
                ?.versions
                ?.find { it.version == "1.0.0" }
                ?.features,
            listOf("io-util", "process", "macros", "rt", "io-std", "sync", "fs", "rt-multi-thread", "default", "test-util", "time", "net", "signal", "full")
        )
    }

    fun `test code-generation-example has specific versions`() {
        val versions = cratesService.getCrate("code-generation-example").unwrap()?.versions?.map { it.version }

        assertNotNull(versions)

        assertTrue(versions!!.contains("0.1.0"))
        assertTrue(versions.contains("0.2.0"))
    }

    companion object {
        private val cratesService: CratesLocalIndexService by lazy {
            CratesLocalIndexServiceImpl().apply {
                recoverIfNeeded()
                awaitLoadedAndUpdated()
            }
        }
    }
}
