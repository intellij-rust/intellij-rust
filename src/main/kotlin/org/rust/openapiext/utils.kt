/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.openapiext

import com.intellij.concurrency.SensitiveProgressWrapper
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.laf.UIThemeBasedLookAndFeelInfo
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.TrailingSpacesStripper
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.*
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.psi.*
import com.intellij.psi.impl.PsiDocumentManagerBase
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.UIUtil
import org.jdom.Element
import org.jdom.input.SAXBuilder
import org.rust.cargo.RustfmtWatcher
import org.rust.ide.annotator.RsExternalLinterPass
import java.lang.ref.SoftReference
import java.lang.reflect.Field
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicReference
import kotlin.Pair
import kotlin.reflect.KProperty

val isUnitTestMode: Boolean get() = ApplicationManager.getApplication().isUnitTestMode
val isHeadlessEnvironment: Boolean get() = ApplicationManager.getApplication().isHeadlessEnvironment
val isDispatchThread: Boolean get() = ApplicationManager.getApplication().isDispatchThread
val isInternal: Boolean get() = ApplicationManager.getApplication().isInternal
val isUnderDarkTheme: Boolean
    get() {
        val lookAndFeel = LafManager.getInstance().currentLookAndFeel as? UIThemeBasedLookAndFeelInfo
        return lookAndFeel?.theme?.isDark == true || UIUtil.isUnderDarcula()
    }

/**
 * Perform a write action for the provided project.
 *
 * This method should be used for the implementation of all user-initiated IDE actions.
 * i.e. those which should appear in the Undo/Redo stack. It explicitly requires the name
 * and groupId of the action are provided, unlike the `WriteCommandAction` methods which
 * allow omitting that information or to use "Undefined" defaults.
 *
 * Actions which should not be undoable by the user, such as internal macro expansion or
 * actions in tests, should not use this method. You can use methods on `WriteCommandAction`
 * instead.
 *
 * @param commandName the name of the action which will appear for the user in the Undo/Redo stack.
 * @param files files modified by the action. This may be safely omitted if a single file is
 *      modified. Otherwise, it is recommended to explicitly specify modified files, to ensure
 *      that the operation doesn't fail halfway due to some files being non-writable.
 * @param command the write action to perform. It can also return some value.
 */
fun <T> Project.runWriteCommandAction(
    @Suppress("UnstableApiUsage")
    @NlsContexts.Command commandName: String,
    vararg files: PsiFile,
    command: () -> T
): T {
    return WriteCommandAction.writeCommandAction(this, *files)
        .withName(commandName)
        .compute<T, RuntimeException>(command)
}

/** This is modification of [runUndoTransparentWriteAction] which applies formatting to modified code */
fun Project.runUndoTransparentWriteCommandAction(command: () -> Unit) {
    CommandProcessor.getInstance().runUndoTransparentAction {
        WriteCommandAction.runWriteCommandAction(this, command)
    }
}

val Project.modules: Collection<Module>
    get() = ModuleManager.getInstance(this).modules.toList()


fun <T> recursionGuard(key: Any, block: Computable<T>, memoize: Boolean = true): T? =
    RecursionManager.doPreventingRecursion(key, memoize, block)


fun checkWriteAccessAllowed() {
    check(ApplicationManager.getApplication().isWriteAccessAllowed) {
        "Needs write action"
    }
}

fun checkWriteAccessNotAllowed() {
    check(!ApplicationManager.getApplication().isWriteAccessAllowed)
}

fun checkReadAccessAllowed() {
    check(ApplicationManager.getApplication().isReadAccessAllowed) {
        "Needs read action"
    }
}

fun checkReadAccessNotAllowed() {
    check(!ApplicationManager.getApplication().isReadAccessAllowed)
}

fun checkIsDispatchThread() {
    check(ApplicationManager.getApplication().isDispatchThread) {
        "Should be invoked on the Swing dispatch thread"
    }
}

fun checkIsBackgroundThread() {
    check(!ApplicationManager.getApplication().isDispatchThread) {
        "Long running operation invoked on UI thread"
    }
}

fun checkIsSmartMode(project: Project) {
    if (DumbService.getInstance(project).isDumb) throw IndexNotReadyException.create()
}

