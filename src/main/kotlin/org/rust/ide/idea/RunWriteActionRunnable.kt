package org.rust.ide.idea

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import java.io.IOException

/**
 * Created by thoma on 07/07/2016.
 */
class RunWriteActionRunnable : Runnable{
    var builder : RustModuleBuilder?
    var file : VirtualFile?
    var module : Module?
    constructor(builder: RustModuleBuilder?, file: VirtualFile?, module: Module?){
        this.builder = builder
        this.file = file
        this.module = module
    }
    override fun run() {
        try{
            val src : VirtualFile? = file?.createChildDirectory(this,"src")
            if(src != null) {
                val mainFile : VirtualFile? = file?.createChildData(this, "main.rs")
            }
            val test : VirtualFile? = file?.createChildDirectory(this,"test")
            if(test != null){
                val testSrc : VirtualFile? = test.createChildDirectory(this,"src")
                val rootModel = ModuleRootManager.getInstance(module!!).modifiableModel
                val entry : ContentEntry? = RustModuleBuilder.findContentEntry(rootModel,testSrc)
                if(entry != null){
                    entry.addSourceFolder(src!!,true)
                    entry.addSourceFolder(testSrc!!,true)
                    rootModel.commit()
                }
            }
        }
        catch(ioe: IOException){
            ioe.printStackTrace(System.err)
            Messages.showErrorDialog(null,"Error Creating Project","Error")
        }
    }
}