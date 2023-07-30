/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.util.NlsContexts.Tooltip
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic
import org.rust.RsBundle
import org.rust.cargo.CargoConstants
import org.rust.cargo.project.model.impl.UserDisabledFeatures
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.FeatureState
import org.rust.cargo.project.workspace.PackageFeature
import org.rust.cargo.toolchain.RsToolchainBase
import org.rust.cargo.toolchain.impl.RustcVersion
import org.rust.cargo.toolchain.tools.isRustupAvailable
import org.rust.ide.notifications.showBalloon
import org.rust.openapiext.pathAsPath
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

/**
 * Stores a list of [CargoProject]s associated with the current IntelliJ [Project].
 * Use [Project.cargoProjects] to get an instance of the service.
 *
 * See [ARCHITECTURE.md](https://github.com/intellij-rust/intellij-rust/blob/master/ARCHITECTURE.md#project-model)
 * for more details.
 */
interface CargoProjectsService {
    val project: Project
    val allProjects: Collection<CargoProject>
    val hasAtLeastOneValidProject: Boolean
    val initialized: Boolean

    fun findProjectForFile(file: VirtualFile): CargoProject?
    fun findPackageForFile(file: VirtualFile): CargoWorkspace.Package?

    /**
     * @param manifest a path to `Cargo.toml` file of the project that should be attached
     */
    fun attachCargoProject(manifest: Path): Boolean
    fun attachCargoProjects(vararg manifests: Path)
    fun detachCargoProject(cargoProject: CargoProject)
    fun refreshAllProjects(): CompletableFuture<out List<CargoProject>>
    fun discoverAndRefresh(): CompletableFuture<out List<CargoProject>>
    fun suggestManifests(): Sequence<VirtualFile>

    fun modifyFeatures(cargoProject: CargoProject, features: Set<PackageFeature>, newState: FeatureState)

    companion object {
        val CARGO_PROJECTS_TOPIC: Topic<CargoProjectsListener> = Topic(
            "cargo projects changes",
            CargoProjectsListener::class.java
        )

        val CARGO_PROJECTS_REFRESH_TOPIC: Topic<CargoProjectsRefreshListener> = Topic(
            "Cargo refresh",
            CargoProjectsRefreshListener::class.java
        )
    }

    fun interface CargoProjectsListener {
        fun cargoProjectsUpdated(service: CargoProjectsService, projects: Collection<CargoProject>)
    }

    interface CargoProjectsRefreshListener {
        fun onRefreshStarted()
        fun onRefreshFinished(status: CargoRefreshStatus)
    }

    enum class CargoRefreshStatus {
        SUCCESS,
        FAILURE,
        CANCEL
    }
}

val Project.cargoProjects: CargoProjectsService get() = service()

fun CargoProjectsService.isGeneratedFile(file: VirtualFile): Boolean {
    val outDir = findPackageForFile(file)?.outDir ?: return false
    return VfsUtil.isAncestor(outDir, file, false)
}

/**
 * See docs for [CargoProjectsService].
 *
 * Instances of this class are immutable and will be re-created on each project refresh.
 * This class implements [UserDataHolderEx] interface and therefore any data can be attached
 * to it. Note that since instances of this class are re-created on each project refresh,
 * user data will be flushed on project refresh too
 */
interface CargoProject : UserDataHolderEx {
    val project: Project
    val manifest: Path
    val rootDir: VirtualFile?
    val workspaceRootDir: VirtualFile?

    val presentableName: String
    val workspace: CargoWorkspace?

    val rustcInfo: RustcInfo?
    val procMacroExpanderPath: Path?

    val workspaceStatus: UpdateStatus
    val stdlibStatus: UpdateStatus
    val rustcInfoStatus: UpdateStatus

    val mergedStatus: UpdateStatus
        get() = workspaceStatus
            .merge(stdlibStatus)
            .merge(rustcInfoStatus)

    val userDisabledFeatures: UserDisabledFeatures

