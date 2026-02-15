package me.saramquantgateway.infra.oauth.lib

import me.saramquantgateway.infra.oauth.dto.OAuthTokenResponse
import me.saramquantgateway.infra.oauth.dto.OAuthUserInfo
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient

@Component
class KakaoOAuthClient(private val props: OAuthProperties) : OAuthClient {

    private val rest = RestClient.create()

    override fun exchangeCode(code: String): OAuthTokenResponse {
        val body = LinkedMultiValueMap<String, String>().apply {
            add("code", code)
            add("client_id", props.kakao.clientId)
            add("redirect_uri", props.kakao.redirectUri)
            add("grant_type", "authorization_code")
            if (props.kakao.clientSecret.isNotBlank()) {
                add("client_secret", props.kakao.clientSecret)
            }
        }

        val res = rest.post()
            .uri("https://kauth.kakao.com/oauth/token")
            .body(body)
            .retrieve()
            .body(Map::class.java)!!

        return OAuthTokenResponse(accessToken = res["access_token"] as String)
    }

    @Suppress("UNCHECKED_CAST")
    override fun getUserInfo(accessToken: String): OAuthUserInfo {
        val res = rest.get()
            .uri("https://kapi.kakao.com/v2/user/me")
            .header("Authorization", "Bearer $accessToken")
            .retrieve()
            .body(Map::class.java) as Map<String, Any>

        val kakaoAccount = res["kakao_account"] as? Map<String, Any> ?: emptyMap()
        val profile = kakaoAccount["profile"] as? Map<String, Any> ?: emptyMap()

        return OAuthUserInfo(
            email = kakaoAccount["email"] as String,
            name = profile["nickname"] as? String ?: "Kakao User",
            providerId = res["id"].toString(),
            imageUrl = profile["profile_image_url"] as? String,
        )
    }
}
