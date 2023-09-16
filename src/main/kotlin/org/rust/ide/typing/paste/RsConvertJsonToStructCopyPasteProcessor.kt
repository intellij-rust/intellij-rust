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
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.annotations.TestOnly
import org.rust.RsBundle
import org.rust.ide.inspections.lints.toCamelCase
import org.rust.ide.inspections.lints.toSnakeCase
import org.rust.ide.statistics.RsConvertJsonToStructUsagesCollector
import org.rust.ide.utils.import.RsImportHelper
import org.rust.ide.utils.template.newTemplateBuilder
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.RsQualifiedNamedElement
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.containingCargoPackage
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.resolve2.allScopeItemNames
import org.rust.openapiext.createSmartPointer
import org.rust.openapiext.isUnitTestMode
import org.rust.openapiext.runWriteCommandAction
import org.rust.openapiext.toPsiFile
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

class RsConvertJsonToStructCopyPasteProcessor : CopyPastePostProcessor<TextBlockTransferableData>() {
    override fun collectTransferableData(
        file: PsiFile,
        editor: Editor,
        startOffsets: IntArray,
        endOffsets: IntArray
    ): List<TextBlockTransferableData> = emptyList()

    override fun extractTransferableData(content: Transferable): List<TextBlockTransferableData> {
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
        RsConvertJsonToStructUsagesCollector.logJsonTextPasted()
        if (!shouldConvertJson(project)) return

        val factory = RsPsiFactory(project)
        // The only time `elementAtCaret` could be null is if we are pasting into an empty file
        val parentMod = elementAtCaret?.parent as? RsMod ?: file
        val existingNames = parentMod.allScopeItemNames()
        val nameMap = generateStructNames(structs, existingNames)

        val psiDocumentManager = PsiDocumentManager.getInstance(project)
        val insertedItems: MutableList<SmartPsiElementPointer<RsStructItem>> = mutableListOf()

        val hasSerdeDependency = hasSerdeDependency(file)

        runWriteAction {
            // Delete original text
            editor.document.deleteString(bounds.startOffset, bounds.endOffset)
            psiDocumentManager.commitDocument(editor.document)

            val element = file.findElementAt(caretOffset)

            val parent = element?.parent ?: file
            var anchor = element

            for (struct in structs) {
                val inserted = createAndInsertStruct(factory, anchor, parent, struct, nameMap, hasSerdeDependency) ?: continue
                anchor = inserted
                insertedItems.add(inserted.createSmartPointer())
            }
        }

        if (insertedItems.isNotEmpty()) {
            replacePlaceholders(editor, insertedItems, nameMap, file)
        }
        RsConvertJsonToStructUsagesCollector.logPastedJsonConverted()
    }
}

const val CONVERT_JSON_TO_STRUCT_SETTING_KEY: String = "org.rust.convert.json.to.struct"
private var CONVERT_JSON_SERDE_PRESENT: Boolean = false

@TestOnly
fun convertJsonWithSerdePresent(hasSerde: Boolean, action: () -> Unit) {
    val original = CONVERT_JSON_SERDE_PRESENT
    CONVERT_JSON_SERDE_PRESENT = hasSerde
    try {
        action()
    } finally {
        CONVERT_JSON_SERDE_PRESENT = original
    }
}

private fun hasSerdeDependency(file: RsFile): Boolean {
    if (isUnitTestMode && CONVERT_JSON_SERDE_PRESENT) {
        return true
    }
    return file.containingCargoPackage?.dependencies?.any { it.name == "serde" } == true
}

enum class StoredPreference {
    YES,
    NO,
    ASK_EVERY_TIME;

    override fun toString(): String = when (this) {
        YES -> "Yes"
        NO -> "No"
        ASK_EVERY_TIME -> "Ask every time"
    }
}

