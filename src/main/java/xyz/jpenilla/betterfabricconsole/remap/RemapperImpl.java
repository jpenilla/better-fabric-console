/*
 * This file is part of Better Fabric Console, licensed under the MIT License.
 *
 * Copyright (c) 2021-2024 Jason Penilla
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package xyz.jpenilla.betterfabricconsole.remap;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.DefaultQualifier;
import xyz.jpenilla.betterfabricconsole.util.StringPool;
import xyz.jpenilla.betterfabricconsole.util.ThrowingConsumer;

@DefaultQualifier(NonNull.class)
record RemapperImpl(
  Map<String, String> classes,
  Map<String, String> methods
) implements Remapper {
  @Override
  public void remapThrowable(final Throwable throwable) {
    throwable.setStackTrace(this.remapStacktrace(throwable.getStackTrace()));
    final Throwable cause = throwable.getCause();
    if (cause != null) {
      this.remapThrowable(cause);
    }
    for (final Throwable suppressed : throwable.getSuppressed()) {
      this.remapThrowable(suppressed);
    }
  }

  @Override
  public StackTraceElement[] remapStacktrace(final StackTraceElement[] trace) {
    final StackTraceElement[] newTrace = new StackTraceElement[trace.length];

    for (int i = 0; i < trace.length; i++) {
      final StackTraceElement old = trace[i];

      final @Nullable String sourceFile = old.getFileName();
      final @Nullable String newSourceFile;
      if (sourceFile == null) {
        newSourceFile = null;
      } else {
        final @Nullable String mapped = this.classes.get("net.minecraft." + sourceFile.replace(".java", ""));
        if (mapped == null) {
          newSourceFile = sourceFile;
        } else {
          newSourceFile = mapped.substring(mapped.lastIndexOf('.') + 1) + ".java";
        }
      }

      newTrace[i] = new StackTraceElement(
        old.getClassLoaderName(),
        old.getModuleName(),
        old.getModuleVersion(),
        this.classes.getOrDefault(old.getClassName(), old.getClassName()),
        this.methods.getOrDefault(old.getMethodName(), old.getMethodName()),
        newSourceFile,
        old.getLineNumber()
      );
    }

    return newTrace;
  }

  @Override
  public String remapClassName(final String name) {
    return this.classes.getOrDefault(name, name);
  }

  static Remapper fromMappingTree(
    final String toNamespace,
    final ThrowingConsumer<MemoryMappingTree, IOException> populator
  ) throws IOException {
    LOGGER.info("Reading mappings...");
    final long start = System.currentTimeMillis();

    final MemoryMappingTree tree = new MemoryMappingTree();
    populator.accept(tree);

    final StringPool stringPool = new StringPool(new HashMap<>());
    final Map<String, String> classMapBuilder = new HashMap<>();
    final Map<String, String> methodMapBuilder = new HashMap<>();
    for (final MappingTree.ClassMapping clazz : tree.getClasses()) {
      final @Nullable String icName = clazz.getName(Namespace.INTERMEDIARY);
      final @Nullable String cName = clazz.getName(toNamespace);
      if (icName != null && cName != null) {
        classMapBuilder.put(slashToDot(icName), slashToDot(cName));
      }
      for (final MappingTree.MethodMapping method : clazz.getMethods()) {
        final @Nullable String imName = method.getName(Namespace.INTERMEDIARY);
        final @Nullable String mName = method.getName(toNamespace);
        if (imName != null && mName != null) {
          methodMapBuilder.put(imName, stringPool.string(mName));
        }
      }
    }

    final RemapperImpl remapper = new RemapperImpl(
      Map.copyOf(classMapBuilder),
      Map.copyOf(methodMapBuilder)
    );
    LOGGER.info("Done in {} seconds.", MappingsCache.DECIMAL_FORMAT.format((System.currentTimeMillis() - start) / 1000.00D));
    return remapper;
  }

  private static String slashToDot(final String slahsed) {
    return slahsed.replace("/", ".");
  }
}
