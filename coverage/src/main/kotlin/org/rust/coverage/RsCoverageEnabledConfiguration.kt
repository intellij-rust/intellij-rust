/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.coverage

import com.intellij.coverage.CoverageRunner
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration
import com.intellij.execution.process.ProcessHandler

class RsCoverageEnabledConfiguration(configuration: RunConfigurationType)
    : CoverageEnabledConfiguration(configuration) {
    var coverageProcess: ProcessHandler? = null

    init {
        coverageRunner = CoverageRunner.getInstance(RsCoverageRunner::class.java)
    }
}
