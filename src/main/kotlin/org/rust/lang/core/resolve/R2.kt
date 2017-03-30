package org.rust.lang.core.resolve

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiManager
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.util.getPsiFor
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.RustType
import org.rust.lang.core.types.stripAllRefsIfAny
import org.rust.lang.core.types.type
import org.rust.lang.core.types.types.RustStructType

class ResolveConfig(
    val isCompletion: Boolean
)

private data class SimpleVariant(override val name: String, override val element: RsCompositeElement) : Variant

private class LazyVariant(
    override val name: String,
    thunk: Lazy<RsCompositeElement?>
) : Variant {
    override val element: RsCompositeElement? by thunk

    override fun toString(): String = "LazyVariant($name, $element)"
}

private operator fun RsResolveProcessor.invoke(name: String, e: RsCompositeElement): Boolean {
    return this(SimpleVariant(name, e))
}

private fun RsResolveProcessor.lazy(name: String, e: () -> RsCompositeElement?): Boolean {
    return this(LazyVariant(name, lazy(e)))
}

private operator fun RsResolveProcessor.invoke(e: RsNamedElement): Boolean {
    val name = e.name ?: return false
    return this(name, e)
}

private fun processAll(es: List<RsNamedElement>, processor: RsResolveProcessor): Boolean = es.any { processor(it) }


/// References

fun processResolveVariants(fieldExpr: RsFieldExpr, config: ResolveConfig, processor: RsResolveProcessor): Boolean {
    val receiverType = fieldExpr.expr.type.stripAllRefsIfAny()

    val struct = (receiverType as? RustStructType)?.item
    if (struct != null && processFields(struct, processor)) return true

    if (config.isCompletion) {
        processMethods(fieldExpr.project, receiverType, processor)
    }

    return false
}

fun processResolveVariants(field: RsStructExprField, processor: RsResolveProcessor): Boolean {
    val structOrEnumVariant = field.parentStructExpr.path.reference.resolve() as? RsFieldsOwner ?: return false
    return processFields(structOrEnumVariant, processor)
}

fun processResolveVariants(callExpr: RsMethodCallExpr, processor: RsResolveProcessor): Boolean {
    val receiverType = callExpr.expr.type
    return processMethods(callExpr.project, receiverType, processor)
}

fun processResolveVariants(glob: RsUseGlob, processor: RsResolveProcessor): Boolean {
    val useItem = glob.parentUseItem
    val basePath = useItem.path
    val baseItem = (if (basePath != null)
        basePath.reference.resolve()
    else
    // `use ::{foo, bar}`
        glob.crateRoot) ?: return false

    if (processor("self", baseItem)) return true

    return processDeclarations(baseItem, processor)
}


/**
 * Looks-up file corresponding to particular module designated by `mod-declaration-item`:
 *
 *  ```
 *  // foo.rs
 *  pub mod bar; // looks up `bar.rs` or `bar/mod.rs` in the same dir
 *
 *  pub mod nested {
 *      pub mod baz; // looks up `nested/baz.rs` or `nested/baz/mod.rs`
 *  }
 *
 *  ```
 *
 *  | A module without a body is loaded from an external file, by default with the same name as the module,
 *  | plus the '.rs' extension. When a nested sub-module is loaded from an external file, it is loaded
 *  | from a subdirectory path that mirrors the module hierarchy.
 *
 * Reference:
 *      https://github.com/rust-lang/rust/blob/master/src/doc/reference.md#modules
 */
fun processResolveVariants(modDecl: RsModDeclItem, processor: RsResolveProcessor): Boolean {
    val dir = modDecl.containingMod.ownedDirectory ?: return false

    val explicitPath = modDecl.pathAttribute
    if (explicitPath != null) {
        val vFile = dir.virtualFile.findFileByRelativePath(explicitPath) ?: return false
        val mod = PsiManager.getInstance(modDecl.project).findFile(vFile)?.rustMod ?: return false

        val name = modDecl.name ?: return false
        return processor(name, mod)
    }
    if (modDecl.isLocal) return false

    for (file in dir.files) {
        if (file == modDecl.containingFile.originalFile || file.name == RsMod.MOD_RS) continue
        val mod = file.rustMod ?: continue
        val name = FileUtil.getNameWithoutExtension(file.name)
        if (processor(name, mod)) return true
    }

    for (d in dir.subdirectories) {
        val mod = d.findFile(RsMod.MOD_RS)?.rustMod ?: continue
        if (processor(d.name, mod)) return true
    }

    return false
}

