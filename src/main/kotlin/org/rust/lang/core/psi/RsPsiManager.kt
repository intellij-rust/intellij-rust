/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.intellij.ProjectTopics
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.psi.*
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.messages.Topic
import org.rust.cargo.project.model.cargoProjects
import org.rust.lang.RsFileType
import org.rust.lang.core.psi.RsPsiTreeChangeEvent.*
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.findModificationTrackerOwner

val RUST_STRUCTURE_CHANGE_TOPIC: Topic<RustStructureChangeListener> = Topic.create(
    "RUST_STRUCTURE_CHANGE_TOPIC",
    RustStructureChangeListener::class.java,
    Topic.BroadcastDirection.TO_PARENT
)

val RUST_PSI_CHANGE_TOPIC: Topic<RustPsiChangeListener> = Topic.create(
    "RUST_PSI_CHANGE_TOPIC",
    RustPsiChangeListener::class.java,
    Topic.BroadcastDirection.TO_PARENT
)

interface RsPsiManager {
    /**
     * A project-global modification tracker that increments on each PSI change that can affect
     * name resolution or type inference. It will be incremented with a change of most types of
     * PSI element excluding function bodies (expressions and statements)
     */
    val rustStructureModificationTracker: ModificationTracker
}

interface RustStructureChangeListener {
    fun rustStructureChanged()
}

interface RustPsiChangeListener {
    fun rustPsiChanged(element: PsiElement)
}

class RsPsiManagerImpl(val project: Project) : ProjectComponent, RsPsiManager {

    override val rustStructureModificationTracker = SimpleModificationTracker()

    override fun projectOpened() {
        PsiManager.getInstance(project).addPsiTreeChangeListener(CacheInvalidator())
        project.messageBus.connect().subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootListener {
            override fun rootsChanged(event: ModuleRootEvent) {
                incRustStructureModificationCount()
            }
        })
    }

    inner class CacheInvalidator : RsPsiTreeChangeAdapter() {
        override fun handleEvent(event: RsPsiTreeChangeEvent) {
            val element = when (event) {
                is ChildRemoval.Before -> event.child
                is ChildReplacement.Before -> event.oldChild
                is ChildReplacement.After -> event.newChild
                is ChildAddition.After -> event.child
                is ChildMovement.After -> event.child
                is ChildrenChange.After -> if (!event.isGenericChange) event.parent else return
                is PropertyChange.After -> {
                    if (event.propertyName == PsiTreeChangeEvent.PROP_WRITABLE) return
                    event.element ?: return
                }
                else -> return
            }

            val file = event.file

            // if file is null, this is an event about VFS changes
            if (file == null) {
                if (element is RsFile ||
                    element is PsiDirectory && project.cargoProjects.findPackageForFile(element.virtualFile) != null) {
                    incRustStructureModificationCount()
                }
            } else {
                if (file.fileType != RsFileType) return

                if (element is PsiComment || element is PsiWhiteSpace) return

                // Most of events means that some element *itself* is changed, but ChildrenChange means
                // that changed some of element's children, not the element itself. In this case
                // we should look up for ModificationTrackerOwner a bit differently
                val isChildrenChange = event is ChildrenChange

                updateModificationCount(element, isChildrenChange)
            }
        }

    }

    private fun updateModificationCount(psi: PsiElement, isChildrenChange: Boolean) {
        // We find the nearest parent item or macro call (because macro call can produce items)
        // If found item implements RsModificationTrackerOwner, we increment its own
        // modification counter. Otherwise we increment global modification counter.
        //
        // So, if something is changed inside a function except an item, we will only
        // increment the function local modification counter.
        //
        // It may not be intuitive that if we change an item inside a function,
        // like this struct: `fn foo() { struct Bar; }`, we will increment the
        // global modification counter instead of function-local. We do not care
        // about it because it is a rare case and implementing it differently
        // is much more difficult.

        val owner = psi.findModificationTrackerOwner(!isChildrenChange)
        if (owner == null || !owner.incModificationCount(psi)) {
            incRustStructureModificationCount()
        }
        project.messageBus.syncPublisher(RUST_PSI_CHANGE_TOPIC).rustPsiChanged(psi)
    }

    private fun incRustStructureModificationCount() {
        rustStructureModificationTracker.incModificationCount()
        project.messageBus.syncPublisher(RUST_STRUCTURE_CHANGE_TOPIC).rustStructureChanged()
    }
}

private val Project.rustPsiManager: RsPsiManager
    get() = getComponent(RsPsiManager::class.java)

/** @see RsPsiManager.rustStructureModificationTracker */
val Project.rustStructureModificationTracker: ModificationTracker
    get() = rustPsiManager.rustStructureModificationTracker

/**
 * Returns [RsPsiManager.rustStructureModificationTracker] or [PsiModificationTracker.MODIFICATION_COUNT]
 * if `this` element is inside language injection
 */
val RsElement.rustStructureOrAnyPsiModificationTracker: Any
    get() {
        val containingFile = containingFile
        return when {
            // The case of injected language. Injected PSI don't have it's own event system, so can only
            // handle evens from outer PSI. For example, Rust language is injected to Kotlin's string
            // literal. If a user change the literal, we can only be notified that the literal is changed.
            // So we have to invalidate the cached value on any PSI change
            containingFile.virtualFile is VirtualFileWindow -> PsiModificationTracker.MODIFICATION_COUNT
            else -> containingFile.project.rustStructureModificationTracker
        }
    }
