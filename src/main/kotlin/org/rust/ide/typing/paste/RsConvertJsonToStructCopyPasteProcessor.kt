/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing.paste

import com.intellij.codeInsight.editorActions.CopyPastePostProcessor
import com.intellij.codeInsight.editorActions.TextBlockTransferableData
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.showYesNoDialog
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.rust.RsBundle
import org.rust.ide.inspections.lints.toSnakeCase
import org.rust.ide.utils.template.newTemplateBuilder
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.openapiext.createSmartPointer
import org.rust.openapiext.isUnitTestMode
import org.rust.openapiext.runWriteCommandAction
import org.rust.openapiext.toPsiFile
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

val CONVERT_JSON_ON_PASTE: RegistryValue = Registry.get("org.rust.ide.json.paste.processor")

class RsConvertJsonToStructCopyPasteProcessor : CopyPastePostProcessor<TextBlockTransferableData>() {
    override fun collectTransferableData(
        file: PsiFile?,
        editor: Editor?,
        startOffsets: IntArray?,
        endOffsets: IntArray?
    ): List<TextBlockTransferableData> = emptyList()

    override fun extractTransferableData(content: Transferable): List<TextBlockTransferableData> {
        if (!CONVERT_JSON_ON_PASTE.asBoolean()) return emptyList()
        try {
            if (content.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                val text = content.getTransferData(DataFlavor.stringFlavor) as String
                return listOf(PotentialJsonTransferableData(text))
            }
            return emptyList()
        } catch (e: Throwable) {
            return emptyList()
        }
    }

    override fun processTransferableData(
        project: Project,
        editor: Editor,
        bounds: RangeMarker,
        caretOffset: Int,
        indented: Ref<in Boolean>,
        values: List<TextBlockTransferableData>
    ) {
        val file = editor.document.toPsiFile(project) as? RsFile ?: return

        val data = values.getOrNull(0) as? PotentialJsonTransferableData ?: return
        val text = data.text

        val elementAtCaret = file.findElementAt(caretOffset)
        if (elementAtCaret != null && elementAtCaret.parent !is RsMod) return

        val structs = extractStructsFromJson(text) ?: return
        if (!shouldConvertJson(project)) return

        val factory = RsPsiFactory(project)
        val nameMap = generateStructNames(structs)

        val psiDocumentManager = PsiDocumentManager.getInstance(project)
        val insertedItems: MutableList<SmartPsiElementPointer<RsStructItem>> = mutableListOf()

        runWriteAction {
            // Delete original text
            editor.document.deleteString(bounds.startOffset, bounds.endOffset)
            psiDocumentManager.commitDocument(editor.document)

            val element = file.findElementAt(caretOffset)

            val parent = element?.parent ?: file
            var anchor = element

            for (struct in structs) {
                val inserted = createAndInsertStruct(factory, anchor, parent, struct, nameMap) ?: continue
                anchor = inserted
                insertedItems.add(inserted.createSmartPointer())
            }
        }

        if (insertedItems.isNotEmpty()) {
            replacePlaceholders(editor, insertedItems, nameMap, file)
        }
    }
}

private fun shouldConvertJson(project: Project): Boolean {
    return if (isUnitTestMode) {
        true
    } else {
        showYesNoDialog(
            RsBundle.message("copy.paste.convert.json.to.struct.dialog.title"),
            RsBundle.message("copy.paste.convert.json.to.struct.dialog.text"),
            project,
            icon = Messages.getQuestionIcon()
        )
    }
}

private fun generateStructNames(structs: List<Struct>): Map<Struct, String> {
    return if (structs.size == 1) {
        mapOf(structs[0] to "Struct")
    } else {
        structs.mapIndexed { index, struct -> struct to "Struct${index + 1}" }.toMap()
    }
}

/**
 * Creates a PSI struct from the given Struct datatype description and inserts it after the given anchor.
 */
private fun createAndInsertStruct(
    factory: RsPsiFactory,
    anchor: PsiElement?,
    parent: PsiElement,
    struct: Struct,
    nameMap: Map<Struct, String>
): RsStructItem? {
    val structPsi = generateStruct(factory, struct, nameMap) ?: return null

    val inserted = if (anchor == null) {
        parent.add(structPsi)
    } else {
        parent.addAfter(structPsi, anchor)
    } as RsStructItem

    return inserted
}

