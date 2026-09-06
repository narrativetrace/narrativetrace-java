/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.micronaut

import ai.narrativetrace.core.context.ThreadLocalNarrativeContext
import ai.narrativetrace.micronaut.sample.Greeter
import ai.narrativetrace.micronaut.sample.OutwardFacing
import ai.narrativetrace.micronaut.sample.SampleGreeter
import ai.narrativetrace.micronaut.sample.nested.NestedGreeter
import ai.narrativetrace.micronaut.samplex.SiblingGreeter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

/**
 * A base package matches itself and what is under it — nothing that merely starts with its letters.
 *
 * INTENT: The 2026-09-01 bug hunt found Micronaut matching packages by raw prefix while Spring used
 * a dot boundary, so configuring `com.acme` also wrapped `com.acme2` and `com.acmeExtra`. Silently
 * instrumenting a sibling module, or a third party sharing a prefix, is the kind of surprise that
 * shows up as somebody else's code in your trace.
 */
class BeanPackageBoundaryTest {
    private val base = "ai.narrativetrace.micronaut.sample"
    private val context = ThreadLocalNarrativeContext()

    private fun wrap(bean: Any): Any = NarrativeTraceBeanListener.wrapIfEligible(bean, listOf(base), context)

    @Test
    fun aBeanInTheBasePackageIsWrapped() {
        assertThat(Proxy.isProxyClass(wrap(SampleGreeter()).javaClass)).isTrue()
    }

    @Test
    fun aBeanInASubPackageOfTheBasePackageIsWrapped() {
        assertThat(Proxy.isProxyClass(wrap(NestedGreeter()).javaClass)).isTrue()
    }

    @Test
    fun aBeanInASiblingPackageSharingThePrefixIsLeftAlone() {
        val bean = SiblingGreeter()

        assertThat(wrap(bean)).describedAs("samplex is not under sample").isSameAs(bean)
    }

    @Test
    fun anInterfaceInASiblingPackageSharingThePrefixDoesNotQualifyABean() {
        val bean = OutwardFacing()

        assertThat(wrap(bean)).describedAs("its only interface lives in samplex").isSameAs(bean)
    }

    @Test
    fun aBeanKeepsItsContractWhenWrapped() {
        val wrapped = wrap(SampleGreeter()) as Greeter

        assertThat(wrapped.greet("Ada")).isEqualTo("Hello, Ada")
    }
}
