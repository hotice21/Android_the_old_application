package com.eligo.server

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration

@SpringBootApplication(exclude = [UserDetailsServiceAutoConfiguration::class])
class EligoServerApplication

fun main(args: Array<String>) {
    SpringApplication.run(EligoServerApplication::class.java, *args)
}
