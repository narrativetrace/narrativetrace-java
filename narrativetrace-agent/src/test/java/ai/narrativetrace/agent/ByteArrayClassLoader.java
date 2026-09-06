/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

public class ByteArrayClassLoader extends ClassLoader {

  private final byte[] classBytes;
  private final String className;

  public ByteArrayClassLoader(ClassLoader parent, byte[] classBytes, String className) {
    super(parent);
    this.classBytes = classBytes;
    this.className = className;
  }

  @Override
  protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    if (name.equals(className)) {
      var clazz = defineClass(name, classBytes, 0, classBytes.length);
      if (resolve) {
        resolveClass(clazz);
      }
      return clazz;
    }
    return super.loadClass(name, resolve);
  }
}
