/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.intellij.ProjectTopics
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.psi.*
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.messages.Topic
import org.rust.cargo.project.model.CargoProjectsService
import org.rust.cargo.project.model.CargoProjectsService.CargoProjectsListener
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.RsFileType
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.crate.crateGraph
import org.rust.lang.core.macros.MacroExpansionFileSystem
import org.rust.lang.core.macros.MacroExpansionMode
import org.rust.lang.core.macros.macroExpansionManagerIfCreated
import org.rust.lang.core.psi.RsPsiManager.Companion.isIgnorePsiEvents
import org.rust.lang.core.psi.RsPsiTreeChangeEvent.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve2.defMapService

/** Don't subscribe directly or via plugin.xml lazy listeners. Use [RsPsiManager.subscribeRustStructureChange] */
private val RUST_STRUCTURE_CHANGE_TOPIC: Topic<RustStructureChangeListener> = Topic.create(
    "RUST_STRUCTURE_CHANGE_TOPIC",
    RustStructureChangeListener::class.java,
    Topic.BroadcastDirection.TO_PARENT
)

/** Don't subscribe directly or via plugin.xml lazy listeners. Use [RsPsiManager.subscribeRustPsiChange] */
private val RUST_PSI_CHANGE_TOPIC: Topic<RustPsiChangeListener> = Topic.create(
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

    /**
     * Similar to [rustStructureModificationTracker], but it is not incremented by changes in
     * workspace rust files.
     *
     * @see PackageOrigin.WORKSPACE
     */
    val rustStructureModificationTrackerInDependencies: SimpleModificationTracker

    fun incRustStructureModificationCount()

    /** This is an instance method because [RsPsiManager] should be created prior to event subscription */
    fun subscribeRustStructureChange(connection: MessageBusConnection, listener: RustStructureChangeListener) {
        connection.subscribe(RUST_STRUCTURE_CHANGE_TOPIC, listener)
    }

    /** This is an instance method because [RsPsiManager] should be created prior to event subscription */
    fun subscribeRustPsiChange(connection: MessageBusConnection, listener: RustPsiChangeListener) {
        connection.subscribe(RUST_PSI_CHANGE_TOPIC, listener)
    }

    companion object {
        private val IGNORE_PSI_EVENTS: Key<Boolean> = Key.create("IGNORE_PSI_EVENTS")

        fun <T> withIgnoredPsiEvents(psi: PsiFile, f: () -> T): T {
            setIgnorePsiEvents(psi, true)
            try {
                return f()
            } finally {
                setIgnorePsiEvents(psi, false)
            }
        }

        fun isIgnorePsiEvents(psi: PsiFile): Boolean =
            psi.getUserData(IGNORE_PSI_EVENTS) == true

        private fun setIgnorePsiEvents(psi: PsiFile, ignore: Boolean) {
            psi.putUserData(IGNORE_PSI_EVENTS, if (ignore) true else null)
        }
    }
}

interface RustStructureChangeListener {
    fun rustStructureChanged(file: PsiFile?, changedElement: PsiElement?)
}

interface RustPsiChangeListener {
    fun rustPsiChanged(file: PsiFile, element: PsiElement, isStructureModification: Boolean)
}

class RsPsiManagerImpl(val project: Project) : RsPsiManager, Disposable {

    override val rustStructureModificationTracker = SimpleModificationTracker()
    override val rustStructureModificationTrackerInDependencies = SimpleModificationTracker()

