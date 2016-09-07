package org.rust.cargo.toolchain

import com.intellij.openapi.application.ApplicationManager
import org.rust.cargo.RustWithToolchainTestBase
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.util.StandardLibraryRoots

class RustupTest : RustWithToolchainTestBase() {
    override val dataPath: String = ""

    override fun runInDispatchThread(): Boolean = false

    fun testDownloadStdlib() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val versionInfo = project.toolchain?.queryVersions()

            if (versionInfo?.rustc?.nightlyCommitHash == null) {
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

            val stdlib = checkNotNull(rustup.downloadStdlib()) {
                "Failed to download stdlib via rustup"
            }

            checkNotNull(StandardLibraryRoots.fromFile(stdlib)) {
                "Failed to extract standard library roots"
            }
        }.get()
    }
}
