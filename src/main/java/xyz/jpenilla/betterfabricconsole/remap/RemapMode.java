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

import java.io.IOException;
import java.util.function.Function;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.DefaultQualifier;
import xyz.jpenilla.betterfabricconsole.BetterFabricConsole;
import xyz.jpenilla.betterfabricconsole.util.ThrowingFunction;

@DefaultQualifier(NonNull.class)
public enum RemapMode {
  MOJANG(
    MappingsDownloaderFactory::mojangMappings,
    downloader -> Remapper.mojangMappings(
      downloader.serverMappings(),
      downloader.clientMappings(),
      downloader.intermediaryMappingsJar()
    )
  ),
  YARN(
    MappingsDownloaderFactory::yarnMappings,
    downloader -> Remapper.yarn(downloader.yarnMappingsJar())
  ),
  NONE;

  private final @Nullable Function<MappingsDownloaderFactory, MappingsDownloader> downloaderFactory;
  private final @Nullable ThrowingFunction<MappingsDownloader, Remapper, IOException> remapperFactory;

  @SuppressWarnings({"rawtypes", "unchecked"})
  <D extends MappingsDownloader> RemapMode(
    final Function<MappingsDownloaderFactory, D> downloaderFactory,
    final ThrowingFunction<D, Remapper, IOException> remapperFactory
  ) {
    this.downloaderFactory = (Function) downloaderFactory;
    this.remapperFactory = (ThrowingFunction) remapperFactory;
  }

  RemapMode() {
    this.downloaderFactory = null;
    this.remapperFactory = null;
  }

  public @Nullable Remapper createRemapper(final MappingsDownloaderFactory factory) {
    if (this.downloaderFactory == null || this.remapperFactory == null) {
      return null;
    }

    final MappingsDownloader downloader = this.downloaderFactory.apply(factory);
    try {
      downloader.downloadMappings();
    } catch (final IOException ex) {
      BetterFabricConsole.LOGGER.warn("Failed to download mappings.", ex);
      return null;
    }
    try {
      return this.remapperFactory.apply(downloader);
    } catch (final IOException ex) {
      BetterFabricConsole.LOGGER.warn("Failed to read mappings.", ex);
      return null;
    }
  }
}
