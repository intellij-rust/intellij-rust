package org.rust.cargo.toolchain.impl

import com.intellij.util.containers.MultiMap
import com.intellij.util.xmlb.annotations.Transient
import com.intellij.util.xmlb.annotations.AbstractCollection
import org.rust.cargo.CargoProjectDescription
import java.util.*

class CargoProjectState {
    @AbstractCollection
    private var packages: MutableCollection<SerializablePackage> = ArrayList()

    @AbstractCollection
    private var dependencies: MutableCollection<DependencyNode> = ArrayList()

    @get:Transient
    var cargoProjectDescription: CargoProjectDescription?
        get() {
            val packages = packages.map { it.into() ?: return null }
            val deps = MultiMap<Int, Int>().apply {
                for ((pkg, pkgDeps) in dependencies) {
                    putValues(pkg, pkgDeps)
                }
            }
            return CargoProjectDescription.create(packages, deps)
        }
        set(projectDescription) {
            if (projectDescription == null) {
                packages = ArrayList()
                dependencies = ArrayList()
            } else {
                packages = projectDescription.packages.map { SerializablePackage.from(it) }.toMutableList()
                dependencies = projectDescription.rawDependencies.entrySet().map {
                    DependencyNode(it.key, it.value)
                }.toMutableList()
            }
        }
}


// IDEA serializer requires objects to have default constructors,
// nullable fields and mutable collections, so we have to introduce
// an evil nullable twin of [CargoProjectDescription]
//
// `null`s in fields signal serialization failure, so we can't `!!` here safely.
//
// Ideally this should be private, but alas this also breaks serialization
data class DependencyNode(
    var index: Int = 0,
    var dependenciesIndices: MutableCollection<Int> = ArrayList()
)

data class SerializablePackage(
    var contentRootUrl: String? = null,
    var name: String? = null,
    var version: String? = null,
    var targets: Collection<SerializableTarget> = ArrayList(),
    var source: String? = null
) {
    fun into(): CargoProjectDescription.Package? {
        return CargoProjectDescription.Package(
            contentRootUrl ?: return null,
            name ?: return null,
            version ?: return null,
            targets.map { it.into() ?: return null },
            source
        )
    }

    companion object {
        fun from(pkg: CargoProjectDescription.Package): SerializablePackage {
            return SerializablePackage(
                pkg.contentRootUrl,
                pkg.name,
                pkg.version,
                pkg.targets.map { SerializableTarget.from(it) },
                pkg.source
            )
        }
    }
}


data class SerializableTarget(
    var url: String? = null,
    var kind: CargoProjectDescription.TargetKind? = null
) {
    fun into(): CargoProjectDescription.Target? {
        return CargoProjectDescription.Target(
            url ?: return null,
            kind ?: return null
        )
    }

    companion object {
        fun from(target: CargoProjectDescription.Target): SerializableTarget =
            SerializableTarget(target.url, target.kind)
    }
}
