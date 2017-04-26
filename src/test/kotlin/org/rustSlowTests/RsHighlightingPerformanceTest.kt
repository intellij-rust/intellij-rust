package org.rustSlowTests

import com.intellij.psi.stubs.StubUpdatingIndex
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.util.indexing.FileBasedIndex
import org.junit.experimental.categories.Category
import org.rust.Performance
import org.rust.lang.RsTestBase

class RsHighlightingPerformanceTest : RsTestBase() {
    override val dataPath = "org/rust/ide/annotator/fixtures/performance"

    override fun setUp() {
        super.setUp()

        FileBasedIndex.getInstance().requestRebuild(StubUpdatingIndex.INDEX_ID)
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = WithStdlibRustProjectDescriptor

    fun testHighlightingWithStdlib() {
        myFixture.copyDirectoryToProject("", "")

        myFixture.configureFromTempProjectFile("main.rs")

        val elapsed = myFixture.checkHighlighting()
        reportTeamCityMetric(name, elapsed)
    }

}

