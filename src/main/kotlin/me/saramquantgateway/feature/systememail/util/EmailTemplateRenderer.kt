package me.saramquantgateway.feature.systememail.util

import org.springframework.stereotype.Component
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import java.time.Year

@Component
class EmailTemplateRenderer(private val templateEngine: TemplateEngine) {

    companion object {
        private const val LOGO_URL = "https://www.saramquant.com/image/logo/saramquant-logo.jpg"
        private const val APP_NAME = "SaramQuant"
    }

    fun render(templateName: String, variables: Map<String, Any>): String {
        val ctx = Context().apply {
            setVariable("logoUrl", LOGO_URL)
            setVariable("appName", APP_NAME)
            setVariable("currentYear", Year.now().value)
            variables.forEach { (k, v) -> setVariable(k, v) }
        }
        return templateEngine.process("email/$templateName", ctx)
    }
}
