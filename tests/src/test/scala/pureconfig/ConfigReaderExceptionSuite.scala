/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package pureconfig

import com.typesafe.config._
import java.net.URL
import java.nio.file.Paths
import org.scalatest._
import shapeless._

import pureconfig.error._
import pureconfig.generic.auto._
import pureconfig.generic.error._
import pureconfig.generic.hlist._
import pureconfig.syntax._

class ConfigReaderExceptionSuite extends FlatSpec with Matchers {
  behavior of "ConfigReaderException"

  case class Conf(a: Int, b: String, c: Int)

  it should "have a message with failures organized by path" in {
    val conf = ConfigFactory.parseString("""
      {
        a = "string"
      }
    """)

    val exception = intercept[ConfigReaderException[_]] {
      conf.toOrThrow[Conf]
    }

    exception.failures.toList.toSet shouldBe Set(
      ConvertFailure(WrongType(ConfigValueType.STRING, Set(ConfigValueType.NUMBER)), None, "a"),
      ConvertFailure(KeyNotFound("b", Set()), None, ""),
      ConvertFailure(KeyNotFound("c", Set()), None, ""))

    exception.getMessage shouldBe
      s"""|Cannot convert configuration to a pureconfig.ConfigReaderExceptionSuite$$Conf. Failures are:
          |  at the root:
          |    - Key not found: 'b'.
          |    - Key not found: 'c'.
          |  at 'a':
          |    - Expected type NUMBER. Found STRING instead.
          |""".stripMargin
  }

  case class ParentConf(conf: Conf)

  it should "include failures that occur at the root of the configuration" in {
    val conf = ConfigFactory.parseString("""
      {
        conf = 2
      }
    """)

    val exception1 = intercept[ConfigReaderException[_]] {
      conf.root().get("conf").toOrThrow[Conf]
    }

    exception1.failures.toList.toSet shouldBe Set(
      ConvertFailure(WrongType(ConfigValueType.NUMBER, Set(ConfigValueType.OBJECT)), None, ""))

    val exception2 = intercept[ConfigReaderException[_]] {
      conf.root().toOrThrow[ParentConf]
    }

    exception2.failures.toList.toSet shouldBe Set(
      ConvertFailure(WrongType(ConfigValueType.NUMBER, Set(ConfigValueType.OBJECT)), None, "conf"))
  }

  case class MapConf(values: Map[String, MapConf])

  it should "include failures with the full error path" in {
    val conf = ConfigFactory.parseString("""
      {
        values {
          a {
            values {
              c = 2
              d {
                values = {}
              }
            }
          }
          b = "str"
        }
      }
    """)

    val exception = intercept[ConfigReaderException[_]] {
      conf.root().toOrThrow[MapConf]
    }

    exception.failures.toList.toSet shouldBe Set(
      ConvertFailure(WrongType(ConfigValueType.STRING, Set(ConfigValueType.OBJECT)), None, "values.b"),
      ConvertFailure(WrongType(ConfigValueType.NUMBER, Set(ConfigValueType.OBJECT)), None, "values.a.values.c"))
  }

  sealed trait A
  case class A1(a: Int) extends A
  case class A2(a: String) extends A
  case class EnclosingA(values: Map[String, A])

  it should "include failures relevant for coproduct derivation" in {
    val conf = ConfigFactory.parseString("""
      {
        values {
          v1 {
            type = "unexpected"
            a = 2
          }
          v2 {
            type = "a-2"
            a = "val"
          }
          v3 {
            a = 5
          }
        }
      }
    """)

    val exception = intercept[ConfigReaderException[_]] {
      conf.root().toOrThrow[EnclosingA]
    }

    exception.failures.toList.toSet shouldBe Set(
      ConvertFailure(UnexpectedValueForFieldCoproductHint(ConfigValueFactory.fromAnyRef("unexpected")), None, "values.v1.type"),
      ConvertFailure(KeyNotFound("type", Set()), None, "values.v3"))
  }

