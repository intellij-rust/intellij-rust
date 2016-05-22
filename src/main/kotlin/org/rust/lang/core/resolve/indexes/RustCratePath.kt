package org.rust.lang.core.resolve.indexes

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.io.IOUtil
import org.rust.cargo.util.*
import org.rust.lang.core.psi.RustMod
import org.rust.lang.core.psi.impl.rustMod
import org.rust.lang.core.psi.util.module
import java.io.DataInput
import java.io.DataOutput
import java.io.Serializable
import java.util.*

/**
 * URI for the particular module of the Crate
 */
data class RustCratePath private constructor (private val crateName: String?, val path: String) : Serializable {

    fun findModuleIn(p: Project): RustMod? =
        ModuleManager.getInstance(p)
            .modules
            .firstOrNull()?.let {
                if (crateName == null)
                    it.cargoProject?.packages
                        .orEmpty()
                        .firstOrNull()?.let { it.contentRoot }
                else
                    it.findExternCrateRootByName(crateName)?.parent
            }?.let { p.getPsiFor(it.findFileByRelativePath(path)) }?.rustMod

    override fun hashCode(): Int =
        Objects.hash(crateName, path)

    override fun equals(other: Any?): Boolean =
        (other as? RustCratePath)?.let {
            it.crateName.equals(crateName) && it.path.equals(path)
        } ?: false

    companion object {

        fun devise(f: PsiFile): RustCratePath? =
            f.module?.let { module ->
                module.relativise(f.virtualFile ?: f.viewProvider.virtualFile)?.let {
                    RustCratePath(it.first, it.second)
                }
            }

        val STUB = "<null>"

        fun writeTo(out: DataOutput, path: RustCratePath) {
            IOUtil.writeUTF(out, path.crateName ?: STUB)
            IOUtil.writeUTF(out, path.path)
        }

        fun readFrom(`in`: DataInput): RustCratePath? {
            return RustCratePath(IOUtil.readUTF(`in`).let { if (it.equals(STUB)) null else it }, IOUtil.readUTF(`in`))
        }

    }
}

