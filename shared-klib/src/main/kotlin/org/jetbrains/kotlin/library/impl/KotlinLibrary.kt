package org.jetbrains.kotlin.library.impl

import org.jetbrains.kotlin.konan.file.File
import org.jetbrains.kotlin.konan.file.asZipRoot
import org.jetbrains.kotlin.konan.file.createTempDir
import org.jetbrains.kotlin.konan.file.createTempFile
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.util.removeSuffixIfPresent
import org.jetbrains.kotlin.library.KOTLIN_LIBRARY_EXTENSION
import org.jetbrains.kotlin.library.KOTLIN_LIBRARY_EXTENSION_WITH_DOT
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.library.KotlinLibraryLayout

class UnzippedKotlinLibrary(
        override val location: File,
        override val target: KonanTarget? = null
): KotlinLibrary {

    override val libDir = location
    override val libraryName = location.path

    val targetList by lazy { targetsDir.listFiles.map{ it.name } }
}

class ZippedKotlinLibrary(
        override val location: File,
        override val target: KonanTarget? = null
): KotlinLibrary {

    init {
        check(location.exists) { "Could not find $location." }
        check(location.isFile) { "$location is expected to be a regular file." }

        val extension = location.extension
        check(extension == KOTLIN_LIBRARY_EXTENSION || extension == "") { "Unexpected library extension: $extension." }
    }

    override val libDir by lazy { location.asZipRoot }
    override val libraryName = libraryName(location)

    fun unpackTo(newDir: File) {
        if (newDir.exists) {
            if (newDir.isDirectory)
                newDir.deleteRecursively()
            else
                newDir.delete()
        }
        libDir.recursiveCopyTo(newDir)
        check(newDir.exists) { "Could not unpack $location as $newDir." }
    }

    companion object {
        fun libraryName(location: File) = location.path.removeSuffixIfPresent(KOTLIN_LIBRARY_EXTENSION_WITH_DOT)
    }
}

// This class automatically extracts pieces of
// the library on first access. Use it if you need
// to pass extracted files to an external tool.
// Otherwise, stick to ZippedKonanLibrary.
private class FileExtractor(zippedLibrary: ZippedKotlinLibrary): KotlinLibraryLayout by zippedLibrary {

    override val manifestFile: File by lazy { extractFile(super.manifestFile) }
    override val resourcesDir: File by lazy { extractDir(super.resourcesDir) }
    override val linkdataDir: File by lazy { extractDir(super.linkdataDir) }
    override val kotlinDir: File by lazy { extractDir(super.kotlinDir) }
    override val nativeDir: File by lazy { extractDir(super.nativeDir) }
    override val includedDir: File by lazy { extractDir(super.includedDir) }

    fun extractFile(file: File): File {
        val temporary = createTempFile(file.name)
        file.copyTo(temporary)
        temporary.deleteOnExit()
        return temporary
    }

    fun extractDir(directory: File): File {
        val temporary = createTempDir(directory.name)
        directory.recursiveCopyTo(temporary)
        temporary.deleteOnExitRecursively()
        return temporary
    }
}

fun KotlinLibrary(location: File, target: KonanTarget? = null) =
        if (location.isFile) ZippedKotlinLibrary(location, target) else UnzippedKotlinLibrary(location, target)

val KotlinLibrary.realFiles
    get() = when (this) {
        is ZippedKotlinLibrary -> FileExtractor(this)
        // Unpacked library just provides its own files.
        is UnzippedKotlinLibrary -> this
        else -> kotlin.error("Provide an extractor for your container.")
    }

