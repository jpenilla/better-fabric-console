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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import net.minecraft.SharedConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;

@DefaultQualifier(NonNull.class)
public final class MappingsCache {
  private static final Logger LOGGER = LogManager.getLogger();
  static final String MINECRAFT_VERSION = SharedConstants.getCurrentVersion().getId();
  static final String DATA_PATH = "data";
  static final String MAPPINGS_PATH = "mappings";
  static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.0000");
  static final Gson GSON = new GsonBuilder().create();

  final Path cache;

  public MappingsCache(final Path cache) {
    this.cache = cache;
  }

  public MappingsDownloader<MojangMappingsDownloader.MojangMappingsData> createMojangMappingsDownloader() {
    return new MojangMappingsDownloader(this);
  }

  public MappingsDownloader<YarnMappingsDownloader.YarnData> createYarnMappingsDownloader() {
    return new YarnMappingsDownloader(this);
  }

  static void downloadFile(final String url, final Path dest) throws IOException {
    Files.createDirectories(dest.getParent());

    final Path tempDest = dest.resolveSibling(dest.getFileName().toString() + ".download.tmp");
    Files.deleteIfExists(tempDest);

    LOGGER.info("Downloading " + url + "...");
    final long start = System.currentTimeMillis();
    try (
      final ReadableByteChannel downloadChannel = Channels.newChannel(new URL(url).openStream());
      final FileOutputStream outputStream = new FileOutputStream(tempDest.toFile())
    ) {
      outputStream.getChannel().transferFrom(downloadChannel, 0, Long.MAX_VALUE);
    } catch (final IOException ex) {
      Files.deleteIfExists(tempDest);
      throw ex;
    }
    Files.move(tempDest, dest, StandardCopyOption.REPLACE_EXISTING);
    LOGGER.info("Done in {} seconds.", DECIMAL_FORMAT.format((System.currentTimeMillis() - start) / 1000.00D));
  }
}