private fun shouldConvertJson(project: Project): Boolean {
    return if (isUnitTestMode) {
        true
    } else {
        when (AdvancedSettings.getEnum(CONVERT_JSON_TO_STRUCT_SETTING_KEY, StoredPreference::class.java)) {
            StoredPreference.YES -> true
            StoredPreference.NO -> false
            StoredPreference.ASK_EVERY_TIME -> {
                MessageDialogBuilder.yesNo(
                    title=RsBundle.message("copy.paste.convert.json.to.struct.dialog.title"),
                    message=RsBundle.message("copy.paste.convert.json.to.struct.dialog.text")
                )
                    .yesText(Messages.getYesButton())
                    .noText(Messages.getNoButton())
                    .icon(Messages.getQuestionIcon())
                    .doNotAsk(object : DoNotAskOption.Adapter() {
                        override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
                            if (isSelected) {
                                val value = when (exitCode) {
                                    Messages.YES -> StoredPreference.YES
                                    else -> StoredPreference.NO
                                }
                                AdvancedSettings.setEnum(CONVERT_JSON_TO_STRUCT_SETTING_KEY, value)

                                // `exitCode != Messages.CANCEL` is always true since we don't override `shouldSaveOptionsOnCancel`
                                RsConvertJsonToStructUsagesCollector.logRememberChoiceResult(value == StoredPreference.YES)
                            }
                        }
                    })
                    .ask(project)
            }
        }
    }
}

private fun generateStructNames(structs: List<Struct>, existingNames: Set<String>): Map<Struct, String> {
    val map = mutableMapOf<Struct, String>()
    if (structs.isEmpty()) return map

    val assignedNames = existingNames.toMutableSet()
    val assignName = { struct: Struct, name: String ->
        val normalizedName = normalizeStructName(name)

        val actualName = if (normalizedName in assignedNames) {
            generateSequence(1) { it + 1 }
                .map { "$normalizedName$it" }
                .first { it !in assignedNames }
        } else {
            normalizedName
        }

        assignedNames.add(actualName)
        map[struct] = actualName
    }
    assignName(structs.last(), "Root")

    // Maps structs to fields under which they are embedded
    val structEmbeddedFields = mutableMapOf<Struct, MutableSet<String>>()
    val visitor = object: DataTypeVisitor() {
        override fun visitStruct(dataType: DataType.StructRef) {
            for ((field, type) in dataType.struct.fields) {
                val innerType = type.unwrap()
                if (innerType is DataType.StructRef) {
                    structEmbeddedFields.getOrPut(innerType.struct) { mutableSetOf() }.add(field)
                }
            }
            super.visitStruct(dataType)
        }
    }
    visitor.visit(DataType.StructRef(structs.last()))

    for (struct in structs.reversed()) {
        if (struct !in map) {
            val fields = structEmbeddedFields[struct].orEmpty()
            if (fields.size == 1) {
                assignName(struct, fields.first())
            } else {
                assignName(struct, "Struct")
            }
        }
    }
    return map
}

/**
 * Creates a PSI struct from the given Struct datatype description and inserts it after the given anchor.
 */
private fun createAndInsertStruct(
    factory: RsPsiFactory,
    anchor: PsiElement?,
    parent: PsiElement,
    struct: Struct,
    nameMap: Map<Struct, String>,
    hasSerdeDependency: Boolean
): RsStructItem? {
    val structPsi = generateStruct(factory, struct, nameMap, hasSerdeDependency) ?: return null

    val inserted = if (anchor == null) {
        parent.add(structPsi)
    } else {
        parent.addAfter(structPsi, anchor)
    } as RsStructItem

    if (hasSerdeDependency) {
        val knownItems = inserted.knownItems
        val traits: List<RsQualifiedNamedElement> = listOfNotNull(
            knownItems.findItem("serde::Serialize", isStd = false),
            knownItems.findItem("serde::Deserialize", isStd = false)
        )
        for (trait in traits) {
            RsImportHelper.importElement(inserted, trait)
        }
    }

    return inserted
}

