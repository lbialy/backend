package io.branchtalk.users.api

import io.branchtalk.api._
import io.branchtalk.api.AuthenticationSupport._
import io.branchtalk.shared.models.ID
import io.branchtalk.users.api.UserModels._
import io.branchtalk.users.model.User
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.json.jsoniter._

object UserAPIs {

  private val prefix = "users"

  private val errorMapping = oneOf[UserError](
    statusMapping[UserError.BadCredentials](StatusCode.Unauthorized, jsonBody[UserError.BadCredentials]),
    statusMapping[UserError.NoPermission](StatusCode.Unauthorized, jsonBody[UserError.NoPermission]),
    statusMapping[UserError.NotFound](StatusCode.NotFound, jsonBody[UserError.NotFound]),
    statusMapping[UserError.ValidationFailed](StatusCode.BadRequest, jsonBody[UserError.ValidationFailed]),
    statusDefaultMapping[UserError](jsonBody[UserError])
  )

  // TODO: confirm email endpoint
  // TODO: reset password endpoint
  // TODO: list/search users endpoint

  val signUp: Endpoint[SignUpRequest, UserError, SignUpResponse, Nothing] = endpoint
    .name("Sign up")
    .summary("Allows creation of User's account")
    .description("Scheduled User creation and returns future User's ID as well as future Session's handler")
    .tags(List(UsersTags.domain, UsersTags.users, UsersTags.sessions))
    .post
    .in(prefix / "sign_up")
    .in(jsonBody[SignUpRequest])
    .out(jsonBody[SignUpResponse])
    .errorOut(errorMapping)

  // TODO: consider returning user data besides session data
  val signIn: Endpoint[Authentication, UserError, SignInResponse, Nothing] = endpoint
    .name("Sign in")
    .summary("Allows logging into existing User's account")
    .description("Returns Session's handler")
    .tags(List(UsersTags.domain, UsersTags.sessions))
    .post
    .in(authHeader)
    .in(prefix / "sign_up")
    .out(jsonBody[SignInResponse])
    .errorOut(errorMapping)

  val signOut: Endpoint[Authentication, UserError, SignOutResponse, Nothing] = endpoint
    .name("Sign out")
    .summary("Destroys specific User's session")
    .description("Make the Session ID immediately invalid")
    .tags(List(UsersTags.domain, UsersTags.sessions))
    .delete
    .in(authHeader)
    .in(prefix / "sign_out")
    .out(jsonBody[SignOutResponse])
    .errorOut(errorMapping)

  val fetchProfile: Endpoint[ID[User], UserError, APIUser, Nothing] = endpoint
    .name("Fetch profile")
    .summary("Fetches specific User's profile")
    .description("Returns User's profile")
    .tags(List(UsersTags.domain, UsersTags.users))
    .get
    .in(prefix / path[ID[User]])
    .out(jsonBody[APIUser])
    .errorOut(errorMapping)

  val updateProfile: Endpoint[(Authentication, ID[User], UpdateUserRequest), UserError, UpdateUserResponse, Nothing] =
    endpoint
      .name("Update profile")
      .summary("Updates specific User's profile")
      .description("Schedules specific User's profile update, requires ownership or moderator status")
      .tags(List(UsersTags.domain, UsersTags.users))
      .put
      .in(authHeader)
      .in(prefix / path[ID[User]])
      .in(jsonBody[UpdateUserRequest])
      .out(jsonBody[UpdateUserResponse])
      .errorOut(errorMapping)

  val deleteProfile: Endpoint[(Authentication, ID[User]), UserError, DeleteUserResponse, Nothing] = endpoint
    .name("Delete profile")
    .summary("Deletes specific User's profile")
    .description("Schedules specific User's profile deletion, requires ownership or moderator status, cannot be undone")
    .tags(List(UsersTags.domain, UsersTags.users))
    .delete
    .in(authHeader)
    .in(prefix / path[ID[User]])
    .out(jsonBody[DeleteUserResponse])
    .errorOut(errorMapping)

  val endpoints: List[Endpoint[_, _, _, _]] = List(signUp, signIn, signOut, fetchProfile, updateProfile, deleteProfile)
}