fun checkCommitIsNotInProgress(project: Project) {
    val app = ApplicationManager.getApplication()
    if ((app.isUnitTestMode || app.isInternal) && app.isDispatchThread) {
        if ((PsiDocumentManager.getInstance(project) as PsiDocumentManagerBase).isCommitInProgress) {
            error("Accessing indices during PSI event processing can lead to typing performance issues")
        }
    }
}

fun fullyRefreshDirectory(directory: VirtualFile) {
    VfsUtil.markDirtyAndRefresh(/* async = */ false, /* recursive = */ true, /* reloadChildren = */ true, directory)
}

fun VirtualFile.findFileByMaybeRelativePath(path: String): VirtualFile? =
    if (FileUtil.isAbsolute(path))
        fileSystem.findFileByPath(path)
    else
        findFileByRelativePath(path)

fun VirtualFile.findNearestExistingFile(path: String): Pair<VirtualFile, List<String>> {
    var file = this
    val segments = StringUtil.split(path, "/")
    segments.forEachIndexed { i, segment ->
        file = file.findChild(segment) ?: return file to segments.subList(i, segments.size)
    }
    return file to emptyList()
}

val VirtualFile.pathAsPath: Path get() = Paths.get(path)

fun VirtualFile.toPsiFile(project: Project): PsiFile? =
    PsiManager.getInstance(project).findFile(this)

fun VirtualFile.toPsiDirectory(project: Project): PsiDirectory? =
    PsiManager.getInstance(project).findDirectory(this)

fun Document.toPsiFile(project: Project): PsiFile? =
    PsiDocumentManager.getInstance(project).getPsiFile(this)

val Document.virtualFile: VirtualFile?
    get() = FileDocumentManager.getInstance().getFile(this)

val VirtualFile.document: Document?
    get() = FileDocumentManager.getInstance().getDocument(this)

val PsiFile.document: Document?
    get() = viewProvider.document

val VirtualFile.fileId: Int
    get() = (this as VirtualFileWithId).id

inline fun <Key: Any, reified Psi : PsiElement> getElements(
    indexKey: StubIndexKey<Key, Psi>,
    key: Key, project: Project,
    scope: GlobalSearchScope?
): Collection<Psi> =
    StubIndex.getElements(indexKey, key, project, scope, Psi::class.java)


fun Element.toXmlString() = JDOMUtil.writeElement(this)

fun elementFromXmlString(xml: String): Element =
    SAXBuilder().build(xml.byteInputStream()).rootElement

class CachedVirtualFile(private val url: String?) {
    private val cache = AtomicReference<VirtualFile>()

    operator fun getValue(thisRef: Any?, property: KProperty<*>): VirtualFile? {
        if (url == null) return null
        val cached = cache.get()
        if (cached != null && cached.isValid) return cached
        val file = VirtualFileManager.getInstance().findFileByUrl(url)
        cache.set(file)
        return file
    }
}

fun saveAllDocuments() = FileDocumentManager.getInstance().saveAllDocuments()

/**
 * Calling of [saveAllDocuments] uses [TrailingSpacesStripper] to format all unsaved documents.
 *
 * In case of [RsExternalLinterPass] it backfires:
 * 1. Calling [TrailingSpacesStripper.strip] on *every* file change.
 * 2. Double run of external linter, because [TrailingSpacesStripper.strip] generates new "PSI change" events.
 *
 * This function saves all documents "as they are" (see [FileDocumentManager.saveDocumentAsIs]), but also fires that
 * these documents should be stripped later (when [saveAllDocuments] is called).
 */
fun saveAllDocumentsAsTheyAre(reformatLater: Boolean = true) {
    val documentManager = FileDocumentManager.getInstance()
    val rustfmtWatcher = RustfmtWatcher.getInstance()
    rustfmtWatcher.withoutReformatting {
        for (document in documentManager.unsavedDocuments) {
            documentManager.saveDocumentAsIs(document)
            documentManager.stripDocumentLater(document)
            if (reformatLater) rustfmtWatcher.reformatDocumentLater(document)
        }
    }
}

