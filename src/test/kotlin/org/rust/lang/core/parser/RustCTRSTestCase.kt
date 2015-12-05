package org.rust.lang.core.parser

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.psi.impl.DebugUtil
import org.assertj.core.api.Assertions.assertThat
import java.io.File

public class RustCTRSTestCase : RustParsingTestCaseBase("ctrs/test") {

    fun testCtrs() {
        var nFilesVisited = 0
        FileUtil.visitFiles(File(myFullDataPath), { file ->
            if (file.isFile && file.extension == myFileExt.trimStart('.')) {
                nFilesVisited++;
                val text = FileUtil.loadFile(file, CharsetToolkit.UTF8)
                val psi = createPsiFile(file.name, text)
                val expectedError = expectedErrors.any { file.path.endsWith(it) }
                val messageTail = "in ${file.path}:\n\n" +
                        "$text\n\n" +
                        "${DebugUtil.psiToString(psi, true)}"
                if (hasError(psi) ) {
                    assertThat(expectedError).overridingErrorMessage("New error " + messageTail)
                            .isTrue()
                } else {
                    assertThat(expectedError).overridingErrorMessage("No error " + messageTail)
                            .isFalse()
                }
            }
            true
        })
        assertThat(nFilesVisited).overridingErrorMessage("CTRS tests were not run.")
                .isGreaterThan(3000)
    }

    private val expectedErrors = setOf(
            "/1.1.0/run-pass/utf8-bom.rs"

    ).map { it.replace("/", File.separator) }
}


