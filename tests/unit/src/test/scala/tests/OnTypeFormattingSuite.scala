package tests

object OnTypeFormattingSuite extends BaseSlowSuite("onTypeFormatting") {

  val tripleQuote = """\u0022\u0022\u0022"""

  check(
    "correct-string",
    s"""
       |object Main {
       |  val str = '''
       |  #@@word
       |  '''.stripMargin
       |}""".stripMargin,
    s"""
       |object Main {
       |  val str = '''
       |  #
       |  #word
       |  '''.stripMargin
       |}""".stripMargin
  )

  check(
    "correct-no-dot",
    s"""
       |object Main {
       |  val str = '''
       |  #@@word
       |  ''' stripMargin
       |}""".stripMargin,
    s"""
       |object Main {
       |  val str = '''
       |  #
       |  #word
       |  ''' stripMargin
       |}""".stripMargin
  )

  check(
    "after-string",
    s"""
       |object Main {
       |val a = '''
       |# this is
       |# a multiline
       |# string
       |'''.stripMargin@@
       |}""".stripMargin,
    s"""
       |object Main {
       |val a = '''
       |# this is
       |# a multiline
       |# string
       |'''.stripMargin
       |
       |}""".stripMargin
  )

  check(
    "no-pipe-string",
    s"""
       |object Main {
       |  val abc = 123
       |  val s = ''' example
       |  word@@'''.stripMargin
       |  abc.toInt
       |}""".stripMargin,
    s"""
       object Main {
       |  val abc = 123
       |  val s = ''' example
       |  word
       |  '''.stripMargin
       |  abc.toInt
       |}""".stripMargin
  )

  check(
    "far-indent-string",
    s"""
       |object Main {
       |  val str = '''#@@
       |'''.stripMargin
       |}""".stripMargin,
    s"""
       |object Main {
       |  val str = '''#
       |               #
       |'''.stripMargin
       |}""".stripMargin
  )

  def check(name: String, testCase: String, expectedCase: String): Unit = {
    def unmangle(string: String): String =
      string
        .replaceAll("#", "|")
        .replaceAll("'''", tripleQuote)

    val test = unmangle(testCase)
    val base = test.replaceAll("(@@)", "")
    val expected = unmangle(expectedCase)
    testAsync(name) {
      for {
        _ <- server.initialize(
          s"""/metals.json
             |{"a":{}}
             |/a/src/main/scala/a/Main.scala
      """.stripMargin + base
        )
        _ <- server.didOpen("a/src/main/scala/a/Main.scala")
        _ <- server.onTypeFormatting(
          "a/src/main/scala/a/Main.scala",
          test, // bez @@
          expected
        )
      } yield ()
    }
  }
}