fun processResolveVariants(crate: RsExternCrateItem, isCompletion: Boolean, processor: RsResolveProcessor): Boolean {
    val module = crate.module ?: return false
    val pkg = crate.containingCargoPackage ?: return false
    fun processPackage(pkg: CargoWorkspace.Package): Boolean {
        if (isCompletion && pkg.origin != PackageOrigin.DEPENDENCY) return false
        val libTarget = pkg.libTarget ?: return false
        return processor.lazy(libTarget.normName) {
            module.project.getPsiFor(libTarget.crateRoot)?.rustMod
        }
    }

    if (processPackage(pkg)) return true
    for (p in pkg.dependencies) {
        if (processPackage(p)) return true
    }
    return false
}


/// Named elements

fun processFields(struct: RsFieldsOwner, processor: RsResolveProcessor): Boolean {
    if (processAll(struct.namedFields, processor)) return true

    for ((idx, field) in struct.positionalFields.withIndex()) {
        if (processor(idx.toString(), field)) return true
    }
    return false
}

fun processMethods(project: Project, receiver: RustType, processor: RsResolveProcessor): Boolean {
    val (inherent, nonInherent) = receiver.getMethodsIn(project).partition { it is RsFunction && it.isInherentImpl }
    if (processAll(inherent, processor)) return true

    val inherentNames = inherent.mapNotNull { it.name }.toHashSet()
    for (fn in nonInherent) {
        if (fn.name in inherentNames) continue
        if (processor(fn)) return true
    }
    return false
}

fun processDeclarations(scope: RsCompositeElement, processor: RsResolveProcessor): Boolean {
    when (scope) {
        is RsEnumItem -> if (processAll(scope.enumBody.enumVariantList, processor)) {
            return true
        }
        is RsFile -> if (processDeclarations(scope, false, processor)) {
            return true
        }
        is RsMod -> if (processDeclarations(scope, false, processor)) {
            return true
        }
    }

    return false
}

fun processDeclarations(scope: RsItemsOwner, withPrivateImports: Boolean, processor: RsResolveProcessor): Boolean {
    for (modDecl in scope.modDeclItemList) {
        val name = modDecl.name ?: continue
        val mod = modDecl.reference.resolve() ?: continue
        if (processor(name, mod)) return true
    }

    if (processAll(scope.functionList, processor)
        || processAll(scope.enumItemList, processor)
        || processAll(scope.modItemList, processor)
        || processAll(scope.constantList, processor)
        || processAll(scope.structItemList, processor)
        || processAll(scope.traitItemList, processor)
        || processAll(scope.typeAliasList, processor)) {
        return true
    }

    for (fmod in scope.foreignModItemList) {
        if (processAll(fmod.functionList, processor)) return true
        if (processAll(fmod.constantList, processor)) return true
    }

    for (crate in scope.externCrateItemList) {
        val name = crate.alias?.name ?: crate.name ?: continue
        val mod = crate.reference.resolve() ?: continue
        if (processor(name, mod)) return true
    }

    val (starImports, itemImports) = scope.useItemList
        .filter { it.isPublic || withPrivateImports }
        .partition { it.isStarImport }

    for (use in itemImports) {
        val globList = use.useGlobList
        if (globList == null) {
            val path = use.path ?: continue
            val name = use.alias?.name ?: path.referenceName ?: continue
            // XXX
            if (processor.lazy(name, { path.reference.resolve() })) return true
        } else {
            for (glob in globList.useGlobList) {
                val name = glob.alias?.name
                    ?: (if (glob.isSelf) use.path?.referenceName else null)
                    ?: glob.referenceName
                    ?: continue
                // XXX
                if (processor.lazy(name, { glob.reference.resolve() })) return true
            }
        }
    }

    for (use in starImports) {
        val mod = use.path?.reference?.resolve() ?: continue
//        val newCtx = context.copy(visitedStarImports = context.visitedStarImports + this)
        if (processDeclarations(mod, processor)) return true
    }

    return false
}

