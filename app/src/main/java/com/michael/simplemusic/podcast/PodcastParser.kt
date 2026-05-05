package com.michael.simplemusic.podcast

import android.util.Xml
import com.michael.simplemusic.data.PodcastEpisode
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class PodcastParser {
    private val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)

    fun parse(inputStream: InputStream, channelId: Int, sinceDate: Long? = null): Pair<String, List<PodcastEpisode>> {
        inputStream.use {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(it, null)
            parser.nextTag()
            return readFeed(parser, channelId, sinceDate)
        }
    }

    private fun readFeed(parser: XmlPullParser, channelId: Int, sinceDate: Long?): Pair<String, List<PodcastEpisode>> {
        var title = ""
        var episodes = listOf<PodcastEpisode>()

        parser.require(XmlPullParser.START_TAG, null, "rss")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name == "channel") {
                val result = readChannel(parser, channelId, sinceDate)
                title = result.first
                episodes = result.second
            } else {
                skip(parser)
            }
        }
        return Pair(title, episodes)
    }

    private fun readChannel(parser: XmlPullParser, channelId: Int, sinceDate: Long?): Pair<String, List<PodcastEpisode>> {
        val episodes = mutableListOf<PodcastEpisode>()
        var podcastTitle = ""
        var stopParsing = false
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "title" -> podcastTitle = readText(parser)
                "item" -> {
                    if (!stopParsing) {
                        val episode = readEpisode(parser, channelId, podcastTitle)
                        if (episode != null) {
                            if (sinceDate != null && (episode.pubDate ?: 0L) < sinceDate) {
                                stopParsing = true
                            } else {
                                episodes.add(episode)
                            }
                        }
                    } else {
                        skip(parser)
                    }
                }
                else -> skip(parser)
            }
        }
        return Pair(podcastTitle, episodes)
    }

    private fun readEpisode(parser: XmlPullParser, channelId: Int, podcastTitle: String): PodcastEpisode? {
        var title = ""
        var description = ""
        var streamUrl = ""
        var pubDate: Long? = null
        var guid = ""
        var durationMs = 0L

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "title" -> title = readText(parser)
                "description" -> description = readText(parser)
                "link" -> { skip(parser) }
                "enclosure" -> {
                    streamUrl = parser.getAttributeValue(null, "url") ?: ""
                    skip(parser)
                }
                "pubDate" -> {
                    val dateStr = readText(parser)
                    pubDate = try { dateFormat.parse(dateStr)?.time } catch (e: Exception) { null }
                }
                "guid" -> guid = readText(parser)
                "itunes:duration" -> {
                    val durationStr = readText(parser)
                    durationMs = parseDuration(durationStr)
                }
                else -> skip(parser)
            }
        }

        if (streamUrl.isBlank()) return null
        if (guid.isBlank()) guid = streamUrl

        return PodcastEpisode(
            channelId = channelId,
            title = title,
            description = description,
            streamUrl = streamUrl,
            pubDate = pubDate,
            guid = guid,
            podcastTitle = podcastTitle,
            durationMs = durationMs
        )
    }

    private fun parseDuration(duration: String): Long {
        return try {
            val parts = duration.split(":")
            when (parts.size) {
                3 -> { // HH:MM:SS
                    val h = parts[0].toLong()
                    val m = parts[1].toLong()
                    val s = parts[2].toLong()
                    (h * 3600 + m * 60 + s) * 1000
                }
                2 -> { // MM:SS
                    val m = parts[0].toLong()
                    val s = parts[1].toLong()
                    (m * 60 + s) * 1000
                }
                1 -> { // SS or total seconds
                    parts[0].toLong() * 1000
                }
                else -> 0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }
        return result
    }

    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) {
            throw IllegalStateException()
        }
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }
}