fun generateStruct(factory: RsPsiFactory, struct: Struct, nameMap: Map<Struct, String>): RsStructItem? {
    val structString = buildString {
        append("struct ${nameMap[struct]} {\n")

        for ((field, type) in struct.fields) {
            val name = normalizeFieldName(field)
            val serdeType = getSerdeType(type, nameMap)
            append("pub $name: ${serdeType},\n")
        }

        append("}")
    }
    return factory.tryCreateStruct(structString)
}

private val NON_IDENTIFIER_REGEX: Regex = Regex("[^a-zA-Z_0-9]")

fun normalizeFieldName(field: String): String {
    var name = field.replace(NON_IDENTIFIER_REGEX, "_")
    if (name.getOrNull(0)?.isDigit() == true) {
        name = "_$name"
    }

    return name.toSnakeCase(false).escapeIdentifierIfNeeded()
}

private fun getSerdeType(type: DataType, nameMap: Map<Struct, String>): String {
    return when (type) {
        DataType.Boolean -> "bool"
        DataType.String -> "String"
        DataType.Integer -> "i64"
        DataType.Float -> "f64"
        is DataType.Nullable -> "Option<${getSerdeType(type.type, nameMap)}>"
        is DataType.StructRef -> nameMap[type.struct] ?: "_"
        is DataType.Array -> "Vec<${getSerdeType(type.type, nameMap)}>"
        DataType.Unknown -> "_"
    }
}

/**
 * Replace generated struct names and _ types in the inserted structs.
 */
private fun replacePlaceholders(
    editor: Editor,
    insertedItems: List<SmartPsiElementPointer<RsStructItem>>,
    nameMap: Map<Struct, String>,
    file: RsFile
) {
    invokeLater {
        if (editor.isDisposed) return@invokeLater

        editor.project?.runWriteCommandAction {
            if (!file.isValid) return@runWriteCommandAction
            val template = editor.newTemplateBuilder(file) ?: return@runWriteCommandAction


            // Gather usages of structs in fields
            val structNames = nameMap.values.toSet()
            val visitor = StructFieldVisitor(structNames)

            val items = insertedItems.mapNotNull { it.element }
            items.forEach { it.accept(visitor) }
            val nameUsages = visitor.usages

            // Gather struct names, references to struct names and _ placeholders
            for (item in items) {
                val identifier = item.identifier
                if (identifier != null) {
                    val variable = template.introduceVariable(identifier)
                    for (usage in nameUsages[identifier.text].orEmpty()) {
                        variable.replaceElementWithVariable(usage)
                    }
                }

                val underscoreVisitor = UnderscorePathVisitor()
                item.accept(underscoreVisitor)

                for (wildcard in underscoreVisitor.paths) {
                    template.replaceElement(wildcard)
                }
            }

            template.runInline()
        }
    }
}

/**
 * Looks for underscore (`_`) paths.
 */
private class UnderscorePathVisitor : RsRecursiveVisitor() {
    private val _paths: MutableSet<RsBaseType> = linkedSetOf()

    val paths: Set<RsBaseType> get() = _paths

    override fun visitBaseType(o: RsBaseType) {
        if (o.text == "_") {
            _paths.add(o)
        }
        super.visitBaseType(o)
    }
}

/**
 * Looks for base paths that are contained in `nameMap`.
 * Returns a mapping from name to a list of usages of that name.
 */
private class StructFieldVisitor(private val structNames: Set<String>) : RsRecursiveVisitor() {
    private val _usages = linkedMapOf<String, MutableList<PsiElement>>()

    val usages: Map<String, List<PsiElement>> get() = _usages

    override fun visitBaseType(o: RsBaseType) {
        val path = o.text
        if (o.ancestorStrict<RsNamedFieldDecl>() != null && path in structNames) {
            _usages.getOrPut(path) { mutableListOf() }.add(o)
        }
        super.visitBaseType(o)
    }
}

private class PotentialJsonTransferableData(val text: String) : TextBlockTransferableData {
    override fun getFlavor(): DataFlavor = DATA_FLAVOR
    override fun getOffsetCount(): Int = 0

    override fun getOffsets(offsets: IntArray, index: Int): Int = index
    override fun setOffsets(offsets: IntArray, index: Int): Int = index

    companion object {
        val DATA_FLAVOR: DataFlavor = DataFlavor(
            RsConvertJsonToStructCopyPasteProcessor::class.java,
            "class: RsConvertJsonToStructCopyPasteProcessor"
        )
    }
}

// TODO: add serde to dependencies
// TODO: derive and import Serialize/Deserialize
// TODO: settings GUI + do not ask checkbox
