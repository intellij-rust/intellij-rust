package org.rust.cargo.project.module.persistence.impl

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.AbstractCollection
import org.rust.cargo.CargoProjectDescription
import org.rust.cargo.project.module.persistence.CargoModuleTargetsService
import java.util.*

@State(
    name = "CargoModuleTargets",
    storages = arrayOf(Storage(file = StoragePathMacros.MODULE_FILE))
)
class CargoModuleTargetsServiceImpl : PersistentStateComponent<CargoModuleTargetsServiceImpl>
                                    , CargoModuleTargetsService {

    @AbstractCollection
    private var myTargets: MutableCollection<SerializableTarget> = ArrayList()

    override fun loadState(state: CargoModuleTargetsServiceImpl) {
        XmlSerializerUtil.copyBean(state, this)
    }

    override fun getState(): CargoModuleTargetsServiceImpl = this

    override val targets: Collection<CargoProjectDescription.Target>
        get() = myTargets.mapNotNull { it.intoTarget() }

    override fun saveTargets(targets: Collection<CargoProjectDescription.Target>) {
        myTargets = targets.map { SerializableTarget.fromTarget(it) }.toMutableList()
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

