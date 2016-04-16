package org.rust.lang.core.resolve.indexes

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.io.IOUtil
import org.rust.cargo.project.module.util.getSourceAndLibraryRoots
import org.rust.cargo.project.module.util.relativise
import org.rust.lang.core.psi.RustFileModItem
import org.rust.lang.core.psi.impl.rustMod
import org.rust.lang.core.psi.util.getModule
import java.io.DataInput
import java.io.DataOutput
import java.io.Serializable
import java.util.*

/**
 * URI for the particular module of the Crate
 */
data class RustModulePath private constructor (private val name: String, val path: String) : Serializable {

    fun findModuleIn(p: Project): RustFileModItem? =
        ModuleManager.getInstance(p).findModuleByName(name)?.let { module ->
                module.getSourceAndLibraryRoots()
                .mapNotNull { sourceRoot ->
                    sourceRoot.findFileByRelativePath(path)
                }
                .firstOrNull()
               ?.let {
                   PsiManager.getInstance(p).findFile(it)
               }
        }?.rustMod

    override fun hashCode(): Int =
        Objects.hash(name, path)

    override fun equals(other: Any?): Boolean =
        (other as? RustModulePath)?.let {
            it.name.equals(name) && it.path.equals(path)
        } ?: false

    companion object {

        fun devise(f: PsiFile): RustModulePath? =
            f.getModule()?.let { module ->
                module.relativise(f.virtualFile ?: f.viewProvider.virtualFile)?.let { path ->
                    RustModulePath(module.name, path)
                }
            }

        fun writeTo(out: DataOutput, path: RustModulePath) {
            IOUtil.writeUTF(out, path.name)
            IOUtil.writeUTF(out, path.path)
        }

        fun readFrom(`in`: DataInput): RustModulePath? {
            return RustModulePath(IOUtil.readUTF(`in`), IOUtil.readUTF(`in`))
        }

    }
}

