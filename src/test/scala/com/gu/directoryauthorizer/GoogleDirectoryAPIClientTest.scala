package com.gu.directoryauthorizer

import cats.Id
import com.gu.directoryauthorizer.ServiceAccountGoogleDirectoryAPIClient.{GroupKey, MemberKey}
import org.scalatest.{FreeSpec, Matchers}

class GoogleDirectoryAPIClientTest extends FreeSpec with Matchers {

  "GoogleDirectoryAPIClient" - {
    "checks membership of multiple groups" in {
      val membership = Map(
        GroupKey("1") -> List("a", "b", "c").map(MemberKey),
        GroupKey("2") -> List("a", "d", "e", "f").map(MemberKey),
        GroupKey("3") -> List("a").map(MemberKey),
        GroupKey("4") -> List("f").map(MemberKey)
      )

      val client = new TestDirectoryClient(membership)

      client.memberOfAll(List("1", "2", "3").map(GroupKey), MemberKey("a")) should be(true)
      client.memberOfAll(List("1", "2", "3").map(GroupKey), MemberKey("b")) should be(false)
      client.memberOfAll(List("2", "4").map(GroupKey), MemberKey("f")) should be(true)
      client.memberOfAll(List("4").map(GroupKey), MemberKey("f")) should be(true)
    }
  }

  class TestDirectoryClient(membership: Map[GroupKey, List[MemberKey]]) extends GoogleDirectoryAPIClient[Id] {

    override def hasMember(group: ServiceAccountGoogleDirectoryAPIClient.GroupKey,
                           member: ServiceAccountGoogleDirectoryAPIClient.MemberKey): Id[Boolean] =
      membership.get(group).exists(_.contains(member))
  }
}
