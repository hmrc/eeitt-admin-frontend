/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.eeittadminfrontend.utils

import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import play.twirl.api.Html

import scala.collection.MapView
object MarkDownUtil {

  case class UnicodeDescMapping(code: Char, desc: String, mapping: String)

  private val INVISIBLE_UNICODE_DESC_MAPPING: Seq[UnicodeDescMapping] = Seq(
    UnicodeDescMapping('\u2000', "En Quad", " "),
    UnicodeDescMapping('\u2001', "Em Quad", " "),
    UnicodeDescMapping('\u2002', "En Space", " "),
    UnicodeDescMapping('\u2003', "Em Space", " "),
    UnicodeDescMapping('\u2004', "Three-Per-Em Space", " "),
    UnicodeDescMapping('\u2005', "Four-Per-Em Space", " "),
    UnicodeDescMapping('\u2006', "Six-Per-Em Space", " "),
    UnicodeDescMapping('\u2007', "Figure Space", " "),
    UnicodeDescMapping('\u2008', "Punctuation Space", " "),
    UnicodeDescMapping('\u2009', "Thin Space", " "),
    UnicodeDescMapping('\u200A', "Hair Space", " "),
    UnicodeDescMapping('\u200B', "Zero-Width Space", ""),
    UnicodeDescMapping('\u200C', "Zero Width Non-Joiner", ""),
    UnicodeDescMapping('\u200D', "Zero Width Joiner", ""),
    UnicodeDescMapping('\u200E', "Left-To-Right Mark", ""),
    UnicodeDescMapping('\u200F', "Right-To-Left Mark", ""),
    UnicodeDescMapping('\u202A', "Left-to-Right Embedding", ""),
    UnicodeDescMapping('\u202B', "Right-to-Left Embedding", ""),
    UnicodeDescMapping('\u202C', "Pop Directional Formatting (PDF)", ""),
    UnicodeDescMapping('\u202D', "Left-to-Right Override (LRO)", ""),
    UnicodeDescMapping('\u202E', "Right-to-Left Override (RLO)", ""),
    UnicodeDescMapping('\u202F', "Narrow No-Break Space", " "),
    UnicodeDescMapping('\u2061', "Function Application", ""),
    UnicodeDescMapping('\u2062', "Invisible Times", ""),
    UnicodeDescMapping('\u2063', "Invisible Separator", ""),
    UnicodeDescMapping('\u2064', "Invisible Plus", ""),
    UnicodeDescMapping('\u2066', "Left-to-Right Isolate (LRI)", ""),
    UnicodeDescMapping('\u2067', "Right-to-Left Isolate (RLI)", ""),
    UnicodeDescMapping('\u2068', "First Strong Isolate (FSI)", ""),
    UnicodeDescMapping('\u2069', "Pop Directional Isolate (PDI)", ""),
    UnicodeDescMapping('\u206A', "Inhibit Symmetric Swapping", ""),
    UnicodeDescMapping('\u206B', "Activate Symmetric Swapping", ""),
    UnicodeDescMapping('\u206C', "Inhibit Arabic Form Shaping", ""),
    UnicodeDescMapping('\u206D', "Activate Arabic Form Shaping", ""),
    UnicodeDescMapping('\u206E', "National Digit Shapes", ""),
    UnicodeDescMapping('\u206F', "Nominal Digit Shapes", ""),
    UnicodeDescMapping('\uFEFF', "Byte order mark", ""),
    UnicodeDescMapping('\u0000', "Null", ""),
    UnicodeDescMapping('\u0001', "Start Of Heading", ""),
    UnicodeDescMapping('\u0002', "Start Of Text", ""),
    UnicodeDescMapping('\u0003', "End Of Text", ""),
    UnicodeDescMapping('\u0004', "End Of Transmission", ""),
    UnicodeDescMapping('\u0005', "Enquiry", ""),
    UnicodeDescMapping('\u0006', "Acknowledge", ""),
    UnicodeDescMapping('\u0007', "Bell", ""),
    UnicodeDescMapping('\u0008', "Backspace", ""),
    UnicodeDescMapping('\u0009', "Character Tabulation", ""),
    UnicodeDescMapping('\u000B', "Line Tabulation", ""),
    UnicodeDescMapping('\u000C', "Form Feed (FF)", ""),
    UnicodeDescMapping('\u000E', "Shift Out", ""),
    UnicodeDescMapping('\u0010', "Data Link Escape", ""),
    UnicodeDescMapping('\u0011', "Device Control One", ""),
    UnicodeDescMapping('\u0012', "Device Control Two", ""),
    UnicodeDescMapping('\u0013', "Device Control Three", ""),
    UnicodeDescMapping('\u0014', "Device Control Four", ""),
    UnicodeDescMapping('\u0015', "Negative Acknowledge", ""),
    UnicodeDescMapping('\u0016', "Synchronous Idle", ""),
    UnicodeDescMapping('\u0017', "End Of Transmission Block", ""),
    UnicodeDescMapping('\u0018', "Cancel", ""),
    UnicodeDescMapping('\u0019', "End Of Medium", ""),
    UnicodeDescMapping('\u001A', "Substitute", ""),
    UnicodeDescMapping('\u001B', "Escape", ""),
    UnicodeDescMapping('\u001C', "Information Separator Four", ""),
    UnicodeDescMapping('\u001D', "Information Separator Three", ""),
    UnicodeDescMapping('\u001E', "Information Separator Two", ""),
    UnicodeDescMapping('\u001F', "Information Separator One", "")
  )

  private val INVISIBLE_CHAR_MAP: MapView[Char, UnicodeDescMapping] =
    INVISIBLE_UNICODE_DESC_MAPPING.groupBy(_.code).view.mapValues(_.head)
  def replaceInvisibleChars(input: String): String =
    input.toCharArray.map(c => INVISIBLE_CHAR_MAP.get(c).map(_.mapping).getOrElse(c.toString)).mkString("")

  private val markdownControlCharacters =
    List("\\", "/", "`", "*", "_", "{", "}", "[", "]", "(", ")", "#", "+", "-", ".", "!")

  def markDownParser(markDownText0: String): Html = {
    val markDownText = markDownText0.trim.replaceAll(" +", " ")
    val flavour = new GFMFlavourDescriptor
    val parsedTree = new MarkdownParser(flavour).buildMarkdownTreeFromString(markDownText)
    val html = new HtmlGenerator(markDownText, parsedTree, flavour, false).generateHtml
    Html(unescapeMarkdownHtml(html))
  }

  def unescapeMarkdownHtml(html: String): String = {
    val unescapeHtml = markdownControlCharacters.foldLeft(html) { case (acc, specialChar) =>
      acc.replace("\\" + specialChar, specialChar)
    }
    replaceInvisibleChars(unescapeHtml)
  }

}
