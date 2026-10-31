package au.howesthat.whirl.feature.news

import au.howesthat.whirl.core.model.NewsItem
import au.howesthat.whirl.core.model.TextQuery
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.test.assertEquals

class NewsTest {
    private fun item(id: Long, date: String) = NewsItem(id, "T$id", "S", "", OffsetDateTime.parse(date))

    @Test
    fun `groups by local day, newest first`() {
        val sydney = ZoneId.of("Australia/Sydney")
        val days = groupByDay(
            listOf(
                item(1, "2026-10-19T08:00:00+10:00"),
                item(2, "2026-10-20T09:00:00+10:00"),
                // 23:30 UTC on the 19th is the 20th in Sydney (AEDT, +11).
                item(3, "2026-10-19T23:30:00Z"),
            ),
            sydney,
        )
        assertEquals(listOf(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 19)), days.map { it.date })
        assertEquals(listOf(3L, 2L), days[0].items.map { it.id })
    }

    @Test
    fun `articles open through Whirlpool's redirect`() {
        assertEquals("https://whirlpool.net.au/news/go.cfm?article=123", newsUrl(item(123, "2026-10-19T08:00:00+10:00")))
    }

    @Test
    fun `find matches title, source or blurb and drops empty days`() {
        val day1 = NewsDay(LocalDate.of(2026, 10, 20), listOf(NewsItem(1, "Telstra outage", "iTnews", "", OffsetDateTime.parse("2026-10-20T09:00:00+10:00"))))
        val day2 = NewsDay(LocalDate.of(2026, 10, 19), listOf(NewsItem(2, "Prices rise", "ABC", "Optus and Telstra", OffsetDateTime.parse("2026-10-19T09:00:00+10:00"))))
        val days = listOf(day1, day2)
        assertEquals(listOf(1L, 2L), filterNews(days, TextQuery("telstra")).flatMap { d -> d.items.map { it.id } })
        assertEquals(listOf(2L), filterNews(days, TextQuery("abc optus")).flatMap { d -> d.items.map { it.id } }, "source and blurb")
        assertEquals(listOf(LocalDate.of(2026, 10, 20)), filterNews(days, TextQuery("itnews")).map { it.date })
    }
}
