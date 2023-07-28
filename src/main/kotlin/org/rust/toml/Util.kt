/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import org.rust.RsBundle
import org.rust.cargo.CargoConstants
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.ide.notifications.showBalloonWithoutProject
import org.rust.lang.core.completion.getElementOfType
import org.rust.lang.core.psi.ext.*
import org.rust.openapiext.toPsiFile
import org.toml.lang.psi.*
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind
import kotlin.reflect.KProperty


fun tomlPluginIsAbiCompatible(): Boolean = computeOnce

private val computeOnce: Boolean by lazy {
    try {
        load<TomlKeySegment>()
        true
    } catch (e: LinkageError) {
        showBalloonWithoutProject(
            RsBundle.message("notification.content.incompatible.toml.plugin.version.code.completion.for.cargo.toml.not.available"),
            NotificationType.WARNING
        )
        false
    }
}

private inline fun <reified T : Any> load(): String = T::class.java.name

@Suppress("unused")
private fun load(p: KProperty<*>): String = p.name

val PsiFile.isCargoToml: Boolean get() = name == CargoConstants.MANIFEST_FILE

val TomlKeySegment.isDependencyKey: Boolean
    get() {
        val name = name
        return name == "dependencies" || name == "dev-dependencies" || name == "build-dependencies"
    }

val TomlKeySegment.isFeaturesKey: Boolean
    get() {
        val name = name
        return name == "features"
    }

val TomlKeySegment.isFeatureDef: Boolean
    get() {
        val table = (parent?.parent as? TomlKeyValue)?.parent as? TomlTable ?: return false
        return table.header.isFeatureListHeader && table.containingFile.isCargoToml
    }

val TomlTableHeader.isDependencyListHeader: Boolean
    get() = key?.segments?.lastOrNull()?.isDependencyKey == true

val TomlTableHeader.isSpecificDependencyTableHeader: Boolean
    get() {
        val names = key?.segments.orEmpty()
        return names.getOrNull(names.size - 2)?.isDependencyKey == true
    }

val TomlTableHeader.isFeatureListHeader: Boolean
    get() = key?.segments?.lastOrNull()?.isFeaturesKey == true

/** Inserts `=` between key and value if missed and wraps inserted string with quotes if needed */
class StringValueInsertionHandler(private val keyValue: TomlKeyValue) : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        var startOffset = context.startOffset
        val value = context.getElementOfType<TomlValue>()
        val hasEq = keyValue.children.any { it.elementType == TomlElementTypes.EQ }
        val hasQuotes = value != null && (value !is TomlLiteral || value.literalType != TomlElementTypes.NUMBER)

        if (!hasEq) {
            context.document.insertString(startOffset - if (hasQuotes) 1 else 0, "= ")
            PsiDocumentManager.getInstance(context.project).commitDocument(context.document)
            startOffset += 2
        }

        if (!hasQuotes) {
            context.document.insertString(startOffset, "\"")
            context.document.insertString(context.selectionEndOffset, "\"")
        }
    }
}

/** Wraps inserted string with quotes if needed */
class StringLiteralInsertionHandler : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val leaf = context.getElementOfType<PsiElement>() ?: return
        val hasQuotes = leaf.parent is TomlLiteral && leaf.elementType in TOML_STRING_LITERALS

        if (!hasQuotes) {
            context.document.insertString(context.startOffset, "\"")
            context.document.insertString(context.selectionEndOffset, "\"")
        }
    }
}

private val TomlLiteral.literalType: IElementType
    get() = children.first().elementType

fun TomlKeyValueOwner.getValueWithKey(key: String): TomlValue? =
    entries.find { it.key.text == key }?.value

fun getClosestKeyValueAncestor(position: PsiElement): TomlKeyValue? {
    val parent = position.parent ?: return null
    val keyValue = parent.ancestorOrSelf<TomlKeyValue>()
        ?: error("PsiElementPattern must not allow values outside of TomlKeyValues")
    // If a value is already present we should ensure that the value is a literal
    // and the caret is inside the value to forbid completion in cases like
    // `key = "" <caret>`
    val value = keyValue.value
    return when {
        value == null || !(value !is TomlLiteral || !value.isAncestorOf(position)) -> keyValue
        else -> null
    }
}

