package me.saramquantgateway.infra.config

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

// ECS 컨테이너 헬스체크 전용 — 인증 없이 200을 돌려준다.
@RestController
class HealthController {

    @GetMapping("/healthz")
    fun healthz(): Map<String, String> = mapOf("status" to "ok")
}
