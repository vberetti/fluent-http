/**
 * Copyright (C) 2013-2014 all@code-story.net
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package net.codestory.http.io;

import static java.nio.charset.StandardCharsets.*;
import static net.codestory.http.io.ClassPaths.*;

import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.nio.file.*;

import net.codestory.http.compilers.*;
import net.codestory.http.misc.*;

public class Resources {
  private static final String[] TEMPLATE_EXTENSIONS = {"", ".html", ".md", ".markdown", ".txt"};

  private final String root;

  public Resources(Env env) {
    this(env.appFolder());
  }

  public Resources(String root) {
    this.root = root;
  }

  public SourceFile sourceFile(Path path) throws IOException {
    return new SourceFile(path, read(path, UTF_8));
  }

  public boolean isPublic(Path path) {
    for (Path part : path) {
      if (part.toString().equals("..") || part.toString().startsWith("_")) {
        return false;
      }
    }
    return exists(path);
  }

  public Path findExistingPath(String uri) {
    if (uri.endsWith("/")) {
      return findExistingPath(uri + "index");
    }
    for (String extension : TEMPLATE_EXTENSIONS) {
      Path templatePath = Paths.get(uri + extension);
      if (exists(templatePath)) {
        return templatePath;
      }
    }
    return null;
  }

  public boolean exists(Path path) {
    String pathWithPrefix = withPrefix(path);
    return existsInFileSystem(pathWithPrefix) || existsInClassPath(pathWithPrefix);
  }

  public String read(Path path, Charset charset) throws IOException {
    String pathWithPrefix = withPrefix(path);
    return existsInFileSystem(pathWithPrefix) ? readFile(pathWithPrefix, charset) : readClasspath(pathWithPrefix, charset);
  }

  public byte[] readBytes(Path path) throws IOException {
    String pathWithPrefix = withPrefix(path);
    return existsInFileSystem(pathWithPrefix) ? readFileBytes(pathWithPrefix) : readClasspathBytes(pathWithPrefix);
  }

  // static

  public static String relativePath(Path parent, Path path) {
    return toUnixString(parent.relativize(path));
  }

  public static String toUnixString(Path path) {
    return path.toString().replace('\\', '/');
  }

  // private

  private String withPrefix(Path path) {
    return toUnixString(Paths.get(root, path.toString()));
  }

  private boolean existsInClassPath(String path) {
    URL url = getResource(path);
    if (url == null) {
      return false;
    }

    File file = fileForClasspath(url);
    return (file == null) || file.isFile();
  }

  private String readClasspath(String path, Charset charset) throws IOException {
    URL url = getResource(path);
    if (url == null) {
      throw new IllegalArgumentException("Classpath resource not found classpath:" + path);
    }

    File file = fileForClasspath(url);
    if (file != null) {
      if (!file.isFile()) {
        throw new IllegalArgumentException("Invalid file classpath: " + path);
      }
      return readFile(file.getAbsolutePath(), charset);
    }

    try (InputStream from = url.openStream()) {
      return InputStreams.readString(from, charset);
    }
  }

  private byte[] readClasspathBytes(String path) throws IOException {
    URL url = getResource(path);
    if (url == null) {
      throw new IllegalArgumentException("Invalid file classpath: " + path);
    }

    File file = fileForClasspath(url);
    if (file != null) {
      if (!file.isFile()) {
        throw new IllegalArgumentException("Invalid file classpath: " + path);
      }
      return readFileBytes(file.getAbsolutePath());
    }

    try (InputStream from = url.openStream()) {
      return InputStreams.readBytes(from);
    }
  }

  // Visible for testing
  File fileForClasspath(URL url) {
    String filename = url.getFile();
    if ((filename == null) || filename.contains(".jar!")) {
      return null;
    }

    try {
      String path = URLDecoder.decode(filename, "US-ASCII");

      // Search for file in sources instead of target to speed up live reload
      String sourcePath = Paths.get("src/main/resources/", root, Strings.substringAfter(path, '/' + root + '/')).toString();
      File file = new File(sourcePath);
      if (file.exists()) {
        return file;
      }

      return new File(path);
    } catch (UnsupportedEncodingException e) {
      throw new IllegalArgumentException("Invalid filename classpath: " + url, e);
    }
  }

  private static boolean existsInFileSystem(String path) {
    return new File(path).isFile();
  }

  private static String readFile(String path, Charset charset) throws IOException {
    if (!new File(path).isFile()) {
      throw new IllegalArgumentException("Invalid file path: " + path);
    }

    try (InputStream from = new FileInputStream(path)) {
      return InputStreams.readString(from, charset);
    }
  }

  private static byte[] readFileBytes(String path) throws IOException {
    if (!new File(path).isFile()) {
      throw new IllegalArgumentException("Invalid file path: " + path);
    }

    try (InputStream from = new FileInputStream(path)) {
      return InputStreams.readBytes(from);
    }
  }
}
