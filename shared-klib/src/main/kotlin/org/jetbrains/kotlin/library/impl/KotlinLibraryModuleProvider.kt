package org.jetbrains.kotlin.library.impl

import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.library.KotlinLibrary

interface KotlinLibraryModuleProvider {

    fun getModule(library: KotlinLibrary): ModuleDescriptor
}

