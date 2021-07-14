/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.target

import com.intellij.execution.target.LanguageRuntimeType.VolumeDescriptor
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironment.TargetPath.Persistent
import com.intellij.execution.target.TargetEnvironment.TargetPath.Temporary
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.execution.target.local.LocalTargetEnvironment
import com.intellij.execution.target.value.DeferredLocalTargetValue
import com.intellij.execution.target.value.DeferredTargetValue
import com.intellij.execution.target.value.TargetValue
import com.intellij.execution.target.value.getUploadRootForLocalPath
import com.intellij.lang.LangBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.isDirectory
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.rust.cargo.runconfig.target.RsCommandLineParameter.*
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

@Suppress("UnstableApiUsage")
class RsCommandLineSetup(
    private val request: TargetEnvironmentRequest,
    private val languageRuntime: RsLanguageRuntimeConfiguration?
) {
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

    fun string(s: String): TargetValue<String> {
        val parameter = StringParameter(s)
        parameter.prepare(this)
        return parameter.toValue()
    }

    fun path(path: String): TargetValue<String> {
        val parameter = PathParameter(path)
        parameter.prepare(this)
        return parameter.toValue()
    }

    fun download(path: File): TargetValue<String> {
        val parameter = DownloadFromTargetParameter(path.name, path.parent, path.parentFile.toPath())
        parameter.prepare(this)
        return parameter.toValue()
    }

    fun requestUploadIntoTarget(uploadPathString: String): TargetValue<String> {
        return requestUploadIntoTarget(projectHomeOnTarget, uploadPathString)
    }

    private fun requestUploadIntoTarget(
        volumeDescriptor: VolumeDescriptor,
        uploadPathString: String
    ): TargetValue<String> {
        val uploadPath = Paths.get(FileUtil.toSystemDependentName(uploadPathString))
        val isDir = uploadPath.isDirectory()
        val localRootPath = if (isDir) uploadPath else (uploadPath.parent ?: Paths.get("."))
        val (uploadRoot, pathToRoot) = request.getUploadRootForLocalPath(localRootPath.toString())
            ?: createUploadRoot(volumeDescriptor, localRootPath).let { uploadRoot ->
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
                val relativePath = if (isDir) pathToRoot
                else uploadPath.fileName.toString().let {
                    if (pathToRoot == ".") it else joinPath(pathToRoot, it)
                }
                val resolvedTargetPath = volume.resolveTargetPath(relativePath)
                uploads.add(Upload(volume, relativePath))
                result.resolve(resolvedTargetPath)
            } catch (t: Throwable) {
                LOG.warn(t)
                targetProgressIndicator.stopWithErrorMessage(
                    LangBundle.message(
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
    ): TargetEnvironment.UploadRoot {
        return languageRuntime?.createUploadRoot(volumeDescriptor, localRootPath)
            ?: TargetEnvironment.UploadRoot(localRootPath = localRootPath, targetRootPath = Temporary())
    }

    fun requestDownloadFromTarget(
        volumeRoot: String?,
        downloadPathString: String,
        localPath: Path? = null
    ): TargetValue<String> {
        val downloadRoot = createDownloadRoot(localPath, volumeRoot)
        request.downloadVolumes += downloadRoot
        val result = DownloadTargetValue<String>()
        dependingOnEnvironmentPromise += environmentPromise.then { (environment, targetProgressIndicator) ->
            if (targetProgressIndicator.isCanceled || targetProgressIndicator.isStopped) {
                return@then
            }
            val volume = environment.downloadVolumes[downloadRoot]
                ?: throw IllegalStateException("Missing download root '$downloadRoot'")
            result.download = Download(volume, downloadPathString)
            val localPathString = volume.localRoot.resolve(downloadPathString).toString()
            val targetPathString = joinPath(volume.targetRoot, downloadPathString)
            result.resolve(localPathString, targetPathString)
        }
        return result
    }

    private fun joinPath(vararg segments: String): String =
        segments.joinTo(StringBuilder(), request.targetPlatform.platform.fileSeparator.toString()).toString()

    private fun createDownloadRoot(localPath: Path?, volumeRoot: String?): TargetEnvironment.DownloadRoot {
        return TargetEnvironment.DownloadRoot(
            localRootPath = localPath,
            targetRootPath = if (volumeRoot != null) Persistent(volumeRoot) else Temporary()
        )
    }

    fun requestPort(targetPort: Int): TargetValue<Int> {
        val binding = TargetEnvironment.TargetPortBinding(null, targetPort)
        request.targetPortBindings.add(binding)
        val result = DeferredLocalTargetValue(targetPort)
        dependingOnEnvironmentPromise += environmentPromise.then { (environment, targetProgressIndicator) ->
            if (targetProgressIndicator.isCanceled || targetProgressIndicator.isStopped) {
                return@then
            }
            val localPort = environment.targetPortBindings[binding]
            result.resolve(localPort)
        }
        return result
    }

    fun provideEnvironment(environment: TargetEnvironment, targetProgressIndicator: TargetProgressIndicator) {
        val application = ApplicationManager.getApplication()
        LOG.assertTrue(
            environment is LocalTargetEnvironment ||
                uploads.isEmpty() ||
                !application.isDispatchThread ||
                application.isUnitTestMode, "Preparation of environment shouldn't be performed on EDT."
        )
        environmentPromise.setResult(environment to targetProgressIndicator)
        uploads.asSequence()
            .sortedBy { it.relativePath.length }
            .groupBy({ it.volume }, { it.relativePath })
            .forEach { (volume, relativePaths) ->
                volume.upload(relativePaths.first(), targetProgressIndicator)
            }
        for (promise in dependingOnEnvironmentPromise) {
            promise.blockingGet(0)  // Just rethrows errors.
        }
    }

    private class Upload(val volume: TargetEnvironment.UploadableVolume, val relativePath: String)
    internal class Download(val volume: TargetEnvironment.DownloadableVolume, val relativePath: String)

    private class DownloadTargetValue<T> : TargetValue<T> {
        private val localPromise: AsyncPromise<T> = AsyncPromise()
        private val targetPromise: AsyncPromise<T> = AsyncPromise()

        @Volatile
        var download: Download? = null

        override fun getLocalValue(): Promise<T> = localPromise.then {
            it.also {
                download?.let { download ->
                    val indicator = ProgressManager.getInstance().progressIndicator ?: EmptyProgressIndicator()
                    download.volume.download(download.relativePath, indicator)
                } ?: throw IllegalStateException("Download wasn't provided")
            }
        }

        override fun getTargetValue(): Promise<T> = targetPromise

        fun resolve(localValue: T, targetValue: T) {
            if (localPromise.isDone) {
                throw IllegalStateException("Local value is already resolved to '" + localPromise.get() + "'")
            }
            if (targetPromise.isDone) {
                throw IllegalStateException("Target value is already resolved to '" + targetPromise.get() + "'")
            }
            localPromise.setResult(localValue)
            targetPromise.setResult(targetValue)
        }
    }

    companion object {
        private val LOG: Logger = logger<RsCommandLineSetup>()
    }
}
