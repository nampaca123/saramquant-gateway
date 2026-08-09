package me.saramquantgateway.infra.user.service

import me.saramquantgateway.domain.document.UserDoc
import me.saramquantgateway.domain.enum.auth.AuthProvider
import me.saramquantgateway.domain.store.UserStore
import me.saramquantgateway.infra.storage.service.ProfileImageService
import me.saramquantgateway.infra.user.dto.ProfileUpdateRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import java.util.UUID

class ProfileServiceTest {

    private lateinit var userStore: UserStore
    private lateinit var profileImageService: ProfileImageService
    private lateinit var profileService: ProfileService

    @BeforeEach
    fun setUp() {
        userStore = Mockito.mock(UserStore::class.java)
        profileImageService = Mockito.mock(ProfileImageService::class.java)
        profileService = ProfileService(userStore, profileImageService)
    }

    @Test
    fun `getByUserId presigns the stored image key`() {
        val user = user().apply { profileImageKey = "profile-images/$id.png" }
        Mockito.`when`(userStore.findById(user.id)).thenReturn(user)
        Mockito.`when`(profileImageService.presignedUrl("profile-images/${user.id}.png"))
            .thenReturn("https://s3.example.com/signed?X-Amz-Signature=abc")

        val profile = profileService.getByUserId(user.id)

        assertEquals("https://s3.example.com/signed?X-Amz-Signature=abc", profile!!.profileImageUrl)
    }

    @Test
    fun `getByUserId leaves the image url null when no key is stored`() {
        val user = user()
        Mockito.`when`(userStore.findById(user.id)).thenReturn(user)

        val profile = profileService.getByUserId(user.id)

        assertNull(profile!!.profileImageUrl)
        Mockito.verify(profileImageService, Mockito.never()).presignedUrl(anyString())
    }

    @Test
    fun `getByUserId returns null for an unknown user`() {
        assertNull(profileService.getByUserId(UUID.randomUUID()))
    }

    @Test
    fun `update returns the presigned image url alongside the updated fields`() {
        val user = user().apply { profileImageKey = "profile-images/$id.jpg" }
        Mockito.`when`(userStore.findById(user.id)).thenReturn(user)
        Mockito.`when`(profileImageService.presignedUrl("profile-images/${user.id}.jpg")).thenReturn("https://signed")

        val profile = profileService.update(user.id, ProfileUpdateRequest(nickname = "quant"))

        assertEquals("quant", profile.nickname)
        assertEquals("https://signed", profile.profileImageUrl)
        Mockito.verify(userStore).save(user)
    }

    @Test
    fun `updateImageKey stores the raw key and clearImageKey removes it`() {
        val user = user()
        Mockito.`when`(userStore.findById(user.id)).thenReturn(user)

        profileService.updateImageKey(user.id, "profile-images/${user.id}.png")
        assertEquals("profile-images/${user.id}.png", user.profileImageKey)

        profileService.clearImageKey(user.id)
        assertNull(user.profileImageKey)
    }

    private fun user() = UserDoc(
        email = "user@example.com",
        emailHash = "hash",
        name = "User",
        provider = AuthProvider.GOOGLE,
        providerId = "pid",
    )
}
