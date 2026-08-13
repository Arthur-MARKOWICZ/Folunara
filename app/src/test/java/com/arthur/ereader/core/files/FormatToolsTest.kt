package com.arthur.ereader.core.files
import com.arthur.ereader.domain.model.BookFormat
import org.junit.Assert.assertEquals
import org.junit.Test
class FormatToolsTest {
 @Test fun `recognizes supported extensions ignoring case`() { assertEquals(BookFormat.PDF,FormatTools.fromName("Manual.PDF")); assertEquals(BookFormat.EPUB,FormatTools.fromName("book.epub")); assertEquals(null,FormatTools.fromName("book.mobi")) }
 @Test fun `sorts comic pages naturally`() { assertEquals(listOf("1.jpg","2.jpg","10.jpg"),FormatTools.naturalSort(listOf("10.jpg","2.jpg","1.jpg"))) }
 @Test fun `recognizes supported mime when file has no extension`() {
  assertEquals(BookFormat.PDF, FormatTools.detect("documento", "application/pdf"))
  assertEquals(BookFormat.EPUB, FormatTools.detect("livro", "application/epub+zip"))
  assertEquals(BookFormat.CBZ, FormatTools.detect("quadrinho", "application/vnd.comicbook+zip"))
 }
 @Test fun `detects cbr by extension or mime`() {
  assertEquals(true, FormatTools.isCbr("quadrinho.CBR", null))
  assertEquals(true, FormatTools.isCbr("quadrinho", "application/vnd.comicbook-rar"))
  assertEquals(false, FormatTools.isCbr("quadrinho.cbz", "application/vnd.comicbook+zip"))
 }
}
