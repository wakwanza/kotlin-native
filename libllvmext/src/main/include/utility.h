//
// Created by jetbrains on 07/08/2018.
//

#ifndef LIBLLVMEXT_UTILITY_H
#define LIBLLVMEXT_UTILITY_H

#include <llvm/Support/raw_ostream.h>

using namespace llvm;

namespace logging {
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

#endif //LIBLLVMEXT_UTILITY_H
