/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.intellij.ProjectTopics
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.psi.*
import com.intellij.psi.impl.PsiTreeChangeEventImpl
import com.intellij.util.messages.Topic
import org.rust.lang.RsFileType
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

    inner class CacheInvalidator : PsiTreeChangeAdapter() {
        override fun beforeChildRemoval(event: PsiTreeChangeEvent) = onPsiChange(event)
        override fun beforeChildReplacement(event: PsiTreeChangeEvent) = onPsiChange(event, event.oldChild)
        override fun childReplaced(event: PsiTreeChangeEvent) = onPsiChange(event)
        override fun childAdded(event: PsiTreeChangeEvent) = onPsiChange(event)
        override fun childrenChanged(event: PsiTreeChangeEvent) = onPsiChange(event)
        override fun childMoved(event: PsiTreeChangeEvent) = onPsiChange(event)
        override fun propertyChanged(event: PsiTreeChangeEvent) = onPsiChange(event)

        private fun onPsiChange(event: PsiTreeChangeEvent, child: PsiElement? = event.child) {
            // `GenericChange` event means that "something changed in the file" and sends
            // after all events for concrete PSI changes in a file.
            // We handle more concrete events and so should ignore generic event
            if (event is PsiTreeChangeEventImpl && event.isGenericChange) return

            // There are some cases when PsiFile stored in the event as a child
            // e.g. file removal by external VFS change
            val file = event.file ?: event.child as? PsiFile
            if (file?.fileType != RsFileType) return

            if (child is PsiComment || child is PsiWhiteSpace) return

            updateModificationCount(child ?: event.parent)
        }
    }

    private fun updateModificationCount(psi: PsiElement) {
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

        val owner = psi.findModificationTrackerOwner()
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
