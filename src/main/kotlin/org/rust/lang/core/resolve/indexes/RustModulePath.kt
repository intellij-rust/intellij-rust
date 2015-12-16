package org.rust.lang.core.resolve.indexes

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.io.IOUtil
import org.rust.cargo.project.module.util.relativise
import org.rust.lang.core.psi.util.getCrate
import java.io.DataInput
import java.io.DataOutput
import java.util.*

/**
 * URI for the particular module of the Crate
 */
data class RustModulePath(private val name: String, val path: String) {

    fun findModuleIn(p: Project): Module? = ModuleManager.getInstance(p).findModuleByName(name)

    override fun hashCode(): Int =
        Objects.hash(name, path)

    override fun equals(other: Any?): Boolean =
        (other as? RustModulePath)?.let {
            it.name.equals(name) && it.path.equals(path)
        } ?: false

    companion object {

        fun devise(f: PsiFile): RustModulePath = f.getCrate().let {
            RustModulePath(it.name, it.relativise(f.virtualFile ?: f.viewProvider.virtualFile)!!)
        }

        fun readFrom(`in`: DataInput): RustModulePath =
            RustModulePath(
                name = IOUtil.readUTF(`in`),
                path = IOUtil.readUTF(`in`)
            )

        fun writeTo(p: RustModulePath, out: DataOutput): Unit {
            IOUtil.writeUTF(out, p.name)
            IOUtil.writeUTF(out, p.path);
        }
    }
}

