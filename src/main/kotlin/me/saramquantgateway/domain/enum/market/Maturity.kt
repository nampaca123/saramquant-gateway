package me.saramquantgateway.domain.enum.market

enum class Maturity(val label: String) {
    D91("91D"),
    Y1("1Y"),
    Y3("3Y"),
    Y10("10Y");

    // DB에 저장된 값이 "91D", "1Y" 등이므로 label로 역변환 필요
    companion object {
        private val byLabel = entries.associateBy { it.label }
        fun fromLabel(label: String): Maturity =
            byLabel[label] ?: throw IllegalArgumentException("Unknown maturity: $label")
    }
}