package io.branchtalk.discussions.api

import cats.effect.IO
import io.branchtalk.api.{ Authentication, Pagination, PaginationLimit, PaginationOffset, ServerIOTest }
import io.branchtalk.discussions.DiscussionsFixtures
import io.branchtalk.discussions.api.PostModels._
import io.branchtalk.discussions.model.{ Channel, Subscription }
import io.branchtalk.mappings._
import io.branchtalk.shared.models._
import io.branchtalk.users.UsersFixtures
import org.specs2.mutable.Specification
import sttp.model.StatusCode

final class SubscriptionServerSpec extends Specification with ServerIOTest with UsersFixtures with DiscussionsFixtures {

  private val defaultChannelID = ID[Channel](java.util.UUID.randomUUID())
  implicit protected lazy val uuidGenerator: TestUUIDGenerator =
    (new TestUUIDGenerator).tap(_.stubNext(defaultChannelID.uuid)) // stub generation in ServerIOTest resources

  "SubscriptionServer-provided endpoints" should {

    "on GET /discussions/subscriptions/newest" in {

      "return newest Posts for default Channels for signed-out User" in {
        (usersWrites.runProjector, discussionsWrites.runProjector).tupled.use {
          case (usersProjector, discussionsProjector) =>
            for {
              // given
              _ <- usersProjector.logError("Error reported by Users projector").start
              _ <- discussionsProjector.logError("Error reported by Discussions projector").start
              CreationScheduled(channelID) <- channelCreate
                .flatTap(_ => IO(uuidGenerator.stubNext(defaultChannelID.uuid))) // create Channel with default ID
                .flatMap(discussionsWrites.channelWrites.createChannel)
                .assert("Created Channel should have predefined ID")(_.id === defaultChannelID)
              _ <- discussionsReads.channelReads.requireById(channelID).eventually()
              postIDs <- (0 until 10).toList.traverse(_ =>
                postCreate(channelID).flatMap(discussionsWrites.postWrites.createPost).map(_.id)
              )
              posts <- postIDs.traverse(discussionsReads.postReads.requireById(_)).eventually()
              // when
              response1 <- SubscriptionAPIs.newest.toTestCall.untupled(None, None, PaginationLimit(5).some)
              response2 <- SubscriptionAPIs.newest.toTestCall.untupled(None,
                                                                       PaginationOffset(5L).some,
                                                                       PaginationLimit(5).some
              )
            } yield {
              // then
              response1.code must_=== StatusCode.Ok
              response1.body must beValid(beRight(anInstanceOf[Pagination[APIPost]]))
              response2.code must_=== StatusCode.Ok
              response2.body must beValid(beRight(anInstanceOf[Pagination[APIPost]]))
              (response1.body.toValidOpt.flatMap(_.toOption), response2.body.toValidOpt.flatMap(_.toOption))
                .mapN { (pagination1, pagination2) =>
                  (pagination1.entities.toSet ++ pagination2.entities.toSet) must_=== posts
                    .map(APIPost.fromDomain)
                    .toSet
                }
                .getOrElse(true must beTrue)
            }
        }
      }

      "return newest Posts for Channels subscribed by current User" in {
        (usersWrites.runProjector, discussionsWrites.runProjector).tupled.use {
          case (usersProjector, discussionsProjector) =>
            for {
              // given
              _ <- usersProjector.logError("Error reported by Users projector").start
              _ <- discussionsProjector.logError("Error reported by Discussions projector").start
              (CreationScheduled(userID), CreationScheduled(sessionID)) <- userCreate.flatMap(
                usersWrites.userWrites.createUser
              )
              _ <- usersReads.userReads.requireById(userID).eventually()
              _ <- usersReads.sessionReads.requireSession(sessionID).eventually()
              CreationScheduled(channelID) <- channelCreate.flatMap(discussionsWrites.channelWrites.createChannel)
              _ <- discussionsReads.channelReads.requireById(channelID).eventually()
              subscriberID = userIDUsers2Discussions.get(userID)
              _ <- discussionsWrites.subscriptionWrites.subscribe(
                Subscription.Subscribe(subscriberID = subscriberID, subscriptions = Set(channelID))
              )
              _ <- discussionsReads.subscriptionReads
                .requireForUser(subscriberID)
                .assert("Subscriptions should contain added Channel ID")(_.subscriptions(channelID))
                .eventually()
              postIDs <- (0 until 10).toList.traverse(_ =>
                postCreate(channelID).flatMap(discussionsWrites.postWrites.createPost).map(_.id)
              )
              posts <- postIDs.traverse(discussionsReads.postReads.requireById(_)).eventually()
              // when
              response1 <- SubscriptionAPIs.newest.toTestCall.untupled(
                Authentication.Session(sessionID = sessionIDApi2Users.reverseGet(sessionID)).some,
                None,
                PaginationLimit(5).some
              )
              response2 <- SubscriptionAPIs.newest.toTestCall.untupled(
                Authentication.Session(sessionID = sessionIDApi2Users.reverseGet(sessionID)).some,
                PaginationOffset(5L).some,
                PaginationLimit(5).some
              )
            } yield {
              // then
              response1.code must_=== StatusCode.Ok
              response1.body must beValid(beRight(anInstanceOf[Pagination[APIPost]]))
              response2.code must_=== StatusCode.Ok
              response2.body must beValid(beRight(anInstanceOf[Pagination[APIPost]]))
              (response1.body.toValidOpt.flatMap(_.toOption), response2.body.toValidOpt.flatMap(_.toOption))
                .mapN { (pagination1, pagination2) =>
                  (pagination1.entities.toSet ++ pagination2.entities.toSet) must_=== posts
                    .map(APIPost.fromDomain)
                    .toSet
                }
                .getOrElse(true must beTrue)
            }
        }
      }
    }
  }
}
