package com.michael.simplemusic.podcast

import android.util.Xml
import com.michael.simplemusic.data.PodcastEpisode
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class PodcastParser {
    private val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)

    fun parse(inputStream: InputStream, channelId: Int): List<PodcastEpisode> {
        inputStream.use {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(it, null)
            parser.nextTag()
            return readFeed(parser, channelId)
        }
    }

    private fun readFeed(parser: XmlPullParser, channelId: Int): List<PodcastEpisode> {
        val episodes = mutableListOf<PodcastEpisode>()

        parser.require(XmlPullParser.START_TAG, null, "rss")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name == "channel") {
                episodes.addAll(readChannel(parser, channelId))
            } else {
                skip(parser)
            }
        }
        return episodes
    }

    private fun readChannel(parser: XmlPullParser, channelId: Int): List<PodcastEpisode> {
        val episodes = mutableListOf<PodcastEpisode>()
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name == "item") {
                readEpisode(parser, channelId)?.let { episodes.add(it) }
            } else {
                skip(parser)
            }
        }
        return episodes
    }

    private fun readEpisode(parser: XmlPullParser, channelId: Int): PodcastEpisode? {
        var title = ""
        var description = ""
        var streamUrl = ""
        var pubDate: Long? = null
        var guid = ""

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "title" -> title = readText(parser)
                "description" -> description = readText(parser)
                "link" -> { /* skip */ skip(parser) }
                "enclosure" -> {
                    streamUrl = parser.getAttributeValue(null, "url") ?: ""
                    skip(parser)
                }
                "pubDate" -> {
                    val dateStr = readText(parser)
                    pubDate = try { dateFormat.parse(dateStr)?.time } catch (e: Exception) { null }
                }
                "guid" -> guid = readText(parser)
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
            guid = guid
        )
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
