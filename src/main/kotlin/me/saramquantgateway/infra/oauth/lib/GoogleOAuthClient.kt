package me.saramquantgateway.infra.oauth.lib

import me.saramquantgateway.infra.oauth.dto.OAuthTokenResponse
import me.saramquantgateway.infra.oauth.dto.OAuthUserInfo
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient

@Component
class GoogleOAuthClient(private val props: OAuthProperties) : OAuthClient {

    private val rest = RestClient.create()

    override fun exchangeCode(code: String): OAuthTokenResponse {
        val body = LinkedMultiValueMap<String, String>().apply {
            add("code", code)
            add("client_id", props.google.clientId)
            add("client_secret", props.google.clientSecret)
            add("redirect_uri", props.google.redirectUri)
            add("grant_type", "authorization_code")
        }

        val res = rest.post()
            .uri("https://oauth2.googleapis.com/token")
            .body(body)
            .retrieve()
            .body(Map::class.java)!!

        return OAuthTokenResponse(accessToken = res["access_token"] as String)
    }

    @Suppress("UNCHECKED_CAST")
    override fun getUserInfo(accessToken: String): OAuthUserInfo {
        val res = rest.get()
            .uri("https://www.googleapis.com/oauth2/v2/userinfo")
            .header("Authorization", "Bearer $accessToken")
            .retrieve()
            .body(Map::class.java) as Map<String, Any>

        return OAuthUserInfo(
            email = res["email"] as String,
            name = res["name"] as String,
            providerId = res["id"] as String,
            imageUrl = res["picture"] as? String,
        )
    }
}
