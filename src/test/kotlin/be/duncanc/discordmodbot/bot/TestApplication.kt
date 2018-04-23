package be.duncanc.discordmodbot

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TestApplication {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<TestApplication>(*args)
        }
    }
}