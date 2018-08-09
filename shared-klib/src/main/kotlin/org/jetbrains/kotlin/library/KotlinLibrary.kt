package org.jetbrains.kotlin.library

import org.jetbrains.kotlin.konan.file.File
import org.jetbrains.kotlin.konan.target.KonanTarget

const val KOTLIN_LIBRARY_EXTENSION = "klib"
const val KOTLIN_LIBRARY_EXTENSION_WITH_DOT = ".$KOTLIN_LIBRARY_EXTENSION"

interface KotlinLibraryLayout {

    /**
     * Path to the library root. All inner file and directory paths in the library are evaluated based on [libDir].
     *
     * If the library is stored as an archive file, [libDir] contains the full path to the archive file.
     * In this case paths to inner files and directories include the path to the archive file plus the relative path to
     * the file or directory. Example: `jar:/home/user/mylib.klib!/manifest`. This is used to address the concrete
     * file or directory in a virtual archive [java.io.FileSystem] without extracting archive contents.
     */
    val libDir: File

    /* This is a default implementation. Can't make it an assignment. */
    val target: KonanTarget?
        get() = null

    val manifestFile
        get() = File(libDir, "manifest")
    val resourcesDir
        get() = File(libDir, "resources")
    val linkdataDir
        get() = File(libDir, "linkdata")
    val moduleHeaderFile
        get() = File(linkdataDir, "module")
    val dataFlowGraphFile
        get() = File(linkdataDir, "module_data_flow_graph")

    val targetsDir
        get() = File(libDir, "targets")
    val targetDir
        get() = File(targetsDir, target!!.visibleName)
    val kotlinDir
        get() = File(targetDir, "kotlin")
    val nativeDir
        get() = File(targetDir, "native")
    val includedDir
        get() = File(targetDir, "included")

    fun packageFile(packageName: String)
            = File(linkdataDir, if (packageName == "") "root_package.knm" else "package_$packageName.knm")
}

interface KotlinLibrary: KotlinLibraryLayout {

    /* The path to the library from external point of view. */
    val location: File

    val libraryName: String
}
