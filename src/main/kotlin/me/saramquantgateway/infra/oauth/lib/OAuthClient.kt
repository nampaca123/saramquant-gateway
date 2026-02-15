package me.saramquantgateway.infra.oauth.lib

import me.saramquantgateway.infra.oauth.dto.OAuthTokenResponse
import me.saramquantgateway.infra.oauth.dto.OAuthUserInfo

interface OAuthClient {
    fun exchangeCode(code: String): OAuthTokenResponse
    fun getUserInfo(accessToken: String): OAuthUserInfo
}
