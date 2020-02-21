package org.rust.wsl

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.remote.ProcessControlWithMappings
import com.intellij.util.PathMappingSettings
import com.intellij.util.io.BaseOutputReader
import org.rust.cargo.runconfig.RsProcessHandler

class RsWslProcessHandler(
    commandLine: GeneralCommandLine,
    private val distribution: WSLDistribution,
    private val additionalMappings: PathMappingSettings
) : RsProcessHandler(commandLine), ProcessControlWithMappings {
    override fun getFileMappings() = distribution.rootMappings + additionalMappings.pathMappings
    override fun readerOptions(): BaseOutputReader.Options = BaseOutputReader.Options.BLOCKING
}
