package org.rust.ide.annotator

import com.intellij.testFramework.LightProjectDescriptor
import org.junit.experimental.categories.Category
import org.rust.Performance
import org.rust.lang.RustTestCaseBase

@Category(Performance::class)
class RustHighlightingPerformanceTest : RustTestCaseBase() {
    override val dataPath = "org/rust/ide/annotator/fixtures/performance"

    override fun getProjectDescriptor(): LightProjectDescriptor = WithStdlibRustProjectDescriptor()

    fun testHighlightingWithStdlib() {
        myFixture.copyDirectoryToProject("", "")

        myFixture.configureFromTempProjectFile("main.rs")

        val elapsed = myFixture.checkHighlighting()
        reportTeamCityMetric(name, elapsed)
    }

}

