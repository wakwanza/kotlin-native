package org.jetbrains.kotlin.backend.konan.llvm.codegen

import kotlinx.cinterop.*
import llvm.*
import org.jetbrains.kotlin.backend.konan.Context
import org.jetbrains.kotlin.backend.konan.KonanPhase
import org.jetbrains.kotlin.backend.konan.PhaseManager
import org.jetbrains.kotlin.backend.konan.library.KonanLibraryReader
import org.jetbrains.kotlin.backend.konan.llvm.parseBitcodeFile
import org.jetbrains.kotlin.konan.target.CompilerOutputKind

internal fun lto(context: Context) {
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


    val llvmContext = LLVMGetModuleContext(context.llvmModule)

    phaser.phase(KonanPhase.NEXTGEN) {
        memScoped {
            val configuration = alloc<CompilationConfiguration>()
            val (outputKind, filename) = if (context.config.produce == CompilerOutputKind.BITCODE) {
                Pair(OutputKind.OUTPUT_KIND_BITCODE, context.config.outputFile)
            } else {
                Pair(OutputKind.OUTPUT_KIND_OBJECT_FILE, "result.o")
            }
            configuration.optLevel = if (context.shouldOptimize()) 3 else 1
            configuration.sizeLevel = 0
            configuration.outputKind = outputKind
            configuration.shouldProfile = if (context.shouldProfilePhases()) 1 else 0
            configuration.fileName = filename.cstr.ptr
            configuration.targetTriple = LLVMGetTarget(runtime.llvmModule)

            if (LLVMLtoCodegen(
                            llvmContext,
                            programModule,
                            runtime.llvmModule,
                            stdlibModule,
                            configuration.readValue()
                    ) != 0) {
                context.log { "Codegen failed" }
            }
        }
    }
}