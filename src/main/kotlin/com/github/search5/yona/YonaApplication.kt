package com.github.search5.yona

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching

@SpringBootApplication
@EnableCaching
class YonaApplication

fun main(args: Array<String>) {
	runApplication<YonaApplication>(*args)
}
