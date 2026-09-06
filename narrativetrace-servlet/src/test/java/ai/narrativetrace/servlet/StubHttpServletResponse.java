/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.servlet;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("PMD") // Test stub — only status is meaningful
class StubHttpServletResponse implements HttpServletResponse {

  private int status = 200;

  @Override
  public int getStatus() {
    return status;
  }

  @Override
  public void setStatus(int sc) {
    this.status = sc;
  }

  @Override
  public void addCookie(Cookie cookie) {}

  @Override
  public boolean containsHeader(String name) {
    return false;
  }

  @Override
  public String encodeURL(String url) {
    return url;
  }

  @Override
  public String encodeRedirectURL(String url) {
    return url;
  }

  @Override
  public void sendError(int sc, String msg) {}

  @Override
  public void sendError(int sc) {}

  @Override
  public void sendRedirect(String location) {}

  @Override
  public void setDateHeader(String name, long date) {}

  @Override
  public void addDateHeader(String name, long date) {}

  @Override
  public void setHeader(String name, String value) {}

  @Override
  public void addHeader(String name, String value) {}

  @Override
  public void setIntHeader(String name, int value) {}

  @Override
  public void addIntHeader(String name, int value) {}

  @Override
  public String getHeader(String name) {
    return null;
  }

  @Override
  public Collection<String> getHeaders(String name) {
    return List.of();
  }

  @Override
  public Collection<String> getHeaderNames() {
    return List.of();
  }

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
