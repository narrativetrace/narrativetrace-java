/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.library

import ai.narrativetrace.api.annotation.OnError

interface CatalogService {
    @OnError(value = "Book {isbn} not found in catalog", exception = BookNotFoundException::class)
    fun findBook(isbn: String): Book
}

class InMemoryCatalogService : CatalogService {
    private val books =
        mapOf(
            "978-0-13-468599-1" to Book("978-0-13-468599-1", "The Pragmatic Programmer", "David Thomas & Andrew Hunt", true),
            "978-0-201-63361-0" to Book("978-0-201-63361-0", "Design Patterns", "Gang of Four", true),
            "978-0-13-235088-4" to Book("978-0-13-235088-4", "Clean Code", "Robert C. Martin", false),
        )

    override fun findBook(isbn: String): Book = books[isbn] ?: throw BookNotFoundException(isbn)
}