    init {
        PsiManager.getInstance(project).addPsiTreeChangeListener(CacheInvalidator(), this)
        project.messageBus.connect().subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootListener {
            override fun rootsChanged(event: ModuleRootEvent) {
                incRustStructureModificationCount()
            }
        })
        project.messageBus.connect().subscribe(CargoProjectsService.CARGO_PROJECTS_TOPIC, CargoProjectsListener { _, _ ->
            incRustStructureModificationCount()
        })
    }

    override fun dispose() {}

    inner class CacheInvalidator : RsPsiTreeChangeAdapter() {
        override fun handleEvent(event: RsPsiTreeChangeEvent) {
            val element = when (event) {
                is ChildRemoval.Before -> event.child
                is ChildRemoval.After -> event.parent
                is ChildReplacement.Before -> event.oldChild
                is ChildReplacement.After -> event.newChild
                is ChildAddition.After -> event.child
                is ChildMovement.After -> event.child
                is ChildrenChange.After -> if (!event.isGenericChange) event.parent else return
                is PropertyChange.After -> {
                    when (event.propertyName) {
                        PsiTreeChangeEvent.PROP_UNLOADED_PSI, PsiTreeChangeEvent.PROP_FILE_TYPES -> {
                            incRustStructureModificationCount()
                            return
                        }
                        PsiTreeChangeEvent.PROP_WRITABLE -> return
                        else -> event.element ?: return
                    }
                }
                else -> return
            }

            val file = event.file

            // if file is null, this is an event about VFS changes
            if (file == null) {
                val isStructureModification = element is RsFile && !isIgnorePsiEvents(element)
                    || element is PsiDirectory && project.cargoProjects.findPackageForFile(element.virtualFile) != null
                if (isStructureModification) {
                    incRustStructureModificationCount(element as? RsFile, element as? RsFile)
                }
            } else {
                if (file.fileType != RsFileType) return
                if (isIgnorePsiEvents(file)) return

                val isWhitespaceOrComment = element is PsiComment || element is PsiWhiteSpace
                if (isWhitespaceOrComment && !isMacroExpansionModeNew) {
                    // Whitespace/comment changes are meaningful if new macro expansion engine is used
                    return
                }

                // Most of events means that some element *itself* is changed, but ChildrenChange means
                // that changed some of element's children, not the element itself. In this case
                // we should look up for ModificationTrackerOwner a bit differently
                val isChildrenChange = event is ChildrenChange || event is ChildRemoval.After

                updateModificationCount(file, element, isChildrenChange, isWhitespaceOrComment)
            }
        }

    }

    private fun updateModificationCount(
        file: PsiFile,
        psi: PsiElement,
        isChildrenChange: Boolean,
        isWhitespaceOrComment: Boolean
    ) {
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

        val owner = if (DumbService.isDumb(project)) null else psi.findModificationTrackerOwner(!isChildrenChange)

        // Whitespace/comment changes are meaningful for macros only
        // (b/c they affect range mappings and body hashes)
        if (isWhitespaceOrComment) {
            if (owner !is RsMacroCall && owner !is RsMacroDefinitionBase && !RsProcMacroPsiUtil.canBeInProcMacroCallBody(psi)) return
        }

        val isStructureModification = owner == null || !owner.incModificationCount(psi)

        if (!isStructureModification && owner is RsMacroCall &&
            (!isMacroExpansionModeNew || !owner.isTopLevelExpansion)) {
            return updateModificationCount(file, owner, isChildrenChange = false, isWhitespaceOrComment = false)
        }

        if (isStructureModification) {
            incRustStructureModificationCount(file, psi)
        }
        project.messageBus.syncPublisher(RUST_PSI_CHANGE_TOPIC).rustPsiChanged(file, psi, isStructureModification)
    }

    private val isMacroExpansionModeNew
        get() = project.macroExpansionManagerIfCreated?.macroExpansionMode is MacroExpansionMode.New

    override fun incRustStructureModificationCount() =
        incRustStructureModificationCount(null, null)

    private fun incRustStructureModificationCount(file: PsiFile? = null, psi: PsiElement? = null) {
        rustStructureModificationTracker.incModificationCount()
        if (!isWorkspaceFile(file)) {
            rustStructureModificationTrackerInDependencies.incModificationCount()
        }
        project.messageBus.syncPublisher(RUST_STRUCTURE_CHANGE_TOPIC).rustStructureChanged(file, psi)
    }

    private fun isWorkspaceFile(file: PsiFile?): Boolean {
        if (file !is RsFile) return false
        val virtualFile = file.virtualFile ?: return false
        val crates = if (virtualFile.fileSystem is MacroExpansionFileSystem) {
            val crateId = project.macroExpansionManagerIfCreated?.getCrateForExpansionFile(virtualFile) ?: return false
            listOf(crateId)
        } else {
            project.defMapService.findCrates(file)
        }
        if (crates.isEmpty()) return false
        val crateGraph =  project.crateGraph
        if (crates.any { crateGraph.findCrateById(it)?.origin != PackageOrigin.WORKSPACE }) return false

        return true
    }
}

val Project.rustPsiManager: RsPsiManager get() = service()

/** @see RsPsiManager.rustStructureModificationTracker */
val Project.rustStructureModificationTracker: ModificationTracker
    get() = rustPsiManager.rustStructureModificationTracker

/**
 * Returns [RsPsiManager.rustStructureModificationTracker] if [Crate.origin] == [PackageOrigin.WORKSPACE] or
 * [RsPsiManager.rustStructureModificationTrackerInDependencies] otherwise
 */
val Crate.rustStructureModificationTracker: ModificationTracker
    get() = if (origin == PackageOrigin.WORKSPACE) {
        project.rustStructureModificationTracker
    } else {
        project.rustPsiManager.rustStructureModificationTrackerInDependencies
    }

/**
 * Returns [RsPsiManager.rustStructureModificationTracker] or [PsiModificationTracker.MODIFICATION_COUNT]
 * if `this` element is inside language injection
 */
val RsElement.rustStructureOrAnyPsiModificationTracker: ModificationTracker
    get() {
        val containingFile = containingFile
        return when {
            // The case of injected language. Injected PSI doesn't have its own event system, so can only
            // handle evens from outer PSI. For example, Rust language is injected to Kotlin's string
            // literal. If a user change the literal, we can only be notified that the literal is changed.
            // So we have to invalidate the cached value on any PSI change
            containingFile.virtualFile is VirtualFileWindow ->
                PsiManager.getInstance(containingFile.project).modificationTracker

            containingFile.containingRsFileSkippingCodeFragments?.crate?.origin == PackageOrigin.WORKSPACE ->
                containingFile.project.rustStructureModificationTracker

            else -> containingFile.project.rustPsiManager.rustStructureModificationTrackerInDependencies
        }
    }
