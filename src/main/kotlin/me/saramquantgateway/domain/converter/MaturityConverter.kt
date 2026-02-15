package me.saramquantgateway.domain.converter

import me.saramquantgateway.domain.enum.Maturity
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter(autoApply = true)
class MaturityConverter : AttributeConverter<Maturity, String> {

    override fun convertToDatabaseColumn(attribute: Maturity): String =
        attribute.label

    override fun convertToEntityAttribute(dbData: String): Maturity =
        Maturity.fromLabel(dbData)
}