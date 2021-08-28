package krboperator

import cats.Parallel
import cats.effect.Sync
import cats.implicits._
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.crd.CustomResource
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import krboperator.Controller.NewStatus
import krboperator.LoggingUtils._
import krboperator.PrincipalController._
import krboperator.service.{Kadmin, KadminContext, KerberosState, Secrets}
import org.typelevel.log4cats.Logger

import java.nio.file.Path

object PrincipalController {
  val ServerLabel = "krb-operator.novakov-alexey.github.io/server"
}

class PrincipalController[F[_]: Parallel](
    secret: Secrets[F],
    kadmin: Kadmin[F],
    client: KubernetesClient[F],
    serverController: ServerController[F],
    parallelSecret: Boolean = true
)(implicit F: Sync[F], val logger: Logger[F])
    extends Controller[F, Principals, PrincipalsStatus]
    with LoggingUtils[F] {

  override def onAdd(
      resource: CustomResource[Principals, PrincipalsStatus]
  ): F[NewStatus[PrincipalsStatus]] =
    onApply(resource.spec, resource.metadata)

  override def onModify(
      resource: CustomResource[Principals, PrincipalsStatus]
  ): F[NewStatus[PrincipalsStatus]] =
    onApply(resource.spec, resource.metadata)

  override def onDelete(
      resource: CustomResource[Principals, PrincipalsStatus]
  ): F[Unit] = super.onDelete(resource)

  private def getNamespace(meta: Option[ObjectMeta]): F[(ObjectMeta, String)] =
    for {
      meta <- F.fromOption(meta, new RuntimeException("Metadata is empty"))
      ns <- F.fromOption(
        meta.namespace,
        new RuntimeException(s"Namespace is empty in $meta")
      )

    } yield (meta, ns)

  private def onApply(
      principals: Principals,
      maybeMeta: Option[ObjectMeta]
  ): F[NewStatus[PrincipalsStatus]] =
    for {
      (meta, ns) <- getNamespace(maybeMeta)
      server <- getKrbServer(meta)
      missingSecrets <- secret.findMissing(
        meta,
        principals.list.map(_.secret.name).toSet
      )
      created <- missingSecrets.toList match {
        case Nil =>
          debug(
            ns,
            s"There are no missing secrets"
          ) *> F.pure(List.empty[Unit])
        case _ =>
          info(
            ns,
            s" There are ${missingSecrets.size} missing secrets, name(s): $missingSecrets"
          ) *> createSecrets(server, principals, meta, ns, missingSecrets)
      }
      _ <- F.whenA(created.nonEmpty)(
        info(ns, s"${created.length} secrets created")
      )
    } yield PrincipalsStatus(
      processed = true,
      created.length,
      principals.list.length
    ).some

  private def currentServers
      : F[List[CustomResource[KrbServer, KrbServerStatus]]] =
    serverController.currentServers.map(_.items.toList)

  private[krboperator] def getKrbServer(
      meta: ObjectMeta
  ): F[CustomResource[KrbServer, KrbServerStatus]] = for {
    serverName <- F.fromEither(
      meta.labels
        .getOrElse(Map.empty)
        .collectFirst { case (ServerLabel, v) => v }
        .toRight(
          new RuntimeException(
            s"Current resource does not have a label '$ServerLabel'"
          )
        )
    )
    servers <- currentServers
    serverOrError = servers
      .find { resource =>
        resource.metadata.exists(_.name.exists(_ == serverName))
      }
      .map(_.asRight[Throwable])
      .getOrElse(
        Either.left(
          new RuntimeException(
            s"Failed to find ${classOf[KrbServer].getSimpleName} resource with name '$serverName'"
          )
        )
      )
    server <- F.fromEither(serverOrError)
  } yield server

  private def createSecrets(
      server: CustomResource[KrbServer, KrbServerStatus],
      principals: Principals,
      meta: ObjectMeta,
      namespace: String,
      missingSecrets: Set[String]
  ): F[List[Unit]] =
    for {
      pwd <- secret.getAdminPwd(meta)
      serverName <- F.fromOption(
        server.metadata.flatMap(_.name),
        new RuntimeException(
          s"Server name is empty in customer resource metadata ${server.metadata}"
        )
      )
      context = KadminContext(
        serverName,
        server.spec.realm,
        meta,
        pwd
      )
      created <- {
        val tasks = missingSecrets
          .map(s => (s, principals.list.filter(_.secret.name == s)))
          .map { case (secretName, principals) =>
            for {
              _ <- debug(
                namespace,
                s"Creating secret: $secretName"
              )
              state <- kadmin.createPrincipalsAndKeytabs(principals, context)
              statuses <- downloadKeytabsFromServer(namespace, state)
              _ <- checkStatuses(statuses)
              _ <- secret.create(namespace, state.principals, secretName)
              _ <- info(
                namespace,
                s"$checkMark Keytab secret '$secretName' created"
              )
              _ <- removeWorkingDirs(namespace, state)
                .handleErrorWith { e =>
                  error(
                    namespace,
                    s"Failed to delete working directory(s) with keytab(s) in POD $namespace/${state.podName}",
                    e
                  )
                }
            } yield ()
          }
          .toList
        if (parallelSecret) tasks.parSequence else tasks.sequence
      }
    } yield created

  private def checkStatuses(statuses: List[(Path, Boolean)]): F[Unit] = {
    val notAllCopied = !statuses.forall { case (_, copied) => copied }
    F.whenA(notAllCopied)(F.raiseError[Unit] {
      val paths = statuses
        .filter { case (_, copied) =>
          !copied
        }
        .map { case (path, _) => path }
      new RuntimeException(s"Failed to upload keytab(s) $paths into POD")
    })
  }

  private def downloadKeytabsFromServer(
      namespace: String,
      state: KerberosState
  ): F[List[(Path, Boolean)]] =
    F.delay(state.principals.foldLeft(List.empty[(Path, Boolean)]) {
      case (acc, principals) =>
        val path = principals.keytabMeta.path
        debug(
          namespace,
          s"Copying keytab '$path' from $namespace/${state.podName} POD"
        )
        val copied = true //TODO
        // client.pods
        // .namespace(namespace)
        // .get(state.podName)
        // .inContainer(operatorCfg.kadminContainer)
        // .file(path.toString)
        // .copy(path)

        acc :+ (path, copied)
    })

  private def removeWorkingDirs(
      namespace: String,
      state: KerberosState
  ): F[Unit] =
    state.principals
      .map { p =>
        kadmin.removeWorkingDir(namespace, state.podName, p.keytabMeta.path)
      }
      .sequence
      .void
}
