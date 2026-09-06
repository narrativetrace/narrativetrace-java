/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.library

import ai.narrativetrace.api.annotation.Narrated
import java.time.LocalDate

interface LendingService {
    @Narrated("Borrowing book {isbn} for member {memberId}")
    fun borrowBook(
        memberId: String,
        isbn: String,
    ): LoanReceipt
}

class DefaultLendingService(
    private val catalog: CatalogService,
    private val members: MemberService,
) : LendingService {
    override fun borrowBook(
        memberId: String,
        isbn: String,
    ): LoanReceipt {
        val book = catalog.findBook(isbn)
        if (!book.available) {
            throw BookUnavailableException(isbn)
        }
        val member = members.lookupMember(memberId, "CARD-VERIFY")
        return LoanReceipt(book.title, member.name, LocalDate.now().plusWeeks(2))
    }
}
