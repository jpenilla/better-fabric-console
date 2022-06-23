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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.stream.StreamSupport;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.slf4j.Logger;

@DefaultQualifier(NonNull.class)
final class YarnMappingsDownloader implements MappingsDownloader<YarnMappingsDownloader.YarnData> {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final String YARN_VERSIONS_PATH = MappingsCache.DATA_PATH + "/yarn-versions.json";
  private static final String YARN_MAPPINGS_PATH = MappingsCache.MAPPINGS_PATH + "/yarn/" + MappingsCache.MINECRAFT_VERSION + ".jar";
  private static final String YARN_MAPPINGS_VERSION_PATH = MappingsCache.MAPPINGS_PATH + "/yarn/" + MappingsCache.MINECRAFT_VERSION + "-current-yarn.txt";
  private static final String YARN_URL = "https://maven.fabricmc.net/net/fabricmc/yarn/{}/yarn-{}-mergedv2.jar";
  private static final String YARN_VERSIONS_URL = "https://maven.fabricmc.net/net/fabricmc/yarn/versions.json";

  private final Path cache;

  YarnMappingsDownloader(final MappingsCache cache) {
    this.cache = cache.cache;
  }

  @Override
  public YarnData downloadMappings() throws IOException {
    final Path yarnVersionsPath = this.cache.resolve(YARN_VERSIONS_PATH);

    final boolean downloadedJson = downloadYarnVersionsJson(yarnVersionsPath, false);
    @Nullable Integer latestBuild = findLatestYarnBuildForMcVersion(yarnVersionsPath);

    if (!downloadedJson && latestBuild == null) {
      downloadYarnVersionsJson(yarnVersionsPath, true);
      latestBuild = findLatestYarnBuildForMcVersion(yarnVersionsPath);
    }
    if (latestBuild == null) {
      throw new IllegalStateException("Could not find yarn mappings for version " + MappingsCache.MINECRAFT_VERSION);
    }

    final String yarnVersion = MappingsCache.MINECRAFT_VERSION + "+build." + latestBuild;

    final Path versionFile = this.cache.resolve(YARN_MAPPINGS_VERSION_PATH);
    if (!Files.isRegularFile(versionFile) || !Files.readString(versionFile).equals(yarnVersion)) {
      this.downloadYarn(downloadedJson, yarnVersion);
    }
    return new YarnData(this.yarnMappingsJar());
  }

  private static @Nullable Integer findLatestYarnBuildForMcVersion(final Path yarnVersionsPath) throws IOException {
    final JsonObject yarnVersions;
    try (final BufferedReader reader = Files.newBufferedReader(yarnVersionsPath)) {
      yarnVersions = MappingsCache.GSON.fromJson(reader, JsonObject.class);
    }
    final JsonElement element = yarnVersions.get(MappingsCache.MINECRAFT_VERSION);
    if (element == null) {
      return null;
    }
    return StreamSupport.stream(element.getAsJsonArray().spliterator(), false)
      .map(JsonElement::getAsInt)
      .max(Comparator.naturalOrder())
      .orElse(null);
  }

  private static boolean downloadYarnVersionsJson(final Path yarnVersionsPath, final boolean forceDownload) throws IOException {
    if (forceDownload || !Files.exists(yarnVersionsPath)) {
      MappingsCache.downloadFile(YARN_VERSIONS_URL, yarnVersionsPath);
      return true;
    } else {
      final long lastModified = Files.getLastModifiedTime(yarnVersionsPath).toMillis();
      final Duration sinceModified = Duration.ofMillis(System.currentTimeMillis() - lastModified);
      if (sinceModified.compareTo(Duration.ofDays(7)) > 0) {
        try {
          MappingsCache.downloadFile(YARN_VERSIONS_URL, yarnVersionsPath);
        } catch (final IOException ex) {
          LOGGER.warn("Failed to update yarn version index", ex);
          return false;
        }
        return true;
      }
    }
    return false;
  }

  private void downloadYarn(final boolean forceDownload, final String yarnVersion) throws IOException {
    final Path yarnMappings = this.yarnMappingsJar();
    if (forceDownload || !Files.exists(yarnMappings)) {
      MappingsCache.downloadFile(YARN_URL.replace("{}", yarnVersion), yarnMappings);
      final Path version = this.cache.resolve(YARN_MAPPINGS_VERSION_PATH);
      Files.writeString(version, yarnVersion);
    }
  }

  private Path yarnMappingsJar() {
    return this.cache.resolve(YARN_MAPPINGS_PATH);
  }

  public record YarnData(Path mappingsJar) {
  }
}
