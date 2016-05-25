package org.rust.lang.core.resolve.indexes

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.io.IOUtil
import org.rust.cargo.util.cargoProject
import org.rust.cargo.util.getPsiFor
import org.rust.cargo.util.modules
import org.rust.cargo.util.relativise
import org.rust.lang.core.psi.RustMod
import org.rust.lang.core.psi.impl.rustMod
import org.rust.lang.core.psi.util.module
import java.io.DataInput
import java.io.DataOutput
import java.io.Serializable

/**
 * URI for the particular module of the Crate
 */
data class RustCratePath private constructor (private val crateName: String, val path: String) : Serializable {

    fun findModuleIn(p: Project): RustMod? = p.modules.firstOrNull()?.cargoProject
        ?.findFileInPackage(crateName, path)?.let { p.getPsiFor(it)?.rustMod }

    companion object {

        fun devise(f: PsiFile): RustCratePath? =
            f.module?.let { module ->
                module.relativise(f.virtualFile ?: f.viewProvider.virtualFile)?.let {
                    RustCratePath(it.first, it.second)
                }
            }

        fun writeTo(out: DataOutput, path: RustCratePath) {
            IOUtil.writeUTF(out, path.crateName)
            IOUtil.writeUTF(out, path.path)
        }

        fun readFrom(`in`: DataInput): RustCratePath? {
            return RustCratePath(IOUtil.readUTF(`in`), IOUtil.readUTF(`in`))
        }

    }
}

