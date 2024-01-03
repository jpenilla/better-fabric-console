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

import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.StreamSupport;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.DefaultQualifier;

@DefaultQualifier(NonNull.class)
final class MojangMappingsDownloader implements MappingsDownloader<MojangMappingsDownloader.MojangMappingsData> {
  private static final String MC_MANIFEST_PATH = MappingsCache.DATA_PATH + "/mc-manifest.json";
  private static final String MC_VERSION_MANIFEST_PATH = MappingsCache.DATA_PATH + "/mc-version-" + MappingsCache.MINECRAFT_VERSION + ".json";
  private static final String MOJANG_MAPPINGS_PATH = MappingsCache.MAPPINGS_PATH + "/" + Namespace.MOJANG;
  private static final String MOJANG_SERVER_MAPPINGS_PATH = MOJANG_MAPPINGS_PATH + "/" + MappingsCache.MINECRAFT_VERSION + "-server.txt";
  private static final String MOJANG_CLIENT_MAPPINGS_PATH = MOJANG_MAPPINGS_PATH + "/" + MappingsCache.MINECRAFT_VERSION + "-client.txt";
  private static final String INTERMEDIARY_MAPPINGS_PATH = MappingsCache.MAPPINGS_PATH + "/" + Namespace.INTERMEDIARY + "/" + MappingsCache.MINECRAFT_VERSION + ".jar";
  private static final String INTERMEDIARY_URL = "https://maven.fabricmc.net/net/fabricmc/intermediary/{}/intermediary-{}-v2.jar";
  private static final String MC_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

  private final Path cache;

  MojangMappingsDownloader(final MappingsCache cache) {
    this.cache = cache.cache;
  }

  private McManifestResult mcManifest(final boolean forceDownload) throws IOException {
    final Path mcManifestPath = this.cache.resolve(MC_MANIFEST_PATH);
    final boolean download = forceDownload || !Files.exists(mcManifestPath);
    if (download) {
      MappingsCache.downloadFile(MC_MANIFEST_URL, mcManifestPath);
    }
    final JsonObject mcManifest;
    try (final BufferedReader reader = Files.newBufferedReader(mcManifestPath)) {
      mcManifest = MappingsCache.GSON.fromJson(reader, JsonObject.class);
    }
    return new McManifestResult(download, mcManifest);
  }

  @Override
  public MojangMappingsData downloadMappings() throws IOException {
    final Path mcVersionManifestPath = this.cache.resolve(MC_VERSION_MANIFEST_PATH);
    if (!Files.exists(mcVersionManifestPath)) {
      McManifestResult mcManifest = this.mcManifest(false);
      @Nullable String versionManifestUrl = findVersionManifestUrl(MappingsCache.MINECRAFT_VERSION, mcManifest.result());
      if (versionManifestUrl == null && !mcManifest.didDownload()) {
        mcManifest = this.mcManifest(true);
        versionManifestUrl = findVersionManifestUrl(MappingsCache.MINECRAFT_VERSION, mcManifest.result());
      }
      if (versionManifestUrl == null) {
        throw new IllegalArgumentException("Could not find version manifest for Minecraft version '" + MappingsCache.MINECRAFT_VERSION + "' in Minecraft manifest.");
      }
      MappingsCache.downloadFile(versionManifestUrl, mcVersionManifestPath);
    }
    final JsonObject mcVersionManifest;
    try (final BufferedReader reader = Files.newBufferedReader(mcVersionManifestPath)) {
      mcVersionManifest = MappingsCache.GSON.fromJson(reader, JsonObject.class);
    }
    final JsonObject downloads = mcVersionManifest.get("downloads").getAsJsonObject();

    final Path serverMappings = this.serverMappings();
    if (!Files.exists(serverMappings)) {
      final String serverMappingsUrl = downloads.get("server_mappings").getAsJsonObject().get("url").getAsString();
      MappingsCache.downloadFile(serverMappingsUrl, serverMappings);
    }

    final Path clientMappings = this.clientMappings();
    if (!Files.exists(clientMappings)) {
      final String clientMappingsUrl = downloads.get("client_mappings").getAsJsonObject().get("url").getAsString();
      MappingsCache.downloadFile(clientMappingsUrl, clientMappings);
    }

    final Path intermediaryMappings = this.intermediaryMappingsJar();
    if (!Files.exists(intermediaryMappings)) {
      MappingsCache.downloadFile(INTERMEDIARY_URL.replace("{}", MappingsCache.MINECRAFT_VERSION), intermediaryMappings);
    }

    return new MojangMappingsData(this.serverMappings(), this.clientMappings(), this.intermediaryMappingsJar());
  }

  private Path serverMappings() {
    return this.cache.resolve(MOJANG_SERVER_MAPPINGS_PATH);
  }

  private Path clientMappings() {
    return this.cache.resolve(MOJANG_CLIENT_MAPPINGS_PATH);
  }

  private Path intermediaryMappingsJar() {
    return this.cache.resolve(INTERMEDIARY_MAPPINGS_PATH);
  }

  private static @Nullable String findVersionManifestUrl(final String minecraftVersion, final JsonObject mcManifest) {
    return StreamSupport.stream(mcManifest.get("versions").getAsJsonArray().spliterator(), false)
      .filter(version -> version.getAsJsonObject().get("id").getAsString().equals(minecraftVersion))
      .findFirst()
      .map(v -> v.getAsJsonObject().get("url").getAsString())
      .orElse(null);
  }

  public record MojangMappingsData(Path serverMappings, Path clientMappings, Path intermediaryMappingsJar) {
  }

  private record McManifestResult(boolean didDownload, JsonObject result) {
  }
}
