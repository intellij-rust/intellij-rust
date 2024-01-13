package org.rust.bsp.service

import ch.epfl.scala.bsp4j.BuildTargetIdentifier
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import org.rust.cargo.project.workspace.CargoWorkspaceData


@State(
    name = "BspProjectViewService",
    storages = [Storage("bspProjectView.xml")],
    reportStatistic = true
)
class BspProjectViewService(val project: Project) :
    PersistentStateComponent<BspProjectState> {



    private var state = BspProjectState()
    override fun getState(): BspProjectState {
        return state
    }

    override fun loadState(state: BspProjectState) {
        this.state = state
    }

    private fun mapPackagesToPojo(rustPackages: List<CargoWorkspaceData.Package>): BspProjectState {
        val result = mutableSetOf<String>()
        rustPackages.forEach { pkg ->
            pkg.allTargets.forEach {
                result.add(pkg.name + ':' + it.name)
            }
        }
        return BspProjectState(result)
    }

    fun getActiveTargets() = state.targets.toList()
    fun updateTargets(
        rustPackages: List<BuildTargetIdentifier>
    ): List<String> {
        state = BspProjectState(rustPackages.map{it.uri}.toMutableSet())
        return state.targets.toList()
    }

    fun filterIncludedPackages(
        rustTargets: List<BuildTargetIdentifier>
    ): List<BuildTargetIdentifier> {
        if (state.targets.isEmpty()) return rustTargets
        return rustTargets.filter { it.uri in state.targets }
    }

    fun includePackage(
        target: BuildTargetIdentifier
    ) {
        state.targets.add(target.uri)
    }

    fun excludePackage(
        target: BuildTargetIdentifier
    ) {
        state.targets.remove(target.uri)
    }

    companion object {
        fun getInstance(project: Project): BspProjectViewService =
            project.getService(BspProjectViewService::class.java)
    }
}


data class BspProjectState(
    var targets: MutableSet<String> = mutableSetOf()
)
