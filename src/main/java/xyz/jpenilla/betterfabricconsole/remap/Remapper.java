/*
 * This file is part of Better Fabric Console, licensed under the MIT License.
 *
 * Copyright (c) 2021-2022 Jason Penilla
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

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.format.ProGuardReader;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.DefaultQualifier;
import xyz.jpenilla.betterfabricconsole.util.ThrowingConsumer;

@DefaultQualifier(NonNull.class)
public final class Remapper {
  private static final Logger LOGGER = LogManager.getLogger();

  private final Map<String, String> classes;
  private final Map<String, String> methods;

  private Remapper(
    final Map<String, String> classes,
    final Map<String, String> methods
  ) {
    this.classes = classes;
    this.methods = methods;
  }

  public static Remapper yarn(
    final Path yarnMappingsJar
  ) throws IOException {
    return fromMappingTree(Namespace.YARN, tree -> {
      try (
        final FileSystem intermediaryJarFs = FileSystems.newFileSystem(URI.create("jar:" + yarnMappingsJar.toUri()), new HashMap<>());
        final BufferedReader intermediaryReader = Files.newBufferedReader(intermediaryJarFs.getPath("mappings/mappings.tiny"))
      ) {
        MappingReader.read(intermediaryReader, tree);
      }
    });
  }

  public static Remapper mojangMappings(
    final Path mojangServerMappings,
    final Path mojangClientMappings,
    final Path intermediaryMappingsJar
  ) throws IOException {
    return fromMappingTree(Namespace.MOJANG, tree -> {
      try (
        final BufferedReader serverReader = Files.newBufferedReader(mojangServerMappings);
        final BufferedReader clientReader = Files.newBufferedReader(mojangClientMappings);
        final FileSystem intermediaryJarFs = FileSystems.newFileSystem(URI.create("jar:" + intermediaryMappingsJar.toUri()), new HashMap<>());
        final BufferedReader intermediaryReader = Files.newBufferedReader(intermediaryJarFs.getPath("mappings/mappings.tiny"))
      ) {
        ProGuardReader.read(serverReader, Namespace.MOJANG, Namespace.OFFICIAL, tree);
        ProGuardReader.read(clientReader, Namespace.MOJANG, Namespace.OFFICIAL, tree);
        MappingReader.read(intermediaryReader, tree);
      }
    });
  }

  private static Remapper fromMappingTree(
    final String toNamespace,
    final ThrowingConsumer<MemoryMappingTree, IOException> populator
  ) throws IOException {
    LOGGER.info("Reading mappings...");

    final MemoryMappingTree tree = new MemoryMappingTree();
    populator.accept(tree);

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
          methodMapBuilder.put(imName, mName);
        }
      }
    }

    final Remapper remapper = new Remapper(
      Map.copyOf(classMapBuilder),
      Map.copyOf(methodMapBuilder)
    );
    LOGGER.info("Done.");
    return remapper;
  }

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

  public String remapClassName(final String name) {
    return this.classes.getOrDefault(name, name);
  }

  private static String slashToDot(final String slahsed) {
    return slahsed.replace("/", ".");
  }
}