fun CargoWorkspace.Package.getPackageCargoTomlFile(project: Project): TomlFile? {
    return contentRoot?.findChild(CargoConstants.MANIFEST_FILE)
        ?.toPsiFile(project)
        as? TomlFile
}

fun PsiElement.findCargoPackageForCargoToml(): CargoWorkspace.Package? {
    val containingFile = containingFile.originalFile
    return containingFile.findCargoPackage()?.takeIf { it.getPackageCargoTomlFile(containingFile.project) == containingFile }
}

private fun CargoWorkspace.Package.findDependencyByPackageName(pkgName: String): CargoWorkspace.Package? =
    dependencies.find { it.cargoFeatureDependencyPackageName == pkgName }?.pkg

fun findDependencyTomlFile(element: TomlElement, depName: String): TomlFile? =
    element.findCargoPackageForCargoToml()
        ?.findDependencyByPackageName(depName)
        ?.getPackageCargoTomlFile(element.project)

/**
 * Consider `Cargo.toml`:
 *
 * ```
 * [dependencies]
 * foo = { version = "*",                 features = [] }
 * #X                ^ returns `foo` for this value #^ and for this value
 * ```
 *
 * ```
 * [dependencies.foo]
 * features = []
 *           #^ returns `foo` for this value
 * ```
 *
 * @see [org.rust.toml.resolve.CargoTomlDependencyFeaturesReferenceProvider]
 */
val TomlValue.containingDependencyKey: TomlKeySegment?
    get() {
        val parentParent = parent?.parent as? TomlElement ?: return null
        return if (parentParent is TomlTable) {
            // [dependencies.foo]
            parentParent.header.key?.segments?.lastOrNull()
        } else {
            // [dependencies]
            // foo = { ... }
            (parentParent.parent as? TomlKeyValue)?.key?.segments?.singleOrNull()
        }
    }

val TomlKey.stringValue: String
    get() {
        return segments.map { it.name }.joinToString(".")
    }

val TomlValue.stringValue: String?
    get() {
        val kind = (this as? TomlLiteral)?.kind
        return (kind as? TomlLiteralKind.String)?.value
    }

val TomlFile.tableList: List<TomlTable> get() = childrenOfType<TomlTable>()


fun TomlFile.findDependencyElement(dependencyName: String): TomlElement? {
    // Check for cases like:
    //     [dependencies.foo]
    //     version = "1.0.0"
    val existingInlinedDependency = tableList.find { table ->
        table.header.key?.stringValue == "dependencies.$dependencyName"
    }
    if (existingInlinedDependency != null) {
        return existingInlinedDependency
    }

    // Check for cases like:
    //     [dependencies.xxx]
    //     name = "foo"
    //     version = "1.0.0"
    val existingInlinedDependencyWithName = tableList.find { table ->
        val headerSegments = table.header.key?.segments ?: return@find false
        headerSegments.size > 1
            && headerSegments.firstOrNull()?.name == "dependencies"
            && table.entries.any {
                entry -> entry.key.stringValue == "name" && entry.value?.stringValue == dependencyName
            }
    }
    if (existingInlinedDependencyWithName != null) {
        return existingInlinedDependencyWithName
    }

    val dependenciesTable = tableList.find {
        it.header.key?.segments?.singleOrNull()?.name == "dependencies"
    } ?: return null

    // Check for cases like:
    //     [dependencies]
    //     foo = "1.0.0"
    // or
    //     [dependencies]
    //     foo = { version = "1.0.0" }
    val existingEntry = dependenciesTable.entries.find { entry ->
        entry.key.stringValue == dependencyName
            && (entry.value as? TomlInlineTable)?.entries?.find { it.key.stringValue == "name" } == null
    }
    if (existingEntry != null) {
        return existingEntry.value
    }

    // Check for cases like:
    //     [dependencies]
    //     xxx = { name = "foo", version = "1.0.0" }
    val existingEntryWithName = dependenciesTable.entries.find { entry ->
        (entry.value as? TomlInlineTable)?.entries?.any {
            it.key.stringValue == "name" && it.value?.stringValue == dependencyName
        } == true
    }
    if (existingEntryWithName != null) {
        return existingEntryWithName.value
    }
    return null
}

