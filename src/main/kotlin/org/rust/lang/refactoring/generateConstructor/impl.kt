package org.rust.lang.refactoring.generateConstructor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import org.rust.lang.core.psi.*
import org.rust.openapiext.checkWriteAccessAllowed

fun generateConstructorBody(structItem : RsStructItem, editor: Editor){
    check(!ApplicationManager.getApplication().isWriteAccessAllowed)
    val chosenFields = showConstructorArgumentsChooser(structItem, structItem.project)
    runWriteAction {
        insertNewConstructor(structItem,chosenFields, editor)
    }
}

fun insertNewConstructor(structItem: RsStructItem,fields:Collection<RsFieldDecl>?, editor: Editor){
    checkWriteAccessAllowed()
    if(fields==null) return
    val rsPsiFactory = RsPsiFactory(editor.project?:return)
    var expr =rsPsiFactory.createImpl(structItem.name?:return, listOf(getFunction(structItem, fields, rsPsiFactory)))
    expr = structItem.parent.addAfter(expr,structItem) as RsImplItem
    editor.caretModel.moveToOffset(expr.textOffset+expr.textLength-1)
}

fun getFunction(structItem: RsStructItem,fields: Collection<RsFieldDecl>, rsPsiFactory: RsPsiFactory) : RsFunction{
    val arguments = buildString {
        append(fields.joinToString ( prefix = "(", postfix = ")", separator = ",") { "${it.identifier.text}:${it.typeReference!!.text}" })
    }
    val body = buildString {
        append(structItem.nameIdentifier!!.text)
        append(fields.joinToString(prefix = "{", separator = "," ) { it.identifier.text })
        val extraFields = structItem.blockFields!!.fieldDeclList.minus(fields)
        if (extraFields.isNotEmpty()){
            if(fields.isNotEmpty()){
                append(",")
            }
            append(extraFields.joinToString(separator = ",") { it.identifier.text + ":()" })
        }
        append("}")
    }
    return rsPsiFactory.createTraitMethodMember("pub fn new$arguments->Self{\n$body}\n")
}

