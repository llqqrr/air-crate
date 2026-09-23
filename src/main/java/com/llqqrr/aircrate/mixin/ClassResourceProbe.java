package com.llqqrr.aircrate.mixin;

/** Classpath probe that deliberately never loads or initializes the target. */
final class ClassResourceProbe {
    private ClassResourceProbe() {
    }

    static boolean exists(ClassLoader loader, String className) {
        return loader.getResource(className.replace('.', '/') + ".class") != null;
    }
}
