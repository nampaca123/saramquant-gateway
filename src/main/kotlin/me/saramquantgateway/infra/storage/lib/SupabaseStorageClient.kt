package me.saramquantgateway.infra.storage.lib

import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class SupabaseStorageClient(private val props: SupabaseStorageProperties) {

    private val rest = RestClient.create()

    fun upload(path: String, bytes: ByteArray, contentType: String): String {
        rest.post()
            .uri("${props.url}/object/${props.bucket}/$path")
            .header("Authorization", "Bearer ${props.secretKey}")
            .header("x-upsert", "true")
            .contentType(MediaType.parseMediaType(contentType))
            .body(bytes)
            .retrieve()
            .toBodilessEntity()

        return getPublicUrl(path)
    }

    fun delete(path: String) {
        rest.delete()
            .uri("${props.url}/object/${props.bucket}/$path")
            .header("Authorization", "Bearer ${props.secretKey}")
            .retrieve()
            .toBodilessEntity()
    }

    fun getPublicUrl(path: String): String =
        "${props.url}/object/public/${props.bucket}/$path"
}