  case class CamelCaseConf(camelCaseInt: Int, camelCaseString: String)
  case class KebabCaseConf(kebabCaseInt: Int, kebabCaseString: String)
  case class SnakeCaseConf(snakeCaseInt: Int, snakeCaseString: String)
  case class EnclosingConf(
      camelCaseConf: CamelCaseConf,
      kebabCaseConf: KebabCaseConf,
      snakeCaseConf: SnakeCaseConf)

  it should "include candidate keys in case of a suspected misconfigured ProductHint" in {
    val conf = ConfigFactory.parseString("""{
      camel-case-conf {
        camelCaseInt = 2
        camelCaseString = "str"
      }
      kebab-case-conf {
        kebab-case-int = 2
        kebab-case-string = "str"
      }
      snake-case-conf {
        snake_case_int = 2
        snake_case_string = "str"
      }
    }""")

    val exception = intercept[ConfigReaderException[_]] {
      conf.root().toOrThrow[EnclosingConf]
    }

    exception.failures.toList.toSet shouldBe Set(
      ConvertFailure(KeyNotFound("camel-case-int", Set("camelCaseInt")), None, "camel-case-conf"),
      ConvertFailure(KeyNotFound("camel-case-string", Set("camelCaseString")), None, "camel-case-conf"),
      ConvertFailure(KeyNotFound("snake-case-int", Set("snake_case_int")), None, "snake-case-conf"),
      ConvertFailure(KeyNotFound("snake-case-string", Set("snake_case_string")), None, "snake-case-conf"))
  }

  it should "have failures with the proper file system location of the values that raised errors, if available" in {
    val workingDir = getClass.getResource("/").getFile
    val file = "conf/configFailureLocation/single/a.conf"
    val url = new URL("file://" + workingDir + file)
    val conf = ConfigFactory.load(file).root()

    val exception = intercept[ConfigReaderException[_]] {
      conf.get("conf").toOrThrow[Conf]
    }

    exception.failures.toList.toSet shouldBe Set(
      ConvertFailure(KeyNotFound("a", Set()), Some(ConfigValueLocation(url, 1)), ""),
      ConvertFailure(WrongType(ConfigValueType.STRING, Set(ConfigValueType.NUMBER)), Some(ConfigValueLocation(url, 3)), "c"))
  }

  it should "include failures regarding the inability to parse a given configuration" in {
    val workingDir = getClass.getResource("/").getFile
    val file = "conf/malformed/a.conf"
    val url = new URL("file://" + workingDir + file)

    val exception = intercept[ConfigReaderException[_]] {
      ConfigSource.file(Paths.get(workingDir, file)).loadOrThrow[Conf]
    }

    exception.failures.toList.toSet shouldBe Set(
      CannotParse("Expecting close brace } or a comma, got end of file", Some(ConfigValueLocation(url, 2))))
  }

  it should "include failures indicating that a given file does not exist" in {
    val workingDir = getClass.getResource("/").getFile
    val file = "conf/nonexisting"

    val exception = intercept[ConfigReaderException[_]] {
      ConfigSource.file(Paths.get(workingDir, file)).loadOrThrow[Conf]
    }

    // Note: exceptions can't be compared for equality
    exception.failures.toList.toString shouldBe
      s"List(CannotReadFile(${workingDir}${file},Some(java.io.FileNotFoundException: ${workingDir}${file} (No such file or directory))))"
  }

  case class HListAndTupleConf(hlist: Int :: Int :: String :: HNil, tuple: (Int, Int, String))

  it should "include failures showing lists of wrong size" in {
    val conf = ConfigFactory.parseString("""
      {
        hlist = [1, 2, "three", 4]
        tuple = [1, 2, "three", 4, 5, 6]
      }
    """)

    val exception = intercept[ConfigReaderException[_]] {
      conf.root().toOrThrow[HListAndTupleConf]
    }

    exception.failures.toList.toSet shouldBe Set(
      ConvertFailure(WrongSizeList(3, 4), None, "hlist"),
      ConvertFailure(WrongSizeList(3, 6), None, "tuple"))
  }
}
