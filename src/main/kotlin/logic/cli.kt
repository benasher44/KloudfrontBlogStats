package com.benasher44.kloudfrontblogstats.logic

import com.benasher44.kloudfrontblogstats.utils.Logger
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import software.amazon.awssdk.regions.Region
import java.time.Instant
import java.time.ZoneId

internal class CLI(private val runLambda: CLI.() -> Unit) : CliktCommand(), Logger {

    val bucket by option(
        "--bucket",
        help = "The S3 bucket containing the log files"
    ).required()

    val region by option(
        "--region",
        help = "The AWS region where the S3 bucket resides"
    )
        .choice(choices = Region.regions().map { it.id() }.toTypedArray())
        .convert { Region.of(it)!! }
        .required()

    val allowDelete by option(
        "--allow-delete",
        help = "When set, allows the tool to delete objects after processing"
    ).flag(default = false)

    override fun run() {
        runLambda.invoke(this)
    }

    override fun log(msg: String) {
        echo(msg.logMessage())
    }

    override fun error(msg: String) {
        echo(msg.logMessage(), err = true)
    }
}

private fun String.logMessage(): String =
    "[${Instant.now().atZone(ZoneId.systemDefault())}] $this"
