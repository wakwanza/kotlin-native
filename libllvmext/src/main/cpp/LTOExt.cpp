/*
 * Copyright 2010-2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "LTOExt.h"

#include <llvm/Support/FileSystem.h>
#include <llvm/Support/TargetSelect.h>
#include <llvm/InitializePasses.h>
#include <llvm/IR/Module.h>
#include <llvm/Transforms/IPO/PassManagerBuilder.h>
#include <llvm/Target/TargetMachine.h>
#include <llvm/LTO/Config.h>
#include <llvm/Analysis/TargetLibraryInfo.h>
#include <llvm/Transforms/IPO.h>
#include <llvm/Transforms/IPO/AlwaysInliner.h>
#include <llvm/Support/TargetRegistry.h>
#include <llvm/Linker/Linker.h>
#include <llvm/IR/Verifier.h>
#include <llvm/IR/LegacyPassManager.h>
#include <llvm/IR/DiagnosticPrinter.h>
#include <llvm/CodeGen/Passes.h>
#include <llvm/Support/ToolOutputFile.h>
#include <llvm/Support/raw_ostream.h>
#include <llvm/Analysis/TargetTransformInfo.h>
#include <llvm/IR/IRPrintingPasses.h>
#include <llvm/MC/SubtargetFeature.h>
#include <llvm/Support/Host.h>

#include <memory>
#include <ctime>

using namespace llvm;

// TODO: wrap with ifdef
constexpr bool debug = true;

// Different logging streams.
namespace log {
raw_ostream &info() {
  return outs();
}

raw_ostream &debug() {
  if (debug) {
    return outs();
  } else {
    return nulls();
  }
}

raw_ostream &error() {
  return errs();
}
}

class ModuleLinker {
 public:
  explicit ModuleLinker(LLVMContext &context)
      : mergedModule(new Module("merged", context)),
        linker(*mergedModule) {}

  bool linkModule(std::unique_ptr<Module> module, bool onlyNeeded, bool shouldProfile) {
    unsigned flags = Linker::Flags::None;
    if (onlyNeeded) {
      flags |= Linker::Flags::LinkOnlyNeeded;
    }

    clock_t linkTime = clock();
    std::string name = module->getName();
    if (linker.linkInModule(std::move(module), flags)) {
      return true;
    }
    linkTime = clock() - linkTime;
    if (shouldProfile) {
      float seconds = ((float)linkTime)/CLOCKS_PER_SEC;
      log::info() << "Linking of " << name << " took " << seconds << " seconds\n";
    }
    return verifyModule(*mergedModule, &log::error());
  }

  std::unique_ptr<Module> mergedModule;

 private:
  Linker linker;
};

static std::unique_ptr<Module> linkModules(LLVMContext &context,
                                           LLVMModuleRef programModuleRef,
                                           LLVMModuleRef runtimeModuleRef,
                                           LLVMModuleRef stdlibModuleRef,
                                           bool shouldProfile) {

  std::unique_ptr<Module> programModule(unwrap(programModuleRef));
  std::unique_ptr<Module> runtimeModule(unwrap(runtimeModuleRef));
  std::unique_ptr<Module> stdlibModule(unwrap(stdlibModuleRef));

  ModuleLinker linker(context);
  if (linker.linkModule(std::move(programModule), false, shouldProfile)) {
    log::error() << "Cannot link program.\n";
    return nullptr;
  }
  if (linker.linkModule(std::move(runtimeModule), false, shouldProfile)) {
    log::error() << "Cannot link program with runtime.\n";
    return nullptr;
  }
  if (linker.linkModule(std::move(stdlibModule), true, shouldProfile)) {
    log::error() << "Cannot link standard library.\n";
    return nullptr;
  }
  return std::move(linker.mergedModule);
}

namespace {
// TODO: populate
TargetOptions createTargetOptions() {
  TargetOptions options;
  return options;
}

// TODO: add blows and whistles
std::unique_ptr<TargetMachine> createTargetMachine(const std::string &tripleString, int optLevel) {
  std::string errorMsg;

  Triple triple(tripleString);

  const Target *target = TargetRegistry::lookupTarget(tripleString, errorMsg);
  if (!target) {
    log::error() << errorMsg;
    return nullptr;
  }

  // TODO: Add support for cross-compilation
  std::string cpu = sys::getHostCPUName();
  if (cpu.empty() && triple.isOSDarwin()) {
    if (triple.getArch() == llvm::Triple::x86_64)
      cpu = "core2";
    else if (triple.getArch() == llvm::Triple::x86)
      cpu = "yonah";
    else if (triple.getArch() == llvm::Triple::aarch64)
      cpu = "cyclone";
  }

  SubtargetFeatures features("");
  features.getDefaultSubtargetFeatures(triple);
  StringMap<bool> HostFeatures;
  if (sys::getHostCPUFeatures(HostFeatures))
    for (auto &F : HostFeatures)
      features.AddFeature(F.first(), F.second);
  const TargetOptions &options = createTargetOptions();
  Reloc::Model relocModel = Reloc::Model::Static;

  CodeGenOpt::Level codeGenOptLevel;
  switch (optLevel) {
    case 0: codeGenOptLevel = CodeGenOpt::None;
      break;
    case 1: codeGenOptLevel = CodeGenOpt::Less;
      break;
    case 2: codeGenOptLevel = CodeGenOpt::Default;
      break;
    case 3: codeGenOptLevel = CodeGenOpt::Aggressive;
      break;
    default:log::error() << "Unsupported opt level: " << optLevel << "\n";
      return nullptr;
  }
  return std::unique_ptr<TargetMachine>(
      target->createTargetMachine(tripleString,
                                  cpu,
                                  features.getString(),
                                  options,
                                  relocModel,
                                  CodeModel::Default,
                                  codeGenOptLevel));
}


void setFunctionAttributes(StringRef cpu, StringRef features, Module &module) {
  for (auto &fn : module) {
    auto &context = fn.getContext();
    AttributeList Attrs = fn.getAttributes();
    AttrBuilder newAttrs;

    if (!cpu.empty())
      newAttrs.addAttribute("target-cpu", cpu);
    if (!features.empty())
      newAttrs.addAttribute("target-features", features);

    fn.setAttributes(Attrs.addAttributes(context, AttributeList::FunctionIndex, newAttrs));
  }
}

void populatePassManager(legacy::PassManager &pm,
                         legacy::FunctionPassManager &fpm,
                         Module &module,
                         TargetMachine &targetMachine,
                         unsigned int optLevel,
                         unsigned int sizeLevel) {
  TargetLibraryInfoImpl tlii(Triple(module.getTargetTriple()));
  fpm.add(createTargetTransformInfoWrapperPass(targetMachine.getTargetIRAnalysis()));
  pm.add(new TargetLibraryInfoWrapperPass(tlii));
  pm.add(createInternalizePass());
  PassManagerBuilder Builder;
  Builder.OptLevel = optLevel;
  Builder.SizeLevel = sizeLevel;
  if (optLevel > 1) {
    Builder.Inliner = createFunctionInliningPass();
  } else {
    Builder.Inliner = createAlwaysInlinerLegacyPass();
  }
  Builder.DisableUnrollLoops = optLevel == 0;
  Builder.LoopVectorize = optLevel > 1 && sizeLevel < 2;
  Builder.SLPVectorize = optLevel > 1 && sizeLevel < 2;
  Builder.LibraryInfo = new TargetLibraryInfoImpl(targetMachine.getTargetTriple());
  targetMachine.adjustPassManager(Builder);

  Builder.populateFunctionPassManager(fpm);
  Builder.populateModulePassManager(pm);
  Builder.populateLTOPassManager(pm);
}

}

// Mostly copy'n'paste from opt and llc for now.
static void initLLVM(PassRegistry *registry) {
  InitializeAllTargets();
  InitializeAllTargetMCs();
  InitializeAllAsmPrinters();
  InitializeAllAsmParsers();

  initializeCore(*registry);

  initializeScalarOpts(*registry);
  initializeObjCARCOpts(*registry);
  initializeVectorization(*registry);
  initializeIPO(*registry);
  initializeAnalysis(*registry);
  initializeTransformUtils(*registry);
  initializeInstCombine(*registry);
  initializeInstrumentation(*registry);
  initializeTarget(*registry);

  initializeCodeGenPreparePass(*registry);
  initializeCodeGen(*registry);
  initializeLoopStrengthReducePass(*registry);
  initializeLowerIntrinsicsPass(*registry);

  initializeScalarizeMaskedMemIntrinPass(*registry);
  initializeAtomicExpandPass(*registry);
  initializeRewriteSymbolsLegacyPassPass(*registry);
  initializeWinEHPreparePass(*registry);
  initializeDwarfEHPreparePass(*registry);
  initializeSafeStackLegacyPassPass(*registry);
  initializeSjLjEHPreparePass(*registry);
  initializePreISelIntrinsicLoweringLegacyPassPass(*registry);
  initializeGlobalMergePass(*registry);
  initializeInterleavedAccessPass(*registry);
  initializeCountingFunctionInserterPass(*registry);
  initializeUnreachableBlockElimLegacyPassPass(*registry);
  initializeExpandReductionsPass(*registry);

  initializeConstantHoistingLegacyPassPass(*registry);
  initializeExpandReductionsPass(*registry);

  // Initialize debugging passes.
  initializeScavengerTestPass(*registry);
}

static void DiagnosticHandler(const DiagnosticInfo &DI, void *Context) {
  bool *HasError = static_cast<bool *>(Context);
  if (DI.getSeverity() == DS_Error)
    *HasError = true;

  if (auto *Remark = dyn_cast<DiagnosticInfoOptimizationBase>(&DI))
    if (!Remark->isEnabled())
      return;

  DiagnosticPrinterRawOStream DP(log::error());
  log::error() << LLVMContext::getDiagnosticMessagePrefix(DI.getSeverity()) << ": ";
  DI.print(DP);
  log::error() << "\n";
}

class CodeGenerator {
 public:
  CodeGenerator(Module &module, TargetMachine &targetMachine, tool_output_file &out)
      : module(module), targetMachine(targetMachine), out(out) {}

  void run(unsigned int optLevel, unsigned int sizeLevel, int outputKind) {
    setFunctionAttributes(targetMachine.getTargetCPU(), targetMachine.getTargetFeatureString(), module);

    // Use legacy pass manager for now.
    legacy::PassManager pm;
    auto manager = new legacy::FunctionPassManager(&module);
    std::unique_ptr<legacy::FunctionPassManager> functionPasses(manager);
    populatePassManager(pm, *functionPasses, module, targetMachine, optLevel, sizeLevel);

//    functionPasses->doInitialization();
//    for (Function &F : module)
//      functionPasses->run(F);
//    functionPasses->doFinalization();

    switch (outputKind) {
      case OUTPUT_KIND_BITCODE:
        pm.add(createPrintModulePass(out.os(), "", true));
        break;
      case OUTPUT_KIND_OBJECT_FILE:
        targetMachine.addPassesToEmitFile(pm, out.os(), TargetMachine::CGFT_ObjectFile);
        break;
      default:
        log::error() << "Unknown outputKind: " << outputKind << "\n";
    }
    pm.run(module);
  }
 private:
  Module& module;
  TargetMachine& targetMachine;
  tool_output_file& out;
};

extern "C" {
// TODO: Pick better name.
// TODO: Pass libraries as array.
// TODO: Pass outputKind as enum
int LLVMLtoCodegen(LLVMContextRef contextRef,
                   LLVMModuleRef programModuleRef,
                   LLVMModuleRef runtimeModuleRef,
                   LLVMModuleRef stdlibModuleRef,
                   int outputKind,
                   const char *filename,
                   int optLevel,
                   int sizeLevel,
                   int shouldProfile) {
  TimePassesIsEnabled = static_cast<bool>(shouldProfile);
  std::error_code EC;
  sys::fs::OpenFlags OpenFlags = sys::fs::F_None;
  auto p = new tool_output_file(filename, EC, OpenFlags);
  std::unique_ptr<tool_output_file> output(p);
  if (EC) {
    log::error() << EC.message();
    return 1;
  }

  std::unique_ptr<LLVMContext> context(unwrap(contextRef));

  bool HasError = false;
  context->setDiagnosticHandler(DiagnosticHandler, &HasError);

  PassRegistry *passRegistry = PassRegistry::getPassRegistry();
  initLLVM(passRegistry);

  // Should copy target triple because runtimeModule will be disposed by linker.
  std::string targetTriple = LLVMGetTarget(runtimeModuleRef);
  auto module = linkModules(*context, programModuleRef, runtimeModuleRef, stdlibModuleRef,
                            static_cast<bool>(shouldProfile));
  if (module == nullptr) {
    log::error() << "Module linkage failed.\n";
    return 1;
  }
  // Now program module contains everything that we need to produce object file.
  auto targetMachine = createTargetMachine(targetTriple, optLevel);
  if (targetMachine == nullptr) {
    log::error() << "Cannot create target machine.\n";
    return 1;
  }
  module->setDataLayout(targetMachine->createDataLayout());

  CodeGenerator(*module, *targetMachine, *output).run(
      static_cast<unsigned int>(optLevel),
      static_cast<unsigned int>(sizeLevel),
      outputKind
  );

  if (*static_cast<bool *>(context->getDiagnosticContext())) {
    log::error() << "LLVM Pass Manager failed.\n";
    return 1;
  }
  output->keep();
  reportAndResetTimings();
  log::debug() << "Bitcode compilation is complete.\n";
  return 0;
}

}