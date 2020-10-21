/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.intellij.openapi.project.Project
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.lang.core.macros.setContext
import org.rust.lang.core.parser.RustParserUtil.PathParsingMode
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.resolve.Namespace
import org.rust.lang.core.resolve.TYPES_N_VALUES
import org.rust.openapiext.toPsiFile


class RsCodeFragmentFactory(val project: Project) {
    private val psiFactory = RsPsiFactory(project, markGenerated = false)

    fun createCrateRelativePath(pathText: String, target: CargoWorkspace.Target): RsPath? {
        check(!pathText.startsWith("::"))
        val vFile = target.crateRoot ?: return null
        val crateRoot = vFile.toPsiFile(project) as? RsFile ?: return null
        return createPath(pathText, crateRoot)
    }

    fun createPath(
        path: String,
        context: RsElement,
        mode: PathParsingMode = PathParsingMode.TYPE,
        ns: Set<Namespace> = TYPES_N_VALUES
    ): RsPath? {
        return RsPathCodeFragment(project, path, false, context, mode, ns).path
    }

    fun createPathInTmpMod(
        importingPathName: String,
        context: RsMod,
        mode: PathParsingMode,
        ns: Set<Namespace>,
        usePath: String,
        crateName: String?
    ): RsPath? {
        val (externCrateItem, useItem) = if (crateName != null) {
            "extern crate $crateName;" to "use self::$usePath;"
        } else {
            "" to "use $usePath;"
        }
        val mod = psiFactory.createModItem(TMP_MOD_NAME, """
            $externCrateItem
            use super::*;
            $useItem
            """)
        mod.setContext(context)
        return createPath(importingPathName, mod, mode, ns)
    }
}

const val TMP_MOD_NAME: String = "__tmp__"
