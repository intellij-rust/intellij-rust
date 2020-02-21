package org.rust.ide.sdk.remote

import com.intellij.util.AbstractPathMapper
import com.intellij.util.PathMappingSettings

class RsRemotePathMapper : AbstractPathMapper(), Cloneable {
    private val pathMappings: MutableMap<RsPathMappingType, MutableList<PathMappingSettings.PathMapping>> =
        mutableMapOf()

    fun addMapping(local: String?, remote: String?, type: RsPathMappingType) {
        pathMappings
            .getOrPut(type) { mutableListOf() }
            .add(PathMappingSettings.PathMapping(local, remote))
    }

    override fun isEmpty(): Boolean = pathMappings.isEmpty()

    override fun convertToLocal(remotePath: String): String {
        for (type in RsPathMappingType.values()) {
            val localPath = convertToLocal(remotePath, pathMappings[type].orEmpty())
            if (localPath != null) {
                return localPath
            }
        }
        return remotePath
    }

    override fun convertToRemote(localPath: String): String {
        for (type in RsPathMappingType.values()) {
            val remotePath = convertToRemote(localPath, pathMappings[type].orEmpty())
            if (remotePath != null) {
                return remotePath
            }
        }
        return localPath
    }

    fun addAll(mappings: Collection<PathMappingSettings.PathMapping>, type: RsPathMappingType) {
        pathMappings
            .getOrPut(type) { mutableListOf() }
            .addAll(mappings.map(::clonePathMapping))
    }

    override fun getAvailablePathMappings(): Collection<PathMappingSettings.PathMapping> =
        pathMappings.values.flatten()

    enum class RsPathMappingType {
        USER_DEFINED,
        REPLICATED_FOLDER,
        SYS_PATH,
        HELPERS
    }

    public override fun clone(): RsRemotePathMapper = cloneMapper(this)

    companion object {

        fun fromSettings(settings: PathMappingSettings, mappingType: RsPathMappingType): RsRemotePathMapper {
            val mapper = RsRemotePathMapper()
            mapper.addAll(settings.pathMappings, mappingType)
            return mapper
        }

        private fun clonePathMapping(pathMapping: PathMappingSettings.PathMapping): PathMappingSettings.PathMapping =
            PathMappingSettings.PathMapping(pathMapping.localRoot, pathMapping.remoteRoot)

        fun cloneMapper(mapper: RsRemotePathMapper?): RsRemotePathMapper {
            val pathMapper = RsRemotePathMapper()
            if (mapper != null) {
                for ((type, mappings) in mapper.pathMappings.entries) {
                    for (pathMapping in mappings) {
                        pathMapper.addMapping(pathMapping.localRoot, pathMapping.remoteRoot, type)
                    }
                }
            }
            return pathMapper
        }
    }
}
