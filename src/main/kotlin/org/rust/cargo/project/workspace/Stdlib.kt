package org.rust.cargo.project.workspace

import com.intellij.notification.NotificationType
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import org.rust.cargo.toolchain.Rustup
import org.rust.ide.notifications.showBalloon

class SetupRustStdlibTask(
    private val module: Module,
    private val rustup: Rustup
) : Task.Backgroundable(module.project, "Setup Rust stdlib") {
    private lateinit var result: Rustup.DownloadResult
    private val oldLibrary = rustStandardLibrary(module)

    override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = true
        result = rustup.downloadStdlib()
    }

    override fun onSuccess() {
        if (module.isDisposed) return
        if (rustStandardLibrary(module) != oldLibrary) {
            return failWithMessage("Rust std library has been updated concurrently")
        }

        val result = result
        when (result) {
            is Rustup.DownloadResult.Ok -> {
                val roots = StandardLibraryRoots.fromFile(result.library)
                    ?: return failWithMessage("${result.library.presentableUrl} is not a valid Rust standard library")

                if (oldLibrary != null && roots.sameAsLibrary(oldLibrary)) return

                runWriteAction { roots.attachTo(module) }
                project.showBalloon(
                    "Using Rust standard library at ${result.library.presentableUrl}",
                    NotificationType.INFORMATION
                )
            }
            is Rustup.DownloadResult.Err ->
                return failWithMessage("Failed to download standard library: ${result.error}")
        }
    }

    private fun failWithMessage(message: String) {
        project.showBalloon(message, NotificationType.ERROR)
    }
}


private fun rustStandardLibrary(module: Module): Library? =
    LibraryTablesRegistrar.getInstance().getLibraryTable(module.project).getLibraryByName(module.rustLibraryName)

