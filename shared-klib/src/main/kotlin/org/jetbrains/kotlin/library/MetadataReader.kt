package org.jetbrains.kotlin.library

interface KotlinLibraryMetadataReader {
    fun loadSerializedModule(library: KotlinLibrary): ByteArray
    fun loadSerializedPackageFragment(library: KotlinLibrary, fqName: String): ByteArray
}
