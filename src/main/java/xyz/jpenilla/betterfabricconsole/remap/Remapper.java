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

import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.HashMap;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.format.proguard.ProGuardFileReader;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.slf4j.Logger;

@DefaultQualifier(NonNull.class)
public interface Remapper {
  Logger LOGGER = LogUtils.getLogger();

  void remapThrowable(Throwable throwable);

  StackTraceElement[] remapStacktrace(StackTraceElement[] trace);

  String remapClassName(String name);

  static Remapper yarn(final YarnMappingsDownloader.YarnData data) throws IOException {
    return RemapperImpl.fromMappingTree(Namespace.YARN, tree -> {
      try (
        final FileSystem yarnJarFs = FileSystems.newFileSystem(URI.create("jar:" + data.mappingsJar().toUri()), new HashMap<>());
        final BufferedReader yarnReader = Files.newBufferedReader(yarnJarFs.getPath("mappings/mappings.tiny"))
      ) {
        MappingReader.read(yarnReader, tree);
      }
    });
  }

  static Remapper mojangMappings(final MojangMappingsDownloader.MojangMappingsData data) throws IOException {
    return RemapperImpl.fromMappingTree(Namespace.MOJANG, tree -> {
      try (
        final BufferedReader serverReader = Files.newBufferedReader(data.serverMappings());
        final BufferedReader clientReader = Files.newBufferedReader(data.clientMappings());
        final FileSystem intermediaryJarFs = FileSystems.newFileSystem(URI.create("jar:" + data.intermediaryMappingsJar().toUri()), new HashMap<>());
        final BufferedReader intermediaryReader = Files.newBufferedReader(intermediaryJarFs.getPath("mappings/mappings.tiny"))
      ) {
        ProGuardFileReader.read(serverReader, Namespace.MOJANG, Namespace.OFFICIAL, tree);
        ProGuardFileReader.read(clientReader, Namespace.MOJANG, Namespace.OFFICIAL, tree);
        MappingReader.read(intermediaryReader, tree);
      }
    });
  }
}
