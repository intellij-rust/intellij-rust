/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.target

import com.intellij.execution.target.LanguageRuntimeType.VolumeDescriptor
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironment.TargetPath.Temporary
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.execution.target.local.LocalTargetEnvironment
import com.intellij.execution.target.value.DeferredTargetValue
import com.intellij.execution.target.value.TargetValue
import com.intellij.execution.target.value.getUploadRootForLocalPath
import com.intellij.lang.LangCoreBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.isDirectory
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.rust.stdext.toPath
import java.nio.file.Path
import java.nio.file.Paths

@Suppress("UnstableApiUsage")
class RsCommandLineSetup(val request: TargetEnvironmentRequest) {
    private val languageRuntime: RsLanguageRuntimeConfiguration? = request.configuration?.languageRuntime
    private val environmentPromise = AsyncPromise<Pair<TargetEnvironment, TargetProgressIndicator>>()
    private val dependingOnEnvironmentPromise: MutableList<Promise<Unit>> = mutableListOf()
    private val uploads: MutableList<Upload> = mutableListOf()
    private val projectHomeOnTarget: VolumeDescriptor = VolumeDescriptor(
        RsCommandLineSetup::class.java.simpleName + ":projectHomeOnTarget",
        "",
        "",
        "",
        request.projectPathOnTarget
    )

    fun requestUploadIntoTarget(uploadPathString: String): TargetValue<String> {
        val uploadPath = FileUtil.toSystemDependentName(uploadPathString).toPath()
        val isDir = uploadPath.isDirectory()
        val localRootPath = if (isDir) uploadPath else (uploadPath.parent ?: Paths.get("."))
        val (uploadRoot, pathToRoot) = request.getUploadRootForLocalPath(localRootPath.toString())
            ?: createUploadRoot(projectHomeOnTarget, localRootPath).let { uploadRoot ->
                request.uploadVolumes += uploadRoot
                uploadRoot to "."
            }
        val result = DeferredTargetValue(uploadPathString)
        dependingOnEnvironmentPromise += environmentPromise.then { (environment, targetProgressIndicator) ->
            if (targetProgressIndicator.isCanceled || targetProgressIndicator.isStopped) {
                result.stopProceeding()
                return@then
            }
            val volume = environment.uploadVolumes.getValue(uploadRoot)
            try {
                val relativePath = if (isDir) {
                    pathToRoot
                } else {
                    uploadPath.fileName.toString()
                        .let { if (pathToRoot == ".") it else joinPath(pathToRoot, it) }
                }
                val resolvedTargetPath = volume.resolveTargetPath(relativePath)
                uploads.add(Upload(volume, relativePath))
                result.resolve(resolvedTargetPath)
            } catch (t: Throwable) {
                LOG.warn(t)
                targetProgressIndicator.stopWithErrorMessage(
                    LangCoreBundle.message(
                        "progress.message.failed.to.resolve.0.1",
                        volume.localRoot, t.localizedMessage
                    )
                )
                result.resolveFailure(t)
            }
        }
        return result
    }

    private fun createUploadRoot(
        volumeDescriptor: VolumeDescriptor,
        localRootPath: Path
    ): TargetEnvironment.UploadRoot =
        languageRuntime?.createUploadRoot(volumeDescriptor, localRootPath)
            ?: TargetEnvironment.UploadRoot(localRootPath, Temporary())

    private fun joinPath(vararg segments: String): String =
        segments.joinToString(request.targetPlatform.platform.fileSeparator.toString())

    fun provideEnvironment(environment: TargetEnvironment, targetProgressIndicator: TargetProgressIndicator) {
        val application = ApplicationManager.getApplication()
        LOG.assertTrue(
            environment is LocalTargetEnvironment ||
                uploads.isEmpty() ||
                !application.isDispatchThread ||
                application.isUnitTestMode,
            "Preparation of environment shouldn't be performed on EDT."
        )
        environmentPromise.setResult(environment to targetProgressIndicator)
        uploads.asSequence()
            .sortedBy { it.relativePath.length }
            .groupBy({ it.volume }, { it.relativePath })
            .forEach { (volume, relativePaths) ->
                volume.upload(relativePaths.first(), targetProgressIndicator)
            }
        for (promise in dependingOnEnvironmentPromise) {
            promise.blockingGet(0) // Just rethrows errors
        }
    }

    private class Upload(val volume: TargetEnvironment.UploadableVolume, val relativePath: String)

    companion object {
        private val LOG: Logger = logger<RsCommandLineSetup>()
    }
}
