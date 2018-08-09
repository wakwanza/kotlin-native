package org.jetbrains.kotlin.library.impl

import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.library.KotlinLibraryMetadataReader

object DefaultKotlinLibraryMetadataReader : KotlinLibraryMetadataReader {

    override fun loadSerializedModule(library: KotlinLibrary): ByteArray = library.moduleHeaderFile.readBytes()

    override fun loadSerializedPackageFragment(library: KotlinLibrary, fqName: String): ByteArray =
            library.packageFile(fqName).readBytes()
}
