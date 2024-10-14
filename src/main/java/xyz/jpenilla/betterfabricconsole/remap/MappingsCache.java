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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.util.zip.GZIPOutputStream;
import net.minecraft.DetectedVersion;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.slf4j.Logger;
import xyz.jpenilla.betterfabricconsole.util.Util;

// TODO: cleanup unused mappings (mappings config switch/mc version upgrade)
// TODO: keep less intermediary results
@DefaultQualifier(NonNull.class)
public final class MappingsCache {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final String VERSION = String.valueOf(2);
  private static final String VERSION_PATH = "version.txt";
  static final String MINECRAFT_VERSION = DetectedVersion.tryDetectVersion().getId();
  static final String DATA_PATH = "data";
  static final String MAPPINGS_PATH = "mappings";
  static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.0000");

  final Path cache;
  private final Path failed;

  public MappingsCache(final Path cache) throws IOException {
    this.cache = cache;
    this.failed = cache.resolveSibling(cache.getFileName() + "_failed");
    // If this fails, don't try and moveFailed.
    Util.deleteDirectoryIfExists(this.failed);
    try {
      this.checkCacheVersion();
    } catch (final IOException e) {
      this.moveFailed();
      throw e;
    }
  }

  private void checkCacheVersion() throws IOException {
    boolean clean = false;
    if (!Files.exists(this.cache.resolve(VERSION_PATH))) {
      clean = true;
    } else {
      final String versionString = Files.readString(this.cache.resolve(VERSION_PATH));
      if (!versionString.equals(VERSION)) {
        clean = true;
      }
    }
    if (clean) {
      Util.deleteDirectoryIfExists(this.cache);
      Files.createDirectories(this.cache);
      Files.writeString(this.cache.resolve(VERSION_PATH), VERSION);
    }
  }

  /**
   * Attempt to move the failed cache to a separate location to allow inspection. Will be deleted on next startup.
   */
  void moveFailed() {
    if (!Files.exists(this.cache)) {
      return;
    }
    try {
      Files.move(this.cache, this.failed);
    } catch (final IOException e) {
      LOGGER.warn("Failed to move failed mappings cache", e);
      try {
        Util.deleteDirectoryIfExists(this.cache);
      } catch (final IOException e1) {
        LOGGER.warn("Failed to delete failed mappings cache", e);
      }
    }
  }

  public MappingsDownloader<MojangMappingsDownloader.MojangMappingsData> createMojangMappingsDownloader() {
    return new MojangMappingsDownloader(this);
  }

  public MappingsDownloader<YarnMappingsDownloader.YarnData> createYarnMappingsDownloader() {
    return new YarnMappingsDownloader(this);
  }

  static void downloadFile(final String url, final Path dest) throws IOException {
    downloadFile(url, dest, false);
  }

  static void downloadFileAndGzip(final String url, final Path dest) throws IOException {
    downloadFile(url, dest, true);
  }

  private static void downloadFile(final String url, final Path dest, final boolean gzip) throws IOException {
    Files.createDirectories(dest.getParent());

    final Path tempDest = dest.resolveSibling(dest.getFileName().toString() + ".download.tmp");
    Files.deleteIfExists(tempDest);

    LOGGER.info("Downloading {}...", url);
    final long start = System.currentTimeMillis();
    try (
      final ReadableByteChannel downloadChannel = Channels.newChannel(URI.create(url).toURL().openStream());
      final FileOutputStream outputStream = new FileOutputStream(tempDest.toFile())
    ) {
      outputStream.getChannel().transferFrom(downloadChannel, 0, Long.MAX_VALUE);
    }
    if (gzip) {
      try (final OutputStream output = new GZIPOutputStream(Files.newOutputStream(dest))) {
        Files.copy(tempDest, output);
        Files.deleteIfExists(tempDest);
      }
    } else {
      Files.move(tempDest, dest, StandardCopyOption.REPLACE_EXISTING);
    }
    LOGGER.info("Done in {} seconds.", DECIMAL_FORMAT.format((System.currentTimeMillis() - start) / 1000.00D));
  }
}
