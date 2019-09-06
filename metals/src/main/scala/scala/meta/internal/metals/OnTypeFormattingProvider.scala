package scala.meta.internal.metals

import org.eclipse.lsp4j.{DocumentOnTypeFormattingParams, Range, TextEdit}

import scala.concurrent.{ExecutionContext, Future}
import scala.meta.inputs.Input
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.mtags.Semanticdbs
import scala.meta.tokens.Token
import scala.meta.tokens.Token.Constant
import scala.meta.tokens.Tokens

/*in order to use onTypeFormatting in vscode,
you'll have to set editor.formatOnType = true in settings*/
final class OnTypeFormattingProvider(
    semanticdbs: Semanticdbs,
    buffer: Buffers
)(implicit ec: ExecutionContext) {

  private val tripleQuote = """\u0022\u0022\u0022"""
  private val space = " "
  private val stripMargin = "stripMargin"

  private def isFinishedByStripMargin(
      stringTokenIndex: Int,
      tokens: Tokens
  ): Boolean = {
    var methodIndex = stringTokenIndex + 1

    while (tokens(methodIndex).isWhiteSpaceOrComment ||
      tokens(methodIndex).isInstanceOf[Token.Dot]) methodIndex += 1

    tokens(methodIndex) match {
      case token: Token.Ident if token.value == stripMargin =>
        true
      case other =>
        false
    }
  }

  private def indent(toInput: String, pos: meta.Position): String = {
    val beforePos = toInput.substring(0, pos.start)
    val lastPipe = beforePos.lastIndexOf("|")
    val lastNewline = beforePos.lastIndexOf("\n", lastPipe)
    val indent = beforePos.substring(beforePos.lastIndexOf("\n")).length
    val length = toInput.substring(lastNewline, lastPipe).length
    space * (length - indent)
  }

  private def isMultilineString(text: String, token: Token) = {
    text.substring(token.start, token.start + 3).equals(tripleQuote)
  }

  private def inToken(pos: meta.Position, token: Token): Boolean = {
    pos.start >= token.start && pos.end <= token.end
  }

  private def pipeInScope(pos: meta.Position, text: String): Boolean = {
    val firstNewline = text.substring(0, pos.start).lastIndexOf("\n")
    val lastNewline =
      text.substring(0, firstNewline).lastIndexOf("\n")
    text
      .substring(lastNewline + 1, pos.start)
      .contains("|")
  }

  def format(
      params: DocumentOnTypeFormattingParams
  ): Future[java.util.List[TextEdit]] = {
    val source = params.getTextDocument.getUri.toAbsolutePath
    val range = new Range(params.getPosition, params.getPosition)

    val edit = if (source.exists) {
      val sourceText = buffer.get(source).getOrElse("")
      val pos = params.getPosition.toMeta(
        Input.VirtualFile(source.toString(), sourceText)
      )
      if (pipeInScope(pos, sourceText)) {
        val tokens =
          Input.VirtualFile(source.toString(), sourceText).tokenize.toOption
        tokens.flatMap { tokens: Tokens =>
          val tokenIndex = tokens.indexWhere {
            case token: Constant.String =>
              inToken(pos, token) &&
                isMultilineString(sourceText, token)
            case _ => false
          }

          if (isFinishedByStripMargin(tokenIndex, tokens)) {
            Some(new TextEdit(range, indent(sourceText, pos) + "|"))
          } else {
            None
          }
        }
      } else None
    } else None
    Future.successful(edit.toList.asJava)
  }

}
