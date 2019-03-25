/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.profiler

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.profiler.DummyFlameChartBuilder
import com.intellij.profiler.api.BaseCallStackElement
import com.intellij.profiler.clion.perf.*
import com.intellij.profiler.fastRemoveCppFunctionArgs
import com.intellij.profiler.model.NativeThread
import com.intellij.profiler.model.ThreadInfo
import org.rust.clion.profiler.perf.RsPerfNavigatableNativeCall
import java.io.File

class RsCachedPerfFlameChartBuilder(
    cacheSize: Int,
    private val addressSymbolizer: AddressSymbolizer?,
    private val indicator: ProgressIndicator
) {
    private val samplesCache: Array<PerfSample?>
    private val addressSymbolizerCache: MutableMap<PerfFrame, AddressSymbolizerItem>
    private var cacheIndex = 0
    private val flameChartBuilder = DummyFlameChartBuilder<ThreadInfo, BaseCallStackElement>()

    init {
        check(cacheSize > 0)
        samplesCache = arrayOfNulls(cacheSize)
        addressSymbolizerCache = PerfUtils.createLRUCache(cacheSize, 0.75f)
    }

    fun add(sample: PerfSample) {
        samplesCache[cacheIndex++] = sample
        if (cacheIndex == samplesCache.size) {
            addChunksToFlameChartBuilder()
            cacheIndex = 0
        }
    }

    fun getFlameChartBuilder(): DummyFlameChartBuilder<ThreadInfo, BaseCallStackElement> {
        if (cacheIndex != 0) {
            addChunksToFlameChartBuilder()
            cacheIndex = 0
        }
        return flameChartBuilder
    }

    private fun addChunksToFlameChartBuilder() {
        ProgressManager.checkCanceled()
        val symbolizerMap = buildAddressSymbolizerMap()
        for (index in 0 until cacheIndex) {
            val sample = samplesCache[index]
            flameChartBuilder.add(NativeThread(sample!!.threadId, sample.threadName),
                sample.frames.asReversed().map { createNavigatableNativeCall(it, symbolizerMap) },
                1)
        }
    }

    private fun createNavigatableNativeCall(
        frame: PerfFrame,
        symbolizerMap: Map<PerfFrame, AddressSymbolizerItem>?
    ): RsPerfNavigatableNativeCall {
        val symbolizerItem = symbolizerMap?.get(frame)
        val binaryName = File(frame.binary).name
        if (symbolizerItem != null && symbolizerItem.isNotEmpty()) {
            return RsPerfNavigatableNativeCall(binaryName, symbolizerItem.deMangledFunction,
                symbolizerItem.filePath, symbolizerItem.line)
        }
        return RsPerfNavigatableNativeCall(binaryName, fastRemoveCppFunctionArgs(frame.function, true))
    }

    private fun buildAddressSymbolizerMap(): Map<PerfFrame, AddressSymbolizerItem>? {
        if (addressSymbolizer == null) return null

        val result = HashMap<PerfFrame, AddressSymbolizerItem>()
        val allFrames = samplesCache.slice(0 until cacheIndex).flatMap { it!!.frames }
        val cachedFrames = allFrames.filter { addressSymbolizerCache.containsKey(it) }
        cachedFrames.forEach { result[it] = addressSymbolizerCache[it]!! }
        val uncachedFrames = allFrames.filter { !addressSymbolizerCache.containsKey(it) }
        val framesGroupedByBinary = uncachedFrames.groupBy { it.binary }
        for (binaryToFramesPair in framesGroupedByBinary) {
            ProgressManager.checkCanceled()
            val binary = binaryToFramesPair.key
            val binaryFrames = binaryToFramesPair.value
            val addressesToConvert = binaryFrames.map { it.instructionPointer }
            val symbolizerItems = addressSymbolizer.execute(binary, addressesToConvert, indicator)
            if (addressesToConvert.size == symbolizerItems.size) {
                binaryFrames.forEachIndexed { index, frame ->
                    run {
                        addressSymbolizerCache[frame] = symbolizerItems[index]
                        result[frame] = symbolizerItems[index]
                    }
                }
            } else {
                LOG.warn("Skipping symbolizer output for: $binary: output is incorrect")
            }
        }
        return result
    }

    companion object {
        private val LOG = Logger.getInstance(CachedPerfFlameChartBuilder::class.java)
    }
}