    sealed class UpdateStatus(private val priority: Int) {
        object UpToDate : UpdateStatus(0)
        object NeedsUpdate : UpdateStatus(1)
        class UpdateFailed(@Suppress("UnstableApiUsage") @Tooltip val reason: String) : UpdateStatus(2) {
            override fun toString(): String = reason
        }

        fun merge(status: UpdateStatus): UpdateStatus = if (priority >= status.priority) this else status
    }
}

data class RustcInfo(
    val sysroot: String,
    val version: RustcVersion?,
    val rustupActiveToolchain: String? = null,
    val targets: List<String>? = null,

    /**
     * In production environments it is always equal to [version].
     * In unit tests it is real, non-mocked toolchain version
     */
    val realVersion: RustcVersion? = version,
)

fun guessAndSetupRustProject(project: Project, explicitRequest: Boolean = false): Boolean {
    if (!explicitRequest) {
        val alreadyTried = run {
            val key = "org.rust.cargo.project.model.PROJECT_DISCOVERY"
            val properties = PropertiesComponent.getInstance(project)
            val alreadyTried = properties.getBoolean(key)
            properties.setValue(key, true)
            alreadyTried
        }
        if (alreadyTried) return false
    }

    val toolchain = project.rustSettings.toolchain
    if (toolchain == null || !toolchain.looksLikeValidToolchain()) {
        discoverToolchain(project)
        return true
    }
    if (!project.cargoProjects.hasAtLeastOneValidProject) {
        project.cargoProjects.discoverAndRefresh()
        return true
    }
    return false
}

private fun discoverToolchain(project: Project) {
    val projectPath = project.guessProjectDir()?.pathAsPath
    val toolchain = RsToolchainBase.suggest(projectPath) ?: return
    invokeLater {
        if (project.isDisposed) return@invokeLater

        val oldToolchain = project.rustSettings.toolchain
        if (oldToolchain != null && oldToolchain.looksLikeValidToolchain()) {
            return@invokeLater
        }

        runWriteAction {
            project.rustSettings.modify { it.toolchain = toolchain }
        }

        val tool = if (toolchain.isRustupAvailable) RsBundle.message("notification.content.rustup") else RsBundle.message("notification.content.cargo.at", toolchain.presentableLocation)
        project.showBalloon(RsBundle.message("notification.content.using", tool), NotificationType.INFORMATION)
        project.cargoProjects.discoverAndRefresh()
    }
}

fun ContentEntry.setup(contentRoot: VirtualFile) = ContentEntryWrapper(this).setup(contentRoot)

fun ContentEntryWrapper.setup(contentRoot: VirtualFile) {
    val makeVfsUrl = { dirName: String -> contentRoot.findChild(dirName)?.url }
    CargoConstants.ProjectLayout.sources.mapNotNull(makeVfsUrl).forEach {
        addSourceFolder(it, isTestSource = false)
    }
    CargoConstants.ProjectLayout.tests.mapNotNull(makeVfsUrl).forEach {
        addSourceFolder(it, isTestSource = true)
    }
    makeVfsUrl(CargoConstants.ProjectLayout.target)?.let(::addExcludeFolder)
}

class ContentEntryWrapper(private val contentEntry: ContentEntry) {
    private val knownFolders: Set<String> = contentEntry.knownFolders()

    fun addExcludeFolder(url: String) {
        if (url in knownFolders) return
        contentEntry.addExcludeFolder(url)
    }

    fun addSourceFolder(url: String, isTestSource: Boolean) {
        if (url in knownFolders) return
        contentEntry.addSourceFolder(url, isTestSource)
    }

    private fun ContentEntry.knownFolders(): Set<String> {
        val knownRoots = sourceFolders.mapTo(hashSetOf()) { it.url }
        knownRoots += excludeFolderUrls
        return knownRoots
    }
}

val isNewProjectModelImportEnabled: Boolean
    get() = Registry.`is`("org.rust.cargo.new.auto.import", false)
