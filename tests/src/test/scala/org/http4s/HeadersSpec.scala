/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import cats.kernel.laws.discipline.{EqTests, MonoidTests}
import org.http4s.headers._
import org.typelevel.ci.CIString

class HeadersSpec extends Http4sSpec {
  val clength = `Content-Length`.unsafeFromLong(10)
  val raw = Header.Raw(CIString("raw-header"), "Raw value")

  val base = Headers.of(clength.toRaw, raw)

  "Headers" should {
    "Not find a header that isn't there" in {
      base.get(`Content-Base`) should beNone
    }

    "Find an existing header and return its parsed form" in {
      base.get(`Content-Length`) should beSome(clength)
      base.get(CIString("raw-header")) should beSome(raw)
    }

    "Replaces headers" in {
      val newlen = `Content-Length`.zero
      base.put(newlen).get(newlen.key) should beSome(newlen)
      base.put(newlen.toRaw).get(newlen.key) should beSome(newlen)
    }

    "also find headers created raw" in {
      val headers = Headers.of(
        org.http4s.headers.`Cookie`(RequestCookie("foo", "bar")),
        Header("Cookie", RequestCookie("baz", "quux").toString)
      )
      headers.get(org.http4s.headers.Cookie).map(_.values.length) must beSome(2)
    }

    "Find the headers with DefaultHeaderKey keys" in {
      val headers = Headers.of(
        `Set-Cookie`(ResponseCookie("foo", "bar")),
        Header("Accept-Patch", ""),
        Header("Access-Control-Allow-Credentials", "")
      )
      headers.get(`Access-Control-Allow-Credentials`).map(_.value) must beSome("")
    }

    "Remove duplicate headers which are not of type Recurring on concatenation (++)" in {
      val hs = Headers.of(clength) ++ Headers.of(clength)
      hs.toList.length must_== 1
      hs.toList.head must_== clength
    }

    "Allow multiple Set-Cookie headers" in {
      val h1 = `Set-Cookie`(ResponseCookie("foo1", "bar1")).toRaw
      val h2 = `Set-Cookie`(ResponseCookie("foo2", "bar2")).toRaw
      val hs = Headers.of(clength) ++ Headers.of(h1) ++ Headers.of(h2)
      hs.toList.count(_.parsed match { case `Set-Cookie`(_) => true; case _ => false }) must_== 2
      hs.exists(_ == clength) must_== true
    }

    "Work with Raw headers (++)" in {
      val foo = ContentCoding.unsafeFromString("foo")
      val bar = ContentCoding.unsafeFromString("bar")
      val h1 = `Accept-Encoding`(foo).toRaw
      val h2 = `Accept-Encoding`(bar).toRaw
      val hs = Headers.of(clength.toRaw) ++ Headers.of(h1) ++ Headers.of(h2)
      hs.get(`Accept-Encoding`) must beSome(`Accept-Encoding`(foo, bar))
      hs.exists(_ == clength) must_== true
    }

    // "Avoid making copies if there are duplicate collections" in {
    //   base ++ Headers.empty eq base must_== true
    //   Headers.empty ++ base eq base must_== true
    // }

    "Preserve original headers when processing" in {
      val rawAuth = Header("Authorization", "test this")

      // Mapping to strings because Header equality is based on the *parsed* version
      (Headers.of(rawAuth) ++ base).toList.map(_.toString) must contain(===(rawAuth.toString))
    }

    "hash the same when constructed with the same contents" in {
      val h1 = Headers.of(Header("Test-Header", "Value"))
      val h2 = Headers.of(Header("Test-Header", "Value"))
      val h3 = Headers(List(Header("Test-Header", "Value"), Header("TestHeader", "other value")))
      val h4 = Headers(List(Header("TestHeader", "other value"), Header("Test-Header", "Value")))
      val h5 = Headers(List(Header("Test-Header", "Value"), Header("TestHeader", "other value")))
      h1.hashCode() must_== h2.hashCode()
      h1.equals(h2) must_== true
      h2.equals(h1) must_== true
      h1.equals(h3) must_== false
      h3.equals(h4) must_== false
      h3.equals(h5) must_== true
    }
  }

  checkAll("Monoid[Headers]", MonoidTests[Headers].monoid)
  checkAll("Eq[Headers]", EqTests[Headers].eqv)
}
