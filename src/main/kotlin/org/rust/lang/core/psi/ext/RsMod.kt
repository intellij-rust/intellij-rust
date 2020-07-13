/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiDirectory
import org.rust.lang.RsConstants
import org.rust.lang.RsFileType
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsModDeclItem
import org.rust.lang.core.psi.RsModItem
import org.rust.openapiext.findFileByMaybeRelativePath
import java.util.*

interface RsMod : RsQualifiedNamedElement, RsItemsOwner, RsVisible {
    /**
     *  Returns a parent module (`super::` in paths).
     *
     *  The parent module may be in the same or other file.
     *
     *  Reference:
     *    https://doc.rust-lang.org/reference.html#paths
     */
    val `super`: RsMod?

    /**
     * XXX: this might be different then [com.intellij.psi.PsiNamedElement.getName].
     *
     * This contortion is required because [org.rust.lang.core.psi.RsFile] is
     * [RsMod], but we shouldn't override its name.
     */
    val modName: String?

    /**
     *  Returns value of `path` attribute related to this module.
     *  If module doesn't have `path` attribute, returns null.
     *
     *  Note, in case of non inline module (i.e. declared via `mod foo;`)
     *  `path` attribute belongs to module declaration but not to module item itself
     */
    val pathAttribute: String?

    val ownsDirectory: Boolean

    /**
     *  Returns directory where direct submodules should be located
     */
    @JvmDefault
    fun getOwnedDirectory(createIfNotExists: Boolean = false): PsiDirectory? {
        if (this is RsFile && name == RsConstants.MOD_RS_FILE || isCrateRoot) return contextualFile.originalFile.parent

        val explicitPath = pathAttribute
        val (parentDirectory, path) = if (explicitPath != null) {
            contextualFile.originalFile.parent to explicitPath
        } else {
            `super`?.getOwnedDirectory(createIfNotExists) to name
        }
        if (parentDirectory == null || path == null) return null

        // Don't use `FileUtil#getNameWithoutExtension` to correctly process relative paths like `./foo`
        val directoryPath = FileUtil.toSystemIndependentName(path).removeSuffix(".${RsFileType.defaultExtension}")
        val directory = parentDirectory.virtualFile
            .findFileByMaybeRelativePath(directoryPath)
            ?.let(parentDirectory.manager::findDirectory)
        return if (directory == null && createIfNotExists) {
            parentDirectory.createSubdirectory(directoryPath)
        } else {
            directory
        }
    }

    val isCrateRoot: Boolean
}

val RsMod.superMods: List<RsMod> get() {
    // For malformed programs, chain of `super`s may be infinite
    // because of cycles, and we need to detect this situation.
    val visited = HashSet<RsMod>()
    return generateSequence(this) { it.`super` }
        .takeWhile { visited.add(it) }
        .toList()
}

fun RsMod.hasChildModules(): Boolean =
    expandedItemsExceptImplsAndUses.any { it is RsModDeclItem || it is RsModItem && it.hasChildModules() }

val RsMod.childModules: List<RsMod>
    get() = expandedItemsExceptImplsAndUses
        .mapNotNull {
            when (it) {
                is RsModDeclItem -> it.reference.resolve() as? RsMod
                is RsModItem -> it
                else -> null
            }
        }

fun RsMod.getChildModule(name: String): RsMod? =
    childModules.find { it.modName == name }

fun commonParentMod(mod1: RsMod, mod2: RsMod): RsMod? {
    val superMods1 = mod1.superMods.asReversed()
    val superMods2 = mod2.superMods.asReversed()
    val superMods = superMods1 zip superMods2
    return superMods.findLast { (superMod1, superMod2) -> superMod1 == superMod2 }?.first
}
