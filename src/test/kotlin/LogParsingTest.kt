package com.benasher44.kloudfrontblogstats

import com.benasher44.kloudfrontblogstats.logic.enumerateLogs
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class LogParsingTest {

    private data class LogRecord(
        val date: String,
        val time: String,
        val referer: String?,
        val userAgent: String?,
        val path: String
    )

    private val sampleLog = """
        #Version: 1.0
        #Fields: date time x-edge-location sc-bytes c-ip cs-method cs(Host) cs-uri-stem sc-status cs(Referer) cs(User-Agent) cs-uri-query cs(Cookie) x-edge-result-type x-edge-request-id x-host-header cs-protocol cs-bytes time-taken x-forwarded-for ssl-protocol ssl-cipher x-edge-response-result-type cs-protocol-version fle-status fle-encrypted-fields c-port time-to-first-byte x-edge-detailed-result-type sc-content-type sc-content-len sc-range-start sc-range-end
        2020-11-25	05:35:49	SIN52-C2	32396	0.0.0.0	GET	something.cloudfront.net	/img/logo.png	200	-	Mozilla/5.0%20(Macintosh;%20Intel%20Mac%20OS%20X%2010.15;%20rv:82.0)%20Gecko/20100101%20Firefox/82.0	-	-	Hit	dGVzdA==	benasher.co	https	244	0.003	-	TLSv1.3	TLS_AES_128_GCM_SHA256	Hit	HTTP/2.0	-	-	59724	0.003	Hit	image/png	32049	-	-
        2020-11-25	05:35:50	SIN52-C2	17700	0.0.0.0	GET	something.cloudfront.net	/favicon.ico	200	https://benasher.co/img/logo.png	Mozilla/5.0%20(Macintosh;%20Intel%20Mac%20OS%20X%2010.15;%20rv:82.0)%20Gecko/20100101%20Firefox/82.0	-	-	Hit	dGVzdA==	benasher.co	https	69	0.002	-	TLSv1.3	TLS_AES_128_GCM_SHA256	Hit	HTTP/2.0	-	-	59724	0.002	Hit	image/vnd.microsoft.icon	17363	-	-
        2020-11-25	05:39:29	AMS54-C1	5112	0.0.0.0	GET	something.cloudfront.net	/kotlin-binary-debugging/	200	-	Mozilla/5.0%20(compatible;%20AhrefsBot/7.0;%20+http://ahrefs.com/robot/)	-	-	Hit	dGVzdA==	benasher.co	https	113	0.012	-	TLSv1.3	TLS_AES_128_GCM_SHA256	Hit	HTTP/2.0	-	-	44862	0.011	Hit	text/html	-	-	-
    """.trimIndent()

    @Test
    fun `parses sample log`() {
        val logRecords = mutableListOf<LogRecord>()
        sampleLog.byteInputStream().enumerateLogs { date, time, referer, userAgent, path ->
            logRecords.add(
                LogRecord(date, time, referer, userAgent, path)
            )
        }
        val expectedLogRecords = listOf(
            LogRecord("2020-11-25", "05:35:49", null, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:82.0) Gecko/20100101 Firefox/82.0", "/img/logo.png"),
            LogRecord("2020-11-25", "05:35:50", "benasher.co/img/logo.png", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:82.0) Gecko/20100101 Firefox/82.0", "/favicon.ico"),
            LogRecord("2020-11-25", "05:39:29", null, "Mozilla/5.0 (compatible; AhrefsBot/7.0; +http://ahrefs.com/robot/)", "/kotlin-binary-debugging"),
        )
        assertEquals(expectedLogRecords, logRecords)
    }
}
