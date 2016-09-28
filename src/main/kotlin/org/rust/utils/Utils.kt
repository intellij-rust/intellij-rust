package org.rust.utils

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.download.DownloadableFileService
import kotlin.reflect.KProperty


/**
 * Helper disposing [d] upon completing the execution of the [block]
 *
 * @d       Target `Disposable` to be disposed upon completion of the @block
 * @block   Target block to be run prior to disposal of @d
 */
fun <T> using(d: Disposable, block: () -> T): T {
    try {
        return block()
    } finally {
        d.dispose()
    }
}

/**
 * Helper disposing [d] upon completing the execution of the [block] (under the [d])
 *
 * @d       Target `Disposable` to be disposed upon completion of the @block
 * @block   Target block to be run prior to disposal of @d
 */
fun <D : Disposable, T> usingWith(d: D, block: (D) -> T): T {
    try {
        return block(d)
    } finally {
        d.dispose()
    }
}

/**
 * Cached value invalidated on any PSI modification
 */
fun <E : PsiElement, T> psiCached(provider: E.() -> CachedValueProvider<T>): PsiCacheDelegate<E, T> = PsiCacheDelegate(provider)

class PsiCacheDelegate<E : PsiElement, T>(val provider: E.() -> CachedValueProvider<T>) {
    operator fun getValue(element: E, property: KProperty<*>): T {
        return CachedValuesManager.getCachedValue(element, element.provider())
    }
}

/**
 * Extramarital son of `sequenceOf` & `listOfNotNull`
 */
fun <T : Any> sequenceOfNotNull(vararg elements: T): Sequence<T> = listOfNotNull(*elements).asSequence()

fun <T : Any> sequenceOfNotNull(element: T?): Sequence<T> = if (element != null) sequenceOf(element) else emptySequence()


/**
 * Downloads file residing at [url] with the name [fileName] and saves it to [destination] folder
 */
fun download(url: String, fileName: String, destination: VirtualFile): VirtualFile? {
    val downloadService = DownloadableFileService.getInstance()
    val downloader = downloadService.createDownloader(listOf(downloadService.createFileDescription(url, fileName)), fileName)

    val downloadTo = VfsUtilCore.virtualToIoFile(destination)
    val (file, @Suppress("UNUSED_VARIABLE") d) = downloader.download(downloadTo).single()

    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
}


/**
 * XXX
 */
fun <T> safely(run: () -> T, finally: () -> Unit): T = usingWith(Disposable { finally() }) { run() }
