package com.eligo.server.common.web

import com.eligo.server.common.api.Result
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/system")
class SystemController {

    @GetMapping("/ping")
    fun ping(): Result<PingResponse> = Result.success(PingResponse("UP"))
}
