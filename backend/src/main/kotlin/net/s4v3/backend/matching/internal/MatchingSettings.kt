package net.s4v3.backend.matching.internal

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(MatchingSettings::class)
internal class MatchingConfig

/** `jobhunter.matching.threshold`: the lowest score at which a posting that breaks no hard rule still passes. */
@ConfigurationProperties("jobhunter.matching")
internal data class MatchingSettings(val threshold: Int = 30)
