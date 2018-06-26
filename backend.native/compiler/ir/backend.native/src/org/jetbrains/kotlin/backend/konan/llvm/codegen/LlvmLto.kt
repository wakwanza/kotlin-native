package org.jetbrains.kotlin.backend.konan.llvm.codegen

import llvm.*
import org.jetbrains.kotlin.backend.konan.Context
import org.jetbrains.kotlin.backend.konan.KonanPhase
import org.jetbrains.kotlin.backend.konan.PhaseManager
import org.jetbrains.kotlin.backend.konan.library.KonanLibraryReader
import org.jetbrains.kotlin.backend.konan.llvm.parseBitcodeFile
import org.jetbrains.kotlin.konan.target.CompilerOutputKind

internal fun lto(context: Context): String {
    val phaser = PhaseManager(context)
    val libraries = context.llvm.librariesToLink
    val programModule = context.llvmModule!!
    val runtime = context.llvm.runtime

    fun stdlibPredicate(libraryReader: KonanLibraryReader) = libraryReader.uniqueName == "stdlib"
    val stdlibModule = parseBitcodeFile(libraries.first(::stdlibPredicate).bitcodePaths.first { it.endsWith("program.kt.bc") })

    val nativeLibraries =
            context.config.nativeLibraries +
                    context.config.defaultNativeLibraries
    PhaseManager(context).phase(KonanPhase.BITCODE_LINKER) {
        for (library in nativeLibraries) {
            val libraryModule = parseBitcodeFile(library)
            val failed = LLVMLinkModules2(programModule, libraryModule)
            if (failed != 0) {
                throw Error("failed to link $library") // TODO: retrieve error message from LLVM.
            }
        }
    }

    val optLevel = if (context.shouldOptimize()) 3 else 1
    val sizeLevel = 0
    val llvmContext = LLVMGetModuleContext(context.llvmModule)

    val (outputKind, filename) = if (context.config.produce == CompilerOutputKind.BITCODE) {
        Pair(OUTPUT_KIND_BITCODE, "result.ll")
    } else {
        Pair(OUTPUT_KIND_OBJECT_FILE, "result.o")
    }

    phaser.phase(KonanPhase.NEXTGEN) {
        if (LLVMLtoCodegen(llvmContext, programModule, runtime.llvmModule, stdlibModule, outputKind, filename, optLevel, sizeLevel) != 0) {
            context.log { "Codegen failed" }
        }
    }
    return filename
}