private fun StringBuilder.writeStructField(
    field: String,
    type: DataType,
    structNameMap: Map<Struct, String>,
    generatedFieldNames: MutableSet<String>,
    hasSerdeDependency: Boolean
) {
    val normalizedName = createFieldName(field, generatedFieldNames)
    val serdeType = getSerdeType(type, structNameMap)
    if (hasSerdeDependency && field != normalizedName) {
        // Escape quotes
        val rawField = field.replace("\"", "\\\"")
        append("#[serde(rename = \"$rawField\")]\n")
    }

    append("pub $normalizedName: ${serdeType},\n")
    generatedFieldNames.add(normalizedName)
}

private fun generateStruct(
    factory: RsPsiFactory,
    struct: Struct,
    nameMap: Map<Struct, String>,
    hasSerdeDependency: Boolean
): RsStructItem? {
    val structString = buildString {
        if (hasSerdeDependency) {
            append("#[derive(Serialize, Deserialize)]\n")
        }
        append("struct ${nameMap[struct]} {\n")

        val names = mutableSetOf<String>()
        for ((field, type) in struct.fields) {
           writeStructField(field, type, nameMap, names, hasSerdeDependency)
        }

        append("}")
    }
    return factory.tryCreateStruct(structString)
}

private val NON_IDENTIFIER_REGEX: Regex = Regex("[^a-zA-Z_0-9]")

private fun normalizeName(name: String, placeholder: String, caseConversion: (String) -> String): String {
    var normalized = name.replace(NON_IDENTIFIER_REGEX, "_")
    normalized = caseConversion(normalized)

    if (normalized.getOrNull(0)?.isDigit() == true) {
        normalized = "_$normalized"
    }

    if (normalized.all { it == '_' }) {
        normalized += placeholder
    }

    return normalized.escapeIdentifierIfNeeded()
}

private fun normalizeFieldName(field: String): String {
    return normalizeName(field, "field") { it.toSnakeCase(false) }
}

private fun normalizeStructName(struct: String): String {
    return normalizeName(struct, "Struct") { it.toCamelCase() }
}

private fun createFieldName(field: String, generatedFieldNames: Set<String>): String {
    val normalizedName = normalizeFieldName(field)
    if (normalizedName !in generatedFieldNames) return normalizedName

    return generateSequence(0) { it + 1 }
        .map { "${normalizedName}_$it" }
        .first { it !in generatedFieldNames }
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

        editor.project?.runWriteCommandAction(
            RsBundle.message("copy.paste.convert.json.to.struct.dialog.title")
        ) {
            if (!file.isValid) return@runWriteCommandAction
            val tpl = editor.newTemplateBuilder(file)

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
                    val variable = tpl.introduceVariable(identifier)
                    for (usage in nameUsages[identifier.text].orEmpty()) {
                        variable.replaceElementWithVariable(usage)
                    }
                }

                val underscoreVisitor = InferTypeVisitor()
                item.accept(underscoreVisitor)

                for (wildcard in underscoreVisitor.types) {
                    tpl.replaceElement(wildcard)
                }
            }

            tpl.runInline()
        }
    }
}

/**
 * Looks for underscore (`_`) types.
 */
private class InferTypeVisitor : RsRecursiveVisitor() {
    private val _types: MutableSet<RsInferType> = linkedSetOf()

    val types: Set<RsInferType> get() = _types

    override fun visitInferType(o: RsInferType) {
        _types.add(o)
        super.visitInferType(o)
    }
}

/**
 * Looks for base paths that are contained in `nameMap`.
 * Returns a mapping from name to a list of usages of that name.
 */
private class StructFieldVisitor(private val structNames: Set<String>) : RsRecursiveVisitor() {
    private val _usages = linkedMapOf<String, MutableList<PsiElement>>()

    val usages: Map<String, List<PsiElement>> get() = _usages

    override fun visitPathType(o: RsPathType) {
        val path = o.text
        if (o.ancestorStrict<RsNamedFieldDecl>() != null && path in structNames) {
            _usages.getOrPut(path) { mutableListOf() }.add(o)
        }
        super.visitPathType(o)
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
