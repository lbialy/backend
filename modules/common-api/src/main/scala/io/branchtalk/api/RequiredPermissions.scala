package io.branchtalk.api

import cats.Eq
import cats.data.{ NonEmptyList, NonEmptySet }
import com.github.plokhotnyuk.jsoniter_scala.macros._
import io.branchtalk.ADT
import io.branchtalk.api.JsoniterSupport._
import io.branchtalk.shared.model.{ FastEq, ShowPretty }
import io.scalaland.catnip.Semi

@Semi(FastEq, ShowPretty) sealed trait RequiredPermissions extends ADT {

  // scalastyle:off method.name
  def &&(other: RequiredPermissions): RequiredPermissions = RequiredPermissions.And(this, other)
  def ||(other: RequiredPermissions): RequiredPermissions = RequiredPermissions.Or(this, other)
  def unary_! : RequiredPermissions = RequiredPermissions.Not(this)
  // scalastyle:on method.name
}
object RequiredPermissions {

  def empty: RequiredPermissions = Empty
  def one(permission: Permission): RequiredPermissions = AllOf(NonEmptySet.one(permission))
  def allOf(head:     Permission, tail: Permission*): RequiredPermissions = AllOf(NonEmptySet.of(head, tail: _*))
  def anyOf(head:     Permission, tail: Permission*): RequiredPermissions = AnyOf(NonEmptySet.of(head, tail: _*))

  case object Empty extends RequiredPermissions

  final case class AllOf(toSet: NonEmptySet[Permission]) extends RequiredPermissions
  final case class AnyOf(toSet: NonEmptySet[Permission]) extends RequiredPermissions

  final case class And(x: RequiredPermissions, y: RequiredPermissions) extends RequiredPermissions
  final case class Or(x: RequiredPermissions, y: RequiredPermissions) extends RequiredPermissions
  final case class Not(x: RequiredPermissions) extends RequiredPermissions

  @SuppressWarnings(Array("org.wartremover.warts.All")) // handling valid null values
  implicit val jsCodec: JsCodec[RequiredPermissions] =
    summonCodec[RequiredPermissions](JsonCodecMaker.make(CodecMakerConfig.withAllowRecursiveTypes(true)))

  implicit val nesEq: Eq[NonEmptySet[Permission]] = (x: NonEmptySet[Permission], y: NonEmptySet[Permission]) =>
    x.toSortedSet.toSet === y.toSortedSet.toSet
  implicit val nesShow: ShowPretty[NonEmptySet[Permission]] =
    (t: NonEmptySet[Permission], sb: StringBuilder, indentWith: String, indentLevel: Int) => {
      val nextIndent = indentLevel + 1
      sb.append(indentWith * indentLevel).append("NonEmptySet(\n")
      t.toNonEmptyList match {
        case NonEmptyList(head, tail) =>
          sb.append(indentWith * nextIndent)
          implicitly[ShowPretty[Permission]].showPretty(head, sb, indentWith, nextIndent)
          tail.foreach { elem =>
            sb.append(",\n")
            sb.append(indentWith * nextIndent)
            implicitly[ShowPretty[Permission]].showPretty(elem, sb, indentWith, nextIndent)
          }
          sb.append("\n)")
      }
      sb
    }
}
