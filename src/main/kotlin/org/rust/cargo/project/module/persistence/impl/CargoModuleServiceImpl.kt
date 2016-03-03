package org.rust.cargo.project.module.persistence.impl

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.AbstractCollection
import org.jetbrains.builtInWebServer.compareNameAndProjectBasePath
import org.rust.cargo.CargoProjectDescription
import org.rust.cargo.project.module.persistence.CargoModuleService
import org.rust.cargo.project.module.persistence.ExternCrateData
import java.util.*

@State(
    name = "CargoModuleData",
    storages = arrayOf(Storage(file = StoragePathMacros.MODULE_FILE))
)
class CargoModuleServiceImpl : PersistentStateComponent<CargoModuleServiceImpl>
                            , CargoModuleService {
    @AbstractCollection
    private var myTargets: MutableCollection<SerializableTarget> = ArrayList()

    @AbstractCollection
    private var myExternCrates: MutableCollection<SerializableExternCrate> = ArrayList()

    override fun loadState(state: CargoModuleServiceImpl) {
        XmlSerializerUtil.copyBean(state, this)
    }

    override fun getState(): CargoModuleServiceImpl = this

    override val targets: Collection<CargoProjectDescription.Target>
        get() = myTargets.mapNotNull { it.intoTarget() }

    override val externCrates: Collection<ExternCrateData>
        get() = myExternCrates.mapNotNull { it.intoExternCrate() }

    override fun saveData(targets: Collection<CargoProjectDescription.Target>, externCrates: Collection<ExternCrateData>) {
        myTargets = targets.map { SerializableTarget.fromTarget(it) }.toMutableList()
        myExternCrates = externCrates.map { SerializableExternCrate.fromExternCrate(it) }.toMutableList()
    }
}

// IDEA serializer requires objects to have default constructors,
// nullable fields and mutable collections, so we have to introduce
// an evil nullable twin of [CargoProjectDescription.Target]
//
// `null`s in fields signal serialization failure, so we can't `!!` here safely.
//
// Ideally this should be private, but alas this also breaks serialization
data class SerializableTarget(
    var path: String? = null,
    var kind: CargoProjectDescription.TargetKind? = null
) {
    fun intoTarget(): CargoProjectDescription.Target? {
        return CargoProjectDescription.Target(
            path ?: return null,
            kind ?: return null
        )
    }

    companion object {
        fun fromTarget(target: CargoProjectDescription.Target): SerializableTarget =
            SerializableTarget(target.path, target.kind)
    }
}

data class SerializableExternCrate(
    var name: String? = null,
    var path: String? = null
) {
    fun intoExternCrate(): ExternCrateData? {
        return ExternCrateData(
            name ?: return null,
            path ?: return null
        )
    }

    companion object {
        fun fromExternCrate(crate: ExternCrateData): SerializableExternCrate =
            SerializableExternCrate(
                crate.name,
                crate.path
            )
    }
}