private fun FileDocumentManager.stripDocumentLater(document: Document): Boolean {
    if (this !is FileDocumentManagerImpl) return false
    val trailingSpacesStripper = trailingSpacesStripperField
        ?.get(this) as? TrailingSpacesStripper ?: return false

    @Suppress("UNCHECKED_CAST")
    val documentsToStripLater = documentsToStripLaterField
        ?.get(trailingSpacesStripper) as? MutableSet<Document> ?: return false
    return documentsToStripLater.add(document)
}

private val trailingSpacesStripperField: Field? =
    initFieldSafely<FileDocumentManagerImpl>("myTrailingSpacesStripper")

private val documentsToStripLaterField: Field? =
    initFieldSafely<TrailingSpacesStripper>("myDocumentsToStripLater")

private inline fun <reified T> initFieldSafely(fieldName: String): Field? =
    try {
        T::class.java
            .getDeclaredField(fieldName)
            .apply { isAccessible = true }
    } catch (e: Throwable) {
        if (isUnitTestMode) throw e else null
    }

inline fun testAssert(action: () -> Boolean) {
    testAssert(action) { "Assertion failed" }
}

inline fun testAssert(action: () -> Boolean, lazyMessage: () -> Any) {
    if (isUnitTestMode && !action()) {
        val message = lazyMessage()
        throw AssertionError(message)
    }
}

fun <T> runWithCheckCanceled(callable: () -> T): T =
    ApplicationUtil.runWithCheckCanceled(callable, ProgressManager.getInstance().progressIndicator)

fun <T> Project.computeWithCancelableProgress(
    @Suppress("UnstableApiUsage") @ProgressTitle title: String,
    supplier: () -> T
): T {
    if (isUnitTestMode) {
        return supplier()
    }
    return ProgressManager.getInstance().runProcessWithProgressSynchronously<T, Exception>(supplier, title, true, this)
}

fun Project.runWithCancelableProgress(
    @Suppress("UnstableApiUsage") @ProgressTitle title: String,
    process: () -> Unit
): Boolean {
    if (isUnitTestMode) {
        process()
        return true
    }
    return ProgressManager.getInstance().runProcessWithProgressSynchronously(process, title, true, this)
}

inline fun <T : Any> UserDataHolderEx.getOrPut(key: Key<T>, defaultValue: () -> T): T =
    getUserData(key) ?: putUserDataIfAbsent(key, defaultValue())

const val PLUGIN_ID: String = "org.rust.lang"

fun plugin(): IdeaPluginDescriptor = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID))!!

val String.escaped: String get() = StringUtil.escapeXmlEntities(this)

fun <T> runReadActionInSmartMode(dumbService: DumbService, action: () -> T): T {
    ProgressManager.checkCanceled()
    if (dumbService.project.isDisposed) throw ProcessCanceledException()
    return dumbService.runReadActionInSmartMode(Computable {
        ProgressManager.checkCanceled()
        action()
    })
}

fun <T : Any> executeUnderProgressWithWriteActionPriorityWithRetries(
    indicator: ProgressIndicator,
    action: (ProgressIndicator) -> T
): T {
    indicator.checkCanceled()
    if (isUnitTestMode && ApplicationManager.getApplication().isReadAccessAllowed) {
        return action(indicator)
    } else {
        checkReadAccessNotAllowed()
    }
    var result: T? = null
    do {
        val wrappedIndicator = SensitiveProgressWrapper(indicator)
        val success = runWithWriteActionPriority(wrappedIndicator) {
            result = action(wrappedIndicator)
        }
        if (!success) {
            indicator.checkCanceled()
            // wait for write action to complete
            ApplicationManager.getApplication().runReadAction(EmptyRunnable.getInstance())
        }
    } while (!success)
    return result!!
}

fun runWithWriteActionPriority(indicator: ProgressIndicator, action: () -> Unit): Boolean =
    ProgressIndicatorUtils.runWithWriteActionPriority(action, indicator)

fun runInReadActionWithWriteActionPriority(indicator: ProgressIndicator, action: () -> Unit): Boolean =
    ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(action, indicator)

fun <T : Any> computeInReadActionWithWriteActionPriority(indicator: ProgressIndicator, action: () -> T): T {
    lateinit var result: T
    val success = runInReadActionWithWriteActionPriority(indicator) {
        result = action()
    }
    if (!success) throw ProcessCanceledException()
    return result
}

