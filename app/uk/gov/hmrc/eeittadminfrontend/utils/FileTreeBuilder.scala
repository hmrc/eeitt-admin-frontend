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

import uk.gov.hmrc.eeittadminfrontend.models.FileVerification
import scala.collection.mutable

object FileTreeBuilder {

  private case class TreeNode(
    name: String,
    isFolder: Boolean,
    children: mutable.Map[String, TreeNode] = mutable.Map(),
    files: List[FileVerification] = List()
  )

  private def formatBytes(bytes: Long): String =
    if (bytes <= 0) "0b"
    else {
      val k = 1024L
      val sizes = Array("&nbsp;B", "KB", "MB", "GB")
      val i = (Math.log(bytes.toDouble) / Math.log(k.toDouble)).toInt
      s"${Math.round((bytes / Math.pow(k.toDouble, i.toDouble)) * 100) / 100} ${sizes(i)}"
    }

  private def buildTree(files: List[FileVerification]): TreeNode = {
    val root = TreeNode("root", true)

    files.foreach { file =>
      val pathParts = file.directory.split("/").filter(_.nonEmpty)
      var currentNode = root

      pathParts.foreach { part =>
        if (!currentNode.children.contains(part)) {
          currentNode.children(part) = TreeNode(part, true)
        }
        currentNode = currentNode.children(part)
      }

      if (!currentNode.children.contains(file.fileName)) {
        currentNode.children(file.fileName) = TreeNode(file.fileName, false, files = List(file))
      }
    }

    root
  }

  private def renderTree(node: TreeNode, level: Int = 0): String = {
    val items = node.children.values.toList.sortBy { node =>
      if (node.isFolder) s"1${node.name}" else s"0${node.name}"
    }
    val html = new StringBuilder

    if (items.nonEmpty) {
      html.append("<ul>\n")
      items.foreach { child =>
        if (child.isFolder) {
          html.append(
            s"""<li><div class="details-layout"><div><i class="folder-open"></i>${child.name}</div></div>\n"""
          )
          html.append(renderTree(child, level + 1))
          html.append("</li>\n")
        } else {
          val file = child.files.headOption
          val exists = if (file.exists(_.exists)) "exists" else "not-exists"
          val highlight = if (file.exists(_.source == "workItem")) "not-sent" else ""
          val modified = file.flatMap(_.lastModified).map(ts => s"<span>${ts.toString}</span>").getOrElse("")
          val sizeStr = file.flatMap(_.contentLength).map(size => s"<span>${formatBytes(size)}</span>").getOrElse("")
          html.append(
            s"""<li><div class="details-layout"><div class="$highlight">${child.name}</div><div>$modified</div><div>$sizeStr</div><div class="$exists">&nbsp;</div></div></li>"""
          )
        }
      }
      html.append("</ul>\n")
    }

    html.toString
  }

  def buildTreeHtml(files: List[FileVerification]): String = {
    val tree = buildTree(files)
    renderTree(tree)
  }
}
