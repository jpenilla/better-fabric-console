/*
 * This file is part of Better Fabric Console, licensed under the MIT License.
 *
 * Copyright (c) 2021 Jason Penilla
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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.Comparator;
import java.util.stream.StreamSupport;
import net.minecraft.SharedConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.DefaultQualifier;

@DefaultQualifier(NonNull.class)
public final class MappingsDownloaderFactory {
  private static final Logger LOGGER = LogManager.getLogger();
  private static final String MINECRAFT_VERSION = SharedConstants.getCurrentVersion().getName();
  private static final String DATA_PATH = "data";
  private static final String MAPPINGS_PATH = "mappings";
  private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.0000");
  private static final Gson GSON = new GsonBuilder().create();

  private final Path cache;

  public MappingsDownloaderFactory(final Path cache) {
    this.cache = cache;
  }

  public MojangMappingsDownloader mojangMappings() {
    return new MojangMappingsDownloader(this.cache);
  }

  public YarnMappingsDownloader yarnMappings() {
    return new YarnMappingsDownloader(this.cache);
  }

  public static final class MojangMappingsDownloader implements MappingsDownloader {
    private static final String MC_MANIFEST_PATH = DATA_PATH + "/mc-manifest.json";
    private static final String MC_VERSION_MANIFEST_PATH = DATA_PATH + "/mc-version-" + MINECRAFT_VERSION + ".json";
    private static final String MOJANG_MAPPINGS_PATH = MAPPINGS_PATH + "/" + Namespace.MOJANG;
    private static final String MOJANG_SERVER_MAPPINGS_PATH = MOJANG_MAPPINGS_PATH + "/" + MappingsDownloaderFactory.MINECRAFT_VERSION + "-server.txt";
    private static final String MOJANG_CLIENT_MAPPINGS_PATH = MOJANG_MAPPINGS_PATH + "/" + MappingsDownloaderFactory.MINECRAFT_VERSION + "-client.txt";
    private static final String INTERMEDIARY_MAPPINGS_PATH = MAPPINGS_PATH + "/" + Namespace.INTERMEDIARY + "/" + MINECRAFT_VERSION + ".jar";
    private static final String INTERMEDIARY_URL = "https://maven.fabricmc.net/net/fabricmc/intermediary/{}/intermediary-{}-v2.jar";
    private static final String MC_MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";

    private final Path cache;

    private MojangMappingsDownloader(final Path cache) {
      this.cache = cache;
    }

    @Override
    public void downloadMappings() throws IOException {
      final Path mcManifestPath = this.cache.resolve(MC_MANIFEST_PATH);
      if (!Files.exists(mcManifestPath)) {
        downloadFile(MC_MANIFEST_URL, mcManifestPath);
      }
      final JsonObject mcManifest;
      try (final BufferedReader reader = Files.newBufferedReader(mcManifestPath)) {
        mcManifest = GSON.fromJson(reader, JsonObject.class);
      }

      final Path mcVersionManifestPath = this.cache.resolve(MC_VERSION_MANIFEST_PATH);
      if (!Files.exists(mcVersionManifestPath)) {
        final String versionManifestUrl = findVersionManifestUrl(MINECRAFT_VERSION, mcManifest);
        downloadFile(versionManifestUrl, mcVersionManifestPath);
      }
      final JsonObject mcVersionManifest;
      try (final BufferedReader reader = Files.newBufferedReader(mcVersionManifestPath)) {
        mcVersionManifest = GSON.fromJson(reader, JsonObject.class);
      }
      final JsonObject downloads = mcVersionManifest.get("downloads").getAsJsonObject();

      final Path serverMappings = this.serverMappings();
      if (!Files.exists(serverMappings)) {
        final String serverMappingsUrl = downloads.get("server_mappings").getAsJsonObject().get("url").getAsString();
        downloadFile(serverMappingsUrl, serverMappings);
      }

      final Path clientMappings = this.clientMappings();
      if (!Files.exists(clientMappings)) {
        final String clientMappingsUrl = downloads.get("client_mappings").getAsJsonObject().get("url").getAsString();
        downloadFile(clientMappingsUrl, clientMappings);
      }

      final Path intermediaryMappings = this.intermediaryMappingsJar();
      if (!Files.exists(intermediaryMappings)) {
        downloadFile(INTERMEDIARY_URL.replace("{}", MINECRAFT_VERSION), intermediaryMappings);
      }
    }

    public Path serverMappings() {
      return this.cache.resolve(MOJANG_SERVER_MAPPINGS_PATH);
    }

    public Path clientMappings() {
      return this.cache.resolve(MOJANG_CLIENT_MAPPINGS_PATH);
    }

    public Path intermediaryMappingsJar() {
      return this.cache.resolve(INTERMEDIARY_MAPPINGS_PATH);
    }

    private static String findVersionManifestUrl(final String minecraftVersion, final JsonObject mcManifest) {
      return StreamSupport.stream(mcManifest.get("versions").getAsJsonArray().spliterator(), false)
        .filter(version -> version.getAsJsonObject().get("id").getAsString().equals(minecraftVersion))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Could not find version manifest for Minecraft version '" + minecraftVersion + "' in Minecraft manifest."))
        .getAsJsonObject()
        .get("url")
        .getAsString();
    }
  }

  public static final class YarnMappingsDownloader implements MappingsDownloader {
    private static final String YARN_VERSIONS_PATH = DATA_PATH + "/yarn-versions.json";
    private static final String YARN_MAPPINGS_PATH = MAPPINGS_PATH + "/yarn/" + MINECRAFT_VERSION + ".jar";
    private static final String YARN_MAPPINGS_VERSION_PATH = MAPPINGS_PATH + "/yarn/" + MINECRAFT_VERSION + "-current-yarn.txt";
    private static final String YARN_URL = "https://maven.fabricmc.net/net/fabricmc/yarn/{}/yarn-{}-mergedv2.jar";
    private static final String YARN_VERSIONS_URL = "https://maven.fabricmc.net/net/fabricmc/yarn/versions.json";

    private final Path cache;

    private YarnMappingsDownloader(final Path cache) {
      this.cache = cache;
    }

    @Override
    public void downloadMappings() throws IOException {
      final Path yarnVersionsPath = this.cache.resolve(YARN_VERSIONS_PATH);

      final boolean downloadedJson = downloadYarnVersionsJson(yarnVersionsPath, false);
      @Nullable Integer latestBuild = findLatestYarnBuildForMcVersion(yarnVersionsPath);

      if (!downloadedJson && latestBuild == null) {
        downloadYarnVersionsJson(yarnVersionsPath, true);
        latestBuild = findLatestYarnBuildForMcVersion(yarnVersionsPath);
      }
      if (latestBuild == null) {
        throw new IllegalStateException("Could not find yarn mappings for version " + MINECRAFT_VERSION);
      }

      final String yarnVersion = MINECRAFT_VERSION + "+build." + latestBuild;

      final Path versionFile = this.cache.resolve(YARN_MAPPINGS_VERSION_PATH);
      if (Files.isRegularFile(versionFile) && Files.readString(versionFile).equals(yarnVersion)) {
        return;
      }
      this.downloadYarn(downloadedJson, yarnVersion);
    }

    private static @Nullable Integer findLatestYarnBuildForMcVersion(final Path yarnVersionsPath) throws IOException {
      final JsonObject yarnVersions;
      try (final BufferedReader reader = Files.newBufferedReader(yarnVersionsPath)) {
        yarnVersions = GSON.fromJson(reader, JsonObject.class);
      }
      return StreamSupport.stream(yarnVersions.get(MINECRAFT_VERSION).getAsJsonArray().spliterator(), false)
        .map(JsonElement::getAsInt)
        .max(Comparator.naturalOrder())
        .orElse(null);
    }

    private static boolean downloadYarnVersionsJson(final Path yarnVersionsPath, final boolean forceDownload) throws IOException {
      if (forceDownload || !Files.exists(yarnVersionsPath)) {
        downloadFile(YARN_VERSIONS_URL, yarnVersionsPath);
        return true;
      } else {
        final long lastModified = Files.getLastModifiedTime(yarnVersionsPath).toMillis();
        final Duration sinceModified = Duration.ofMillis(System.currentTimeMillis() - lastModified);
        if (sinceModified.compareTo(Duration.ofDays(7)) > 0) {
          downloadFile(YARN_VERSIONS_URL, yarnVersionsPath);
          return true;
        }
      }
      return false;
    }

    private void downloadYarn(final boolean forceDownload, final String yarnVersion) throws IOException {
      final Path yarnMappings = this.yarnMappingsJar();
      if (forceDownload || !Files.exists(yarnMappings)) {
        downloadFile(YARN_URL.replace("{}", yarnVersion), yarnMappings);
        final Path version = this.cache.resolve(YARN_MAPPINGS_VERSION_PATH);
        Files.writeString(version, yarnVersion);
      }
    }

    public Path yarnMappingsJar() {
      return this.cache.resolve(YARN_MAPPINGS_PATH);
    }
  }

  private static void downloadFile(final String url, final Path dest) throws IOException {
    Files.createDirectories(dest.getParent());
    Files.deleteIfExists(dest);

    LOGGER.info("Downloading " + url + "...");
    final long start = System.currentTimeMillis();
    try (
      final ReadableByteChannel downloadChannel = Channels.newChannel(new URL(url).openStream());
      final FileChannel outChannel = new FileOutputStream(dest.toFile()).getChannel()
    ) {
      outChannel.transferFrom(downloadChannel, 0, Long.MAX_VALUE);
      LOGGER.info("Done in {} seconds.", DECIMAL_FORMAT.format((System.currentTimeMillis() - start) / 1000.00D));
    } catch (final IOException ex) {
      Files.deleteIfExists(dest);
      throw ex;
    }
  }
}
