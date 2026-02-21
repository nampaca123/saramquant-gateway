package me.saramquantgateway.infra.security.crypto

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.stereotype.Component

@Converter
@Component
class EncryptionConverter(
    private val aes: AesEncryptor,
) : AttributeConverter<String, String> {

    override fun convertToDatabaseColumn(attribute: String?): String? =
        attribute?.let { aes.encrypt(it) }

    override fun convertToEntityAttribute(dbData: String?): String? =
        dbData?.let { aes.decrypt(it) }
}
