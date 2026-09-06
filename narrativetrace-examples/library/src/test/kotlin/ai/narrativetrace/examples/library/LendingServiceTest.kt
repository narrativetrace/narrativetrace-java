/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.library

import ai.narrativetrace.core.context.NarrativeContext
import ai.narrativetrace.core.render.IndentedTextRenderer
import ai.narrativetrace.junit5.NarrativeTraceExtension
import ai.narrativetrace.proxy.NarrativeTraceProxy
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(NarrativeTraceExtension::class)
class LendingServiceTest {
    @Test
    fun memberBorrowsAvailableBook(context: NarrativeContext) {
        val lending = wireServices(context)

        val receipt = lending.borrowBook("M-001", "978-0-13-468599-1")

        assertThat(receipt.bookTitle).isEqualTo("The Pragmatic Programmer")
        assertThat(receipt.memberName).isEqualTo("Alice")

        val narrative = IndentedTextRenderer().render(context.captureTrace())
        assertThat(narrative).contains("LendingService.borrowBook")
        assertThat(narrative).contains("CatalogService.findBook")
        assertThat(narrative).contains("MemberService.lookupMember")
    }

    @Test
    fun borrowingUnavailableBookThrows(context: NarrativeContext) {
        val lending = wireServices(context)

        // "Clean Code" is in the catalog but marked as unavailable
        assertThatThrownBy { lending.borrowBook("M-001", "978-0-13-235088-4") }
            .isInstanceOf(BookUnavailableException::class.java)
            .hasMessage("Book not available: 978-0-13-235088-4")

        val narrative = IndentedTextRenderer().render(context.captureTrace())
        assertThat(narrative).contains("CatalogService.findBook")
        assertThat(narrative).contains("BookUnavailableException")
        // Member lookup should not have been called since book was unavailable
        assertThat(narrative).doesNotContain("MemberService.lookupMember")
    }

    @Test
    fun borrowingMissingBookThrows(context: NarrativeContext) {
        val lending = wireServices(context)

        assertThatThrownBy { lending.borrowBook("M-001", "978-1-23-456789-0") }
            .isInstanceOf(BookNotFoundException::class.java)
            .hasMessage("Book not found: 978-1-23-456789-0")

        val narrative = IndentedTextRenderer().render(context.captureTrace())
        assertThat(narrative).contains("CatalogService.findBook")
        assertThat(narrative).contains("BookNotFoundException")
        assertThat(narrative).doesNotContain("MemberService.lookupMember")
    }

    private fun wireServices(context: NarrativeContext): LendingService {
        val catalog =
            NarrativeTraceProxy.trace(
                InMemoryCatalogService(),
                CatalogService::class.java,
                context,
            )
        val members =
            NarrativeTraceProxy.trace(
                InMemoryMemberService(),
                MemberService::class.java,
                context,
            )
        return NarrativeTraceProxy.trace(
            DefaultLendingService(catalog, members),
            LendingService::class.java,
            context,
        )
    }
}
