package com.gu.directoryauthorizer

import com.gu.directoryauthorizer.ServiceAccountDirectoryAPIClient.{GroupKey, MemberKey}
import org.scalatest.{AsyncFreeSpec, Matchers}

class GoogleDirectoryAPIClientSpec extends AsyncFreeSpec with Matchers with Stubs {

  "GoogleDirectoryAPIClient" - {
    "checks membership of multiple groups" in {
      val membership = Map(
        GroupKey("1") -> List("a", "b", "c").map(MemberKey),
        GroupKey("2") -> List("a", "d", "e", "f").map(MemberKey),
        GroupKey("3") -> List("a").map(MemberKey),
        GroupKey("4") -> List("f").map(MemberKey)
      )

      val client = stubDirectoryAPIClient(membership)

      client.memberOfAll(List("1", "2", "3").map(GroupKey), MemberKey("a")).map(_ should be(true))
      client.memberOfAll(List("1", "2", "3").map(GroupKey), MemberKey("b")).map(_ should be(false))
      client.memberOfAll(List("2", "4").map(GroupKey), MemberKey("f")).map(_ should be(true))
      client.memberOfAll(List("4").map(GroupKey), MemberKey("f")).map(_ should be(true))
    }
  }
}
