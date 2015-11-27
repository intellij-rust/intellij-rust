package org.rust.lang.core.resolve.util

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustModItem
import org.rust.lang.core.psi.impl.RustFileImpl
import org.rust.lang.core.psi.util.*
import org.rust.lang.core.resolve.scope.RustResolveScope

public object RustResolveUtil {

    fun getResolveScopeFor(elem: PsiElement): RustResolveScope? {
        var current = elem.parent
        while (current != null) {
            when (current) {
                is RustResolveScope -> return current
                else                -> current = current.parent
            }
        }

        return null
    }

    /**
     *  Returns a "crate root": the top level module of the crate to which the `PsiElement` belongs.
     *
     *  In most cases it will be situated in another file, typically `src/main.rs` or `src/lib.rs`.
     *
     *  Reference:
     *    https://doc.rust-lang.org/reference.html#crates-and-source-files
     */
    fun getCrateRootFor(elem: PsiElement): RustModItem? {
        val mod = getSelfModFor(elem) ?: return null

        val superMod = getSuperModFor(mod)
        return if (superMod == null) mod else getCrateRootFor(superMod)
    }

    /**
     *  Returns a parent module (`super::` in paths).
     *
     *  The parent module may be in the same or other file.
     *
     *  Reference:
     *    https://doc.rust-lang.org/reference.html#paths
     */
    fun getSuperModFor(mod: RustModItem): RustModItem? {
        val self = getSelfModFor(mod) ?: return null
        val superInFile = self.containingMod
        if (superInFile != null) {
            return superInFile
        }

        val file = self.containingFile
        val dir = self.containingFile?.containingDirectory ?: return null
        val dirOfParent = if (self.ownsDirectory) dir.parent else dir
        dirOfParent?.files.orEmpty()
            .filterIsInstance<RustFileImpl>()
            .map { it.mod }
            .filterNotNull()
            .forEach { mod ->
                for (declaration in mod.modDecls) {
                    val childFile = declaration.moduleFile as? ChildModFile.Found ?: continue

                    if (childFile.file == file ) {
                        return mod
                    }
                }
            }

        return null
    }

    /**
     *  Returns the module the `PsiElement` belongs to (`self::` in paths). If `elem` is a module, returns `elem`.
     *
     *  The module will be in the same file.
     *
     *  Reference:
     *    https://doc.rust-lang.org/reference.html#paths
     */
    fun getSelfModFor(elem: PsiElement): RustModItem? {
        return elem.parentOfType<RustModItem>(strict = false)
    }

}
