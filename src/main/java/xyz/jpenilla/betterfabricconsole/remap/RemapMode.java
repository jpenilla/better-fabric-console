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

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.function.Function;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import xyz.jpenilla.betterfabricconsole.util.ThrowingFunction;

@NullMarked
public enum RemapMode {
  MOJANG(MappingsCache::createMojangMappingsDownloader, Remapper::mojangMappings),
  YARN(MappingsCache::createYarnMappingsDownloader, Remapper::yarn),
  NONE;

  private static final Logger LOGGER = LogUtils.getLogger();

  private final @Nullable Function<MappingsCache, MappingsDownloader<?>> downloaderFactory;
  private final @Nullable ThrowingFunction<Object, Remapper, IOException> remapperFactory;

  @SuppressWarnings({"rawtypes", "unchecked"})
  <O, D extends MappingsDownloader<O>> RemapMode(
    final Function<MappingsCache, D> downloaderFactory,
    final ThrowingFunction<O, Remapper, IOException> remapperFactory
  ) {
    this.downloaderFactory = (Function) downloaderFactory;
    this.remapperFactory = (ThrowingFunction) remapperFactory;
  }

  RemapMode() {
    this.downloaderFactory = null;
    this.remapperFactory = null;
  }

  public @Nullable Remapper createRemapper(final MappingsCache mappingsCache) {
    if (this.downloaderFactory == null || this.remapperFactory == null) {
      return null;
    }

    final MappingsDownloader<?> downloader = this.downloaderFactory.apply(mappingsCache);
    final Object mappingsData;
    try {
      mappingsData = downloader.downloadMappings();
    } catch (final IOException ex) {
      LOGGER.warn("Failed to download mappings. Will retry on next startup.", ex);
      mappingsCache.moveFailed();
      return null;
    }
    try {
      return this.remapperFactory.apply(mappingsData);
    } catch (final IOException ex) {
      LOGGER.warn("Failed to read mappings. Will retry on next startup.", ex);
      mappingsCache.moveFailed();
      return null;
    }
  }
}
