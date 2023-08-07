/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

@file:Suppress("UnstableApiUsage")

package org.rust.cargo.runconfig.target

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.target.*
import com.intellij.execution.target.value.TargetValue
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.text.nullize
import org.rust.RsBundle
import org.rust.cargo.runconfig.RsProcessHandler
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.hasRemoteTarget
import org.rust.openapiext.computeWithCancelableProgress

private val LOG: Logger = Logger.getInstance("org.rust.cargo.runconfig.target.Utils")


enum class BuildTarget {
    LOCAL, REMOTE;

    val isLocal: Boolean get() = this == LOCAL
    val isRemote: Boolean get() = this == REMOTE
}

val CargoCommandConfiguration.targetEnvironment: TargetEnvironmentConfiguration?
    get() {
        if (!RunTargetsEnabled.get()) return null
        val targetName = defaultTargetName ?: return null
        return TargetEnvironmentsManager.getInstance(project).targets.findByName(targetName)
    }

val CargoCommandConfiguration.localBuildArgsForRemoteRun: List<String>
    get() = if (hasRemoteTarget && buildTarget.isLocal) {
        ParametersListUtil.parse(targetEnvironment?.languageRuntime?.localBuildArgs.orEmpty())
    } else {
        emptyList()
    }

val TargetEnvironmentConfiguration.languageRuntime: RsLanguageRuntimeConfiguration?
    get() = runtimes.findByType()

fun GeneralCommandLine.startProcess(
    project: Project,
    config: TargetEnvironmentConfiguration?,
    processColors: Boolean,
    uploadExecutable: Boolean
): ProcessHandler {
    if (config == null) {
        val handler = RsProcessHandler(this)
        ProcessTerminatedListener.attach(handler)
        return handler
    }

    val request = config.createEnvironmentRequest(project)
    val setup = RsCommandLineSetup(request)
    val targetCommandLine = toTargeted(setup, uploadExecutable)
    val progressIndicator = ProgressManager.getInstance().progressIndicator ?: EmptyProgressIndicator()
    val environment = project.computeWithCancelableProgress(RsBundle.message("progress.title.preparing.remote.environment")) {
        request.prepareEnvironment(setup, progressIndicator)
    }
    val process = environment.createProcess(targetCommandLine, progressIndicator)

    val commandRepresentation = targetCommandLine.getCommandPresentation(environment)
    LOG.debug("Executing command: `$commandRepresentation`")

    val handler = RsProcessHandler(process, commandRepresentation, targetCommandLine.charset, processColors)
    ProcessTerminatedListener.attach(handler)
    return handler
}

private fun GeneralCommandLine.toTargeted(
    setup: RsCommandLineSetup,
    uploadExecutable: Boolean
): TargetedCommandLine {
    val commandLineBuilder = TargetedCommandLineBuilder(setup.request)
    commandLineBuilder.charset = charset

    val targetedExePath = if (uploadExecutable) setup.requestUploadIntoTarget(exePath) else TargetValue.fixed(exePath)
    commandLineBuilder.exePath = targetedExePath

    val workDirectory = workDirectory
    if (workDirectory != null) {
        val targetWorkingDirectory = setup.requestUploadIntoTarget(workDirectory.absolutePath)
        commandLineBuilder.setWorkingDirectory(targetWorkingDirectory)
    }

    val inputFile = inputFile
    if (inputFile != null) {
        val targetInput = setup.requestUploadIntoTarget(inputFile.absolutePath)
        commandLineBuilder.setInputFile(targetInput)
    }

    commandLineBuilder.addParameters(parametersList.parameters)

    for ((key, value) in environment.entries) {
        commandLineBuilder.addEnvironmentVariable(key, value)
    }

    val runtime = setup.request.configuration?.languageRuntime
    commandLineBuilder.addEnvironmentVariable("RUSTC", runtime?.rustcPath?.nullize(true))
    commandLineBuilder.addEnvironmentVariable("CARGO", runtime?.cargoPath?.nullize(true))

    return commandLineBuilder.build()
}

private fun TargetEnvironmentRequest.prepareEnvironment(
    setup: RsCommandLineSetup,
    progressIndicator: ProgressIndicator
): TargetEnvironment {
    val targetProgressIndicator = object : TargetProgressIndicator {
        override fun isCanceled(): Boolean = progressIndicator.isCanceled
        override fun stop() = progressIndicator.cancel()
        override fun isStopped(): Boolean = isCanceled
        override fun addText(text: String, key: Key<*>) {
            progressIndicator.text2 = text.trim()
        }
    }

    return try {
        val environment = prepareEnvironment(targetProgressIndicator)
        setup.provideEnvironment(environment, targetProgressIndicator)
        environment
    } catch (e: ProcessCanceledException) {
        throw e
    } catch (e: Exception) {
        throw ExecutionException(RsBundle.message("dialog.message.failed.to.prepare.remote.environment", e.localizedMessage), e)
    }
}

