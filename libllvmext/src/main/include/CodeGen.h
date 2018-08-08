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
#ifndef LIBLLVMEXT_CODEGEN_H
#define LIBLLVMEXT_CODEGEN_H

#include "utility.h"

#include <llvm/IR/Module.h>
#include <LTOExt.h>
#include <llvm/Support/TargetRegistry.h>
#include <llvm/Target/TargetMachine.h>
#include <llvm/MC/SubtargetFeature.h>


using namespace llvm;

int compileModule(Module& module, const CompilationConfiguration& config);

// Pretty much inspired by Clang's BackendUtil
class KotlinNativeLlvmBackend {
 public:
  explicit KotlinNativeLlvmBackend(const CompilationConfiguration& config) : config(config), triple(config.targetTriple) {
    optLevel = static_cast<unsigned int>(config.optLevel);
    sizeLevel = static_cast<unsigned int>(config.sizeLevel);
  }

 private:
  unsigned int optLevel;
  unsigned int sizeLevel;
  Optional<Reloc::Model> relocModel;
  const CompilationConfiguration& config;
  Triple triple;

  std::unique_ptr<TargetMachine> targetMachine;

 private:
  Optional<Reloc::Model> getRelocModel() {
    switch (config.relocMode) {
      case LLVMRelocDefault:      return None;
      case LLVMRelocStatic:       return Reloc::Model::Static;
      case LLVMRelocPIC:          return Reloc::Model::PIC_;
      case LLVMRelocDynamicNoPic: return Reloc::Model::DynamicNoPIC;
    }
  }

  bool createTargetMachine() {
    std::string error;
    const llvm::Target *target = TargetRegistry::lookupTarget(targetTriple, error);
    if (!target) {
      logging::error() << error;
      return true;
    }
    CodeModel::Model = CodeModel::Default; // TODO: add support for other code models
    std::string targetFeatures = getTargetFeatures();
    Reloc::Model relocModel = getRelocModel();
    CodeGenOpt::Level codegenOptLevel = getCodegenOptLevel();

    targetMachine.reset();
  }

  TargetMachine::CodeGenFileType getCodeGenFileType() {
    switch (config.outputKind) {
      case OUTPUT_KIND_OBJECT_FILE:
        return TargetMachine::CodeGenFileType::CGFT_ObjectFile;
      default:
        logging::error() << "Unsupported codegen file type!\n";
        return TargetMachine::CodeGenFileType ::CGFT_Null;
    }
  }

  CodeGenOpt::Level getCodegenOptLevel() {
    switch (config.optLevel) {
      case 0:return CodeGenOpt::Level::None;
      case 1:return CodeGenOpt::Level::Less;
      case 2:return CodeGenOpt::Level::Default;
      case 3:return CodeGenOpt::Level::Aggressive;
    }
  }

  std::string getTargetFeatures() {
    SubtargetFeatures features("");
    features.getDefaultSubtargetFeatures(triple);
    return features.getString();
  }
};

#endif //LIBLLVMEXT_CODEGEN_H
