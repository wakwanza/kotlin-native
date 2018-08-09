package org.jetbrains.kotlin.library.impl

import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.konan.file.File
import org.jetbrains.kotlin.konan.properties.Properties
import org.jetbrains.kotlin.konan.properties.loadProperties
import org.jetbrains.kotlin.konan.properties.propertyList
import org.jetbrains.kotlin.konan.properties.propertyString
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.util.defaultTargetSubstitutions
import org.jetbrains.kotlin.konan.util.substitute
import org.jetbrains.kotlin.library.KotlinLibraryMetadataReader
import org.jetbrains.kotlin.library.KotlinLibraryReader

class KotlinLibraryReaderImpl(
        val libraryFile: File,
        val currentAbiVersion: Int,
        val target: KonanTarget? = null,
        override val isDefaultLibrary: Boolean = false,
        val metadataReader: KotlinLibraryMetadataReader = DefaultKotlinLibraryMetadataReader
) : KotlinLibraryReader {

    // For the zipped libraries inPlace gives files from zip file system
    // whereas realFiles extracts them to /tmp.
    // For unzipped libraries inPlace and realFiles are the same
    // providing files in the library directory.
    private val inPlace = KotlinLibrary(libraryFile, target)
    private val realFiles = inPlace.realFiles

    override val manifestProperties: Properties by lazy {
        val properties = inPlace.manifestFile.loadProperties()
        if (target != null) substitute(properties, defaultTargetSubstitutions(target))
        properties
    }

    val abiVersion: String
        get() {
            val manifestAbiVersion = manifestProperties.getProperty("abi_version")
            if ("$currentAbiVersion" != manifestAbiVersion)
                error("ABI version mismatch. Compiler expects: $currentAbiVersion, the library is $manifestAbiVersion")
            return manifestAbiVersion
        }

    val targetList = inPlace.targetsDir.listFiles.map{it.name}
    override val dataFlowGraph by lazy { inPlace.dataFlowGraphFile.let { if (it.exists) it.readBytes() else null } }

    override val libraryName
        get() = inPlace.libraryName

    override val uniqueName
        get() = manifestProperties.propertyString("unique_name")!!

    override val bitcodePaths: List<String>
        get() = (realFiles.kotlinDir.listFilesOrEmpty + realFiles.nativeDir.listFilesOrEmpty).map { it.absolutePath }

    override val includedPaths: List<String>
        get() = (realFiles.includedDir.listFilesOrEmpty).map { it.absolutePath }

    override val linkerOpts: List<String>
        get() = manifestProperties.propertyList("linkerOpts", target!!.visibleName)

    override val unresolvedDependencies: List<String>
        get() = manifestProperties.propertyList("depends")

    val resolvedDependencies = mutableListOf<KotlinLibraryReaderImpl>()

    override val moduleHeaderData: ByteArray by lazy { metadataReader.loadSerializedModule(inPlace) }

    override var isNeededForLink: Boolean = false
        private set

    private val emptyPackages: List<String> by lazy { emptyPackages(moduleHeaderData) }

    override fun markPackageAccessed(fqName: String) {
        if (!isNeededForLink // fast path
                && !emptyPackages.contains(fqName)) {
            isNeededForLink = true
        }
    }

    override fun packageMetadata(fqName: String) = metadataReader.loadSerializedPackageFragment(inPlace, fqName)

    override fun moduleDescriptor(specifics: LanguageVersionSettings) = deserializeModule(specifics, this)

}

// FIXME: ddol: methods to implement:
fun emptyPackages(libraryDate: ByteArray): List<String> = TODO()
fun deserializeModule(specifics: LanguageVersionSettings, reader: KotlinLibraryReader): ModuleDescriptorImpl = TODO()

//internal fun <T: KotlinLibraryReader> List<T>.purgeUnneeded(config: KonanConfig): List<T> =
//        this.filter{ (!it.isDefaultLibrary && !config.purgeUserLibs) || it.isNeededForLink }



