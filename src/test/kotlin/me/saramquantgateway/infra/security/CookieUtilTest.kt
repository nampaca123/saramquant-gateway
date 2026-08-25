package me.saramquantgateway.infra.security

// 쿠키 Domain 설정값이 실제 Set-Cookie에 반영되는지(비어 있으면 host-only) 검증한다
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito

class CookieUtilTest {

    private fun captureAccessCookie(domain: String): Cookie {
        val response = Mockito.mock(HttpServletResponse::class.java)
        CookieUtil(900, 604800, true, domain).setAccessToken(response, "token")
        val captor = ArgumentCaptor.forClass(Cookie::class.java)
        Mockito.verify(response).addCookie(captor.capture())
        return captor.value
    }

    @Test
    fun `domain이 설정되면 쿠키에 반영된다`() {
        val cookie = captureAccessCookie(".saramquant.com")
        assertEquals(".saramquant.com", cookie.domain)
    }

    @Test
    fun `domain이 비어 있으면 host-only 쿠키가 된다`() {
        val cookie = captureAccessCookie("")
        assertNull(cookie.domain)
    }
}
