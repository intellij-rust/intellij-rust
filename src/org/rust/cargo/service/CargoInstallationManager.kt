package org.rust.cargo.service

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.NonNls
import org.rust.cargo.project.settings.CargoProjectSettings
import org.rust.cargo.project.settings.CargoSettings
import org.rust.cargo.util.Platform

import java.io.File

class CargoInstallationManager {

    private var myCachedCargoHomeFromPath: Ref<File>? = null

    fun getCargoHome(project: Project?, linkedProjectPath: String): File? {
        return getCargoHomeInternal(project, linkedProjectPath)
    }

    fun getCargoHome(module: Module?): VirtualFile? =
        module?.let { m ->
            OrderEnumerator.orderEntries(m).allLibrariesAndSdkClassesRoots
                .filterNotNull()
                .find { containsCargoBinary(it) }
        }

    fun getCargoHome(module: Module?, project: Project?, linkedProjectPath: String): VirtualFile? {
        return getCargoHome(module)
            ?: getCargoHome(project, linkedProjectPath)?.let { LocalFileSystem.getInstance().refreshAndFindFileByIoFile(it) }
    }

    private fun getCargoHomeInternal(project: Project?, linkedProjectPath: String): File? =
        project?.let { p ->
            val settings = CargoSettings.getInstance(p).getLinkedProjectSettings(linkedProjectPath)
            if (settings == null || settings.distributionType == null)
                return null

            return getCargoHomeFromInternal(settings)
        }

    private fun getCargoHomeFromInternal(settings: CargoProjectSettings): File? =
        when (settings.distributionType) {
            CargoProjectSettings.Companion.Distribution.LOCAL ->
                settings.cargoHome.let { File(it).let {
                    return  if (containsCargoBinary(it)) it
                            else tryFindCargoHome()
                    }
                }
            else -> null
        }

    /**
     * Tries to find home of the Cargo SDK in PATH
     * and getting from environment var `$CARGO_HOME`
     */
    fun tryFindCargoHome(): File? {
        return tryFindCargoHomeInPATH() ?: tryGetCargoHomeFromEnv()
    }

    fun tryFindCargoHomeInPATH(): File? {
        val ref = myCachedCargoHomeFromPath
        if (ref != null)
            return ref.get()

        val PATH = System.getenv("PATH") ?: return null

        for (path in PATH.split(File.pathSeparator.toRegex()).dropLastWhile { it.isEmpty() }) {
            val dir = File(path)
            if (!dir.isDirectory)
                continue

            val target = File(dir, CARGO_BINARY_NAME)
            if (target.isFile) {
                val candidate = dir.parentFile // $CARGO_HOME/bin
                if (containsCargoBinary(candidate)) {
                    myCachedCargoHomeFromPath = Ref(candidate)
                    return candidate
                }
            }
        }

        return null
    }

    private fun tryGetCargoHomeFromEnv(): File? =
        System.getenv(CARGO_HOME_ENV_PROPERTY_NAME)?.let {
            File(it).let {
                return  if (containsCargoBinary(it)) it
                        else File(it, "bin").let {
                                return  if (containsCargoBinary(it)) it
                                        else                         null
                            }
            }
        }

    fun isCargoBinary(cargoPath: String): Boolean =
        File(cargoPath).parentFile?.let {
            containsCargoBinary(it)
        } ?: false

    fun containsCargoBinary(cargoHomePath: String): Boolean {
        return containsCargoBinary(File(cargoHomePath))
    }

    private fun containsCargoBinary(file: VirtualFile?): Boolean =
        file?.let { containsCargoBinary(File(it.path)) } ?: false

    private fun containsCargoBinary(path: File?): Boolean =
        path?.let {
            val bin = File(path, Platform.getCanonicalNativeExecutableName(CARGO_BINARY_NAME))
            return !bin.isDirectory && bin.canExecute()
        } ?: false

    companion object {

        @NonNls
        private val CARGO_HOME_ENV_PROPERTY_NAME    = System.getProperty("cargo.home.env",  "CARGO_HOME")
        private val CARGO_BINARY_NAME               = System.getProperty("cargo.name",      "cargo")

    }
}
