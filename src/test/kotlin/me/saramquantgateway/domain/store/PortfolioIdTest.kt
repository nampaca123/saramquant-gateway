package me.saramquantgateway.domain.store

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class PortfolioIdTest {

    @Test
    fun `same user and market group always yields the same id`() {
        val userId = UUID.fromString("11111111-2222-3333-4444-555555555555")

        val first = PortfolioStore.portfolioIdOf(userId, "KR")
        val second = PortfolioStore.portfolioIdOf(userId, "KR")

        assertEquals(first, second)
        assertEquals(first, PortfolioStore.portfolioIdOf(UUID.fromString(userId.toString()), "KR"))
    }

    @Test
    fun `different market groups yield different ids`() {
        val userId = UUID.randomUUID()

        assertNotEquals(
            PortfolioStore.portfolioIdOf(userId, "KR"),
            PortfolioStore.portfolioIdOf(userId, "US"),
        )
    }

    @Test
    fun `different users yield different ids`() {
        assertNotEquals(
            PortfolioStore.portfolioIdOf(UUID.randomUUID(), "KR"),
            PortfolioStore.portfolioIdOf(UUID.randomUUID(), "KR"),
        )
    }

    @Test
    fun `ids are non negative`() {
        repeat(50) {
            assertTrue(PortfolioStore.portfolioIdOf(UUID.randomUUID(), "US") >= 0)
        }
    }
}
