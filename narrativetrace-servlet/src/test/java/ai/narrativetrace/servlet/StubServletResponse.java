/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.servlet;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletResponse;
import java.io.PrintWriter;
import java.util.Locale;

@SuppressWarnings("PMD") // Test stub — all methods return null/defaults
class StubServletResponse implements ServletResponse {

  @Override
  public String getCharacterEncoding() {
    return null;
  }

  @Override
  public String getContentType() {
    return null;
  }

  @Override
  public ServletOutputStream getOutputStream() {
    return null;
  }

  @Override
  public PrintWriter getWriter() {
    return null;
  }

  @Override
  public void setCharacterEncoding(String charset) {}

  @Override
  public void setContentLength(int len) {}

  @Override
  public void setContentLengthLong(long len) {}

  @Override
  public void setContentType(String type) {}

  @Override
  public void setBufferSize(int size) {}

  @Override
  public int getBufferSize() {
    return 0;
  }

  @Override
  public void flushBuffer() {}

  @Override
  public void resetBuffer() {}

  @Override
  public boolean isCommitted() {
    return false;
  }

  @Override
  public void reset() {}

  @Override
  public void setLocale(Locale loc) {}

  @Override
  public Locale getLocale() {
    return null;
  }
}
