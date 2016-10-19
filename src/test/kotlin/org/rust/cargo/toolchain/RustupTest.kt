package org.rust.cargo.toolchain

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.text.SemVer
import org.rust.cargo.RustWithToolchainTestBase
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.util.StandardLibraryRoots

class RustupTest : RustWithToolchainTestBase() {
    override val dataPath: String = ""

    override fun runInDispatchThread(): Boolean = false

    fun testDownloadStdlib() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val versionInfo = project.toolchain?.queryVersions()!!

            if (versionInfo.rustup!! < SemVer.parseFromText("0.6.0")!!) {
                System.err.println("SKIP $name: recent rustup required")
                return@executeOnPooledThread
            }

            if (versionInfo.rustc?.nightlyCommitHash == null) {
                System.err.println("SKIP $name: nightly toolchain required")
                return@executeOnPooledThread
            }

            val rustup = project.toolchain?.rustup(project.baseDir.path)

            if (rustup == null) {
                System.err.println("SKIP $name: rustup required")
                return@executeOnPooledThread
            }

            if ("nightly" !in (rustup.activeToolchain() ?: "")) {
                System.err.println("SKIP $name: nightly toolchain required")
                return@executeOnPooledThread
            }


            val stdlib = rustup.downloadStdlib()
            if (stdlib !is Rustup.DownloadResult.Ok) {
                error("Failed to download stdlib via rustup")
            }

            checkNotNull(StandardLibraryRoots.fromFile(stdlib.library)) {
                "Failed to extract standard library roots"
            }
        }.get()
    }
}
