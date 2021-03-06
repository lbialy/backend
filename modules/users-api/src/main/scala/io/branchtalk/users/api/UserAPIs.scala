package io.branchtalk.users.api

import io.branchtalk.api._
import io.branchtalk.api.AuthenticationSupport._
import io.branchtalk.api.TapirSupport._
import io.branchtalk.shared.model.{ ID, OptionUpdatable, Updatable }
import io.branchtalk.users.api.UserModels._
import io.branchtalk.users.model.Password.{ Raw => RawPassword }
import io.branchtalk.users.model.User
import sttp.model.StatusCode
import sttp.tapir._

object UserAPIs {

  private val prefix = "users"

  private val errorMapping = oneOf[UserError](
    statusMapping[UserError.BadCredentials](StatusCode.Unauthorized, jsonBody[UserError.BadCredentials]),
    statusMapping[UserError.NoPermission](StatusCode.Unauthorized, jsonBody[UserError.NoPermission]),
    statusMapping[UserError.NotFound](StatusCode.NotFound, jsonBody[UserError.NotFound]),
    statusMapping[UserError.ValidationFailed](StatusCode.BadRequest, jsonBody[UserError.ValidationFailed])
  )

  // TODO: paginate users

  val signUp: Endpoint[SignUpRequest, UserError, SignUpResponse, Any] = endpoint
    .name("Sign up")
    .summary("Allows creation of User's account")
    .description("Schedules User creation and returns future User's ID as well as future Session's handler")
    .tags(List(UsersTags.domain, UsersTags.users, UsersTags.sessions))
    .post
    .in(prefix)
    .in(jsonBody[SignUpRequest])
    .out(jsonBody[SignUpResponse])
    .errorOut(errorMapping)

  val signIn: AuthedEndpoint[Authentication, UserError, SignInResponse, Any] = endpoint
    .name("Sign in")
    .summary("Allows logging into existing User's account")
    .description("Returns Session's handler")
    .tags(List(UsersTags.domain, UsersTags.sessions))
    .post
    .in(authHeader)
    .in(prefix / "session")
    .out(jsonBody[SignInResponse])
    .errorOut(errorMapping)
    .notRequiringPermissions

  val signOut: AuthedEndpoint[Authentication, UserError, SignOutResponse, Any] = endpoint
    .name("Sign out")
    .summary("Destroys specific User's session")
    .description("Make the Session ID immediately invalid")
    .tags(List(UsersTags.domain, UsersTags.sessions))
    .delete
    .in(authHeader)
    .in(prefix / "session")
    .out(jsonBody[SignOutResponse])
    .errorOut(errorMapping)
    .notRequiringPermissions

  val fetchProfile: AuthedEndpoint[(Option[Authentication], ID[User]), UserError, APIUser, Any] = endpoint
    .name("Fetch profile")
    .summary("Fetches specific User's profile")
    .description("Returns User's profile")
    .tags(List(UsersTags.domain, UsersTags.users))
    .get
    .in(optAuthHeader)
    .in(prefix / path[ID[User]].name("userID"))
    .out(jsonBody[APIUser])
    .errorOut(errorMapping)
    .notRequiringPermissions

  val updateProfile: AuthedEndpoint[(Authentication, ID[User], UpdateUserRequest), UserError, UpdateUserResponse, Any] =
    endpoint
      .name("Update profile")
      .summary("Updates specific User's profile")
      .description("Schedules specific User's profile update, requires ownership or moderator status")
      .tags(List(UsersTags.domain, UsersTags.users))
      .put
      .in(authHeader)
      .in(prefix / path[ID[User]].name("userID"))
      .in(
        jsonBody[UpdateUserRequest].examples(
          List(
            EndpointIO.Example.of(
              UpdateUserRequest(
                newUsername = Updatable.Set(User.Name("example")),
                newDescription = OptionUpdatable.Set(User.Description("example")),
                newPassword = Updatable.Set(RawPassword.fromString("example"))
              ),
              name = "Set all".some,
              summary = "Assigns new value to all fields".some
            ),
            EndpointIO.Example.of(
              UpdateUserRequest(
                newUsername = Updatable.Keep,
                newDescription = OptionUpdatable.Keep,
                newPassword = Updatable.Keep
              ),
              name = "Keep all".some,
              summary = "Keeps old value for all fields".some
            ),
            EndpointIO.Example.of(
              UpdateUserRequest(
                newUsername = Updatable.Keep,
                newDescription = OptionUpdatable.Erase,
                newPassword = Updatable.Keep
              ),
              name = "Erase description".some,
              summary = "Erases optional value".some
            )
          )
        )
      )
      .out(jsonBody[UpdateUserResponse])
      .errorOut(errorMapping)
      .requiringPermssions { case (_, _, _) => RequiredPermissions.anyOf(Permission.IsOwner, Permission.ModerateUsers) }

  val deleteProfile: AuthedEndpoint[(Authentication, ID[User]), UserError, DeleteUserResponse, Any] = endpoint
    .name("Delete profile")
    .summary("Deletes specific User's profile")
    .description("Schedules specific User's profile deletion, requires ownership or moderator status, cannot be undone")
    .tags(List(UsersTags.domain, UsersTags.users))
    .delete
    .in(authHeader)
    .in(prefix / path[ID[User]].name("userID"))
    .out(jsonBody[DeleteUserResponse])
    .errorOut(errorMapping)
    .requiringPermssions { case (_, _) => RequiredPermissions.anyOf(Permission.IsOwner, Permission.ModerateUsers) }
}
