/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.library

import ai.narrativetrace.api.annotation.NarrativeSummary
import java.time.LocalDate

data class Book(
    val isbn: String,
    val title: String,
    val author: String,
    val available: Boolean,
) {
    @NarrativeSummary
    fun narrativeSummary(): String = "$title by $author"
}

data class Member(
    val id: String,
    val name: String,
    val cardNumber: String,
) {
    @NarrativeSummary
    fun narrativeSummary(): String = name
}

data class LoanReceipt(
    val bookTitle: String,
    val memberName: String,
    val dueDate: LocalDate,
) {
    @NarrativeSummary
    fun narrativeSummary(): String = "$bookTitle loaned to $memberName, due $dueDate"
}

class BookNotFoundException(
    isbn: String,
) : RuntimeException("Book not found: $isbn")

class BookUnavailableException(
    isbn: String,
) : RuntimeException("Book not available: $isbn")