fun <T> executeUnderProgress(indicator: ProgressIndicator, action: () -> T): T {
    var result: T? = null
    ProgressManager.getInstance().executeProcessUnderProgress({ result = action() }, indicator)
    @Suppress("UNCHECKED_CAST")
    return result ?: (null as T)
}

/**
 * [this] indicator can be an instance of [com.intellij.openapi.progress.impl.BackgroundableProcessIndicator]
 * class, which is thread sensitive and its [ProgressIndicator.checkCanceled] method should be used only from
 * a single thread (see [com.intellij.openapi.progress.util.ProgressWindow.MyDelegate.checkCanceled]).
 * So we propagate cancellation.
 */
fun ProgressIndicator.toThreadSafeProgressIndicator(): ProgressIndicator {
    return if (this is ProgressIndicatorEx) {
        val threadSafeIndicator = EmptyProgressIndicator()
        addStateDelegate(object : AbstractProgressIndicatorExBase() {
            override fun cancel() = threadSafeIndicator.cancel()
        })
        threadSafeIndicator
    } else {
        this
    }
}

fun <T : PsiElement> T.createSmartPointer(): SmartPsiElementPointer<T> =
    SmartPointerManager.getInstance(project).createSmartPsiElementPointer(this)

val DataContext.psiFile: PsiFile?
    get() = getData(CommonDataKeys.PSI_FILE)

val DataContext.editor: Editor?
    get() = getData(CommonDataKeys.EDITOR)

val DataContext.project: Project?
    get() = getData(CommonDataKeys.PROJECT)

val DataContext.elementUnderCaretInEditor: PsiElement?
    get() {
        val psiFile = psiFile ?: return null
        val editor = editor ?: return null

        return psiFile.findElementAt(editor.caretModel.offset)
    }

fun isFeatureEnabled(featureId: String): Boolean {
    // Hack to pass values of experimental features in headless IDE run
    // Should help to configure IDE-based tools like Qodana
    if (isHeadlessEnvironment) {
        val value = System.getProperty(featureId)?.toBooleanStrictOrNull()
        if (value != null) return value
    }

    return Experiments.getInstance().isFeatureEnabled(featureId)
}

fun setFeatureEnabled(featureId: String, enabled: Boolean) = Experiments.getInstance().setFeatureEnabled(featureId, enabled)

fun <T> runWithEnabledFeatures(vararg featureIds: String, action: () -> T): T {
    val currentValues = featureIds.map { it to isFeatureEnabled(it) }
    featureIds.forEach { setFeatureEnabled(it, true) }
    return try {
        action()
    } finally {
        currentValues.forEach { (featureId, currentValue) -> setFeatureEnabled(featureId, currentValue) }
    }
}

/**
 * Returns result of [provider] and store it in [dataHolder] among with [dependency].
 * If stored dependency equals [dependency], then returns stored result, without invoking [provider].
 */
fun <T, D> getCachedOrCompute(
    dataHolder: UserDataHolder,
    key: Key<SoftReference<Pair<T, D>>>,
    dependency: D,
    provider: () -> T
): T {
    val oldResult = dataHolder.getUserData(key)?.get()
    if (oldResult != null && oldResult.second == dependency) {
        return oldResult.first
    }
    val value = provider()
    dataHolder.putUserData(key, SoftReference(value to dependency))
    return value
}

/** Intended to be invoked from EDT */
inline fun <R> Project.nonBlocking(crossinline block: () -> R, crossinline uiContinuation: (R) -> Unit) {
    if (isUnitTestMode) {
        val result = block()
        uiContinuation(result)
    } else {
        ReadAction.nonBlocking(Callable {
            block()
        })
            .inSmartMode(this)
            .expireWith(RsPluginDisposable.getInstance(this))
            .finishOnUiThread(ModalityState.current()) { result ->
                uiContinuation(result)
            }.submit(AppExecutorUtil.getAppExecutorService())
    }
}

@Service
class RsPluginDisposable : Disposable {
    companion object {
        @JvmStatic
        fun getInstance(project: Project): Disposable = project.service<RsPluginDisposable>()
    }

    override fun dispose() {}
}

inline fun <reified T: Configurable> Project.showSettingsDialog() {
    ShowSettingsUtil.getInstance().showSettingsDialog(this, T::class.java)
}
