/*
 * This file is part of Better Fabric Console, licensed under the MIT License.
 *
 * Copyright (c) 2026 Jason Penilla
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
package xyz.jpenilla.betterfabricconsole.configuration;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import net.fabricmc.loader.api.ModContainer;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.xml.sax.SAXException;

@NullMarked
public final class Log4jConfigManager {
  private static final Logger LOGGER = LogUtils.getLogger();
  public static final String DISMISS_CONFIG_UPDATE_PROPERTY = "better-fabric-console.dismiss-log4j-config-update";
  private static final String DEFAULT_CONFIG_RESOURCE = "better-fabric-console-default-log4j2.xml";

  private final Path bundledConfigPath;
  private final Path activeConfigPath;
  private final Path latestDefaultPath;
  private final Path reviewedDefaultPath;

  public Log4jConfigManager(final Path configDir, final ModContainer modContainer) {
    this.bundledConfigPath = modContainer.findPath(DEFAULT_CONFIG_RESOURCE)
      .orElseThrow(() -> new IllegalStateException("Could not find " + DEFAULT_CONFIG_RESOURCE + " in mod container"));
    this.activeConfigPath = configDir.resolve("log4j2.xml");
    this.latestDefaultPath = configDir.resolve("defaults/log4j2.xml");
    this.reviewedDefaultPath = configDir.resolve(".tracking/log4j2.reviewed-default.xml");
  }

  public Path prepareConfig() {
    try {
      copyFile(this.bundledConfigPath, this.latestDefaultPath);
      this.prepareActiveConfig();
    } catch (final IOException ex) {
      throw new RuntimeException("Failed to prepare Better Fabric Console Log4j config", ex);
    }

    if (this.isWellFormedXml(this.activeConfigPath)) {
      return this.activeConfigPath;
    }

    LOGGER.warn(
      """
      Failed to parse Better Fabric Console Log4j config at {}. Using the bundled default for this run. \
      Your file was not changed. A fresh default is available at {}. \
      Fix your edits, copy from the default, or delete log4j2.xml to regenerate.\
      """,
      this.activeConfigPath,
      this.latestDefaultPath
    );
    System.err.println("Failed to parse Better Fabric Console Log4j config at " + this.activeConfigPath + ".");
    System.err.println("Using the bundled default for this run. Your file was not changed.");
    System.err.println("A fresh default is available at " + this.latestDefaultPath + ".");
    return this.latestDefaultPath;
  }

  public void dismissUpdate() throws IOException {
    if (!Files.isRegularFile(this.latestDefaultPath)) {
      copyFile(this.bundledConfigPath, this.latestDefaultPath);
    }
    this.markCurrentDefaultReviewed();
  }

  private void prepareActiveConfig() throws IOException {
    if (Files.exists(this.activeConfigPath) && !Files.isRegularFile(this.activeConfigPath)) {
      throw new IOException("Log4j config path exists but is not a regular file: " + this.activeConfigPath);
    }
    if (Files.exists(this.reviewedDefaultPath) && !Files.isRegularFile(this.reviewedDefaultPath)) {
      throw new IOException("Log4j config tracking path exists but is not a regular file: " + this.reviewedDefaultPath);
    }

    if (!Files.exists(this.activeConfigPath)) {
      copyFile(this.bundledConfigPath, this.activeConfigPath);
      copyFile(this.bundledConfigPath, this.reviewedDefaultPath);
      LOGGER.info("Generated Better Fabric Console Log4j config at {}", this.activeConfigPath);
      return;
    }

    if (fileContentEquals(this.activeConfigPath, this.bundledConfigPath)) {
      copyFile(this.bundledConfigPath, this.reviewedDefaultPath);
      return;
    }

    final boolean dismissUpdate = Boolean.getBoolean(DISMISS_CONFIG_UPDATE_PROPERTY);
    if (!Files.isRegularFile(this.reviewedDefaultPath)) {
      if (dismissUpdate) {
        this.markCurrentDefaultReviewed();
      } else {
        this.warnOnMissingTracking();
      }
      return;
    }

    if (fileContentEquals(this.activeConfigPath, this.reviewedDefaultPath)) {
      if (!fileContentEquals(this.bundledConfigPath, this.reviewedDefaultPath)) {
        copyFile(this.bundledConfigPath, this.activeConfigPath);
        copyFile(this.bundledConfigPath, this.reviewedDefaultPath);
        LOGGER.info("Updated unmodified Better Fabric Console Log4j config at {} to the latest bundled default", this.activeConfigPath);
      }
      return;
    }

    if (!fileContentEquals(this.bundledConfigPath, this.reviewedDefaultPath)) {
      if (dismissUpdate) {
        this.markCurrentDefaultReviewed();
      } else {
        this.warnOnConfigUpdate();
      }
    }
  }

  private void markCurrentDefaultReviewed() throws IOException {
    copyFile(this.latestDefaultPath, this.reviewedDefaultPath);
    LOGGER.info(
      """
      Log4j config update dismissed. The current bundled Log4j default is now marked as reviewed. \
      This only updates Better Fabric Console's tracking metadata; it does not edit your active log4j2.xml.\
      """
    );
  }

  private void warnOnMissingTracking() {
    LOGGER.warn(
      """
      Better Fabric Console found an existing custom Log4j config at {} without config-update tracking metadata. It will not be auto-updated.
      Latest default: {}
      Review/merge changes, then run:
        /better-fabric-console dismiss-log4j-config-update
      Or start once with:
        -D{}=true\
      """,
      this.activeConfigPath,
      this.latestDefaultPath,
      DISMISS_CONFIG_UPDATE_PROPERTY
    );
  }

  private void warnOnConfigUpdate() {
    LOGGER.warn(
      """
      Your custom Better Fabric Console Log4j config at {} may be based on an older bundled default.
      Latest default: {}
      Review/merge changes, then run:
        /better-fabric-console dismiss-log4j-config-update
      Or start once with:
        -D{}=true\
      """,
      this.activeConfigPath,
      this.latestDefaultPath,
      DISMISS_CONFIG_UPDATE_PROPERTY
    );
  }

  private boolean isWellFormedXml(final Path path) {
    try (final InputStream inputStream = Files.newInputStream(path)) {
      final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.newDocumentBuilder().parse(inputStream);
      return true;
    } catch (final IOException | ParserConfigurationException | SAXException ex) {
      LOGGER.warn("Failed to parse XML at {}", path, ex);
      return false;
    }
  }

  private static boolean fileContentEquals(final Path a, final Path b) throws IOException {
    return Files.isRegularFile(a) && Files.isRegularFile(b) && Files.mismatch(a, b) == -1L;
  }

  private static void copyFile(final Path source, final Path target) throws IOException {
    final Path parent = target.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
  }
}
