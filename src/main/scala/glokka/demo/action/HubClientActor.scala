package glokka.demo.action


import akka.actor.{Actor, ActorRef, Props, Terminated}

import xitrum.{SockJsAction, SockJsText}
import xitrum.annotation.SOCKJS
import xitrum.util.SeriDeseri

import glokka.demo.model.{Hub, HubClient, Done, Publish, Push, Pull, Subscribe, UnSubscribe, Utils}
import glokka.demo.model.ErrorCode._


class HubImpl extends Hub {
  override def handlePush(msg: Map[String, Any]):  Map[String, Any] = {
    msg.getOrElse("cmd", "invalid") match {
      case "text" =>
        Map(
          "error"   -> SUCCESS,
          "seq"       -> msg.getOrElse("seq", -1),
          "targets"   -> msg.getOrElse("targets", "*"),
          "tag"       -> "text",
          "body"      -> msg.getOrElse("body",""),
          "senderName"-> msg.getOrElse("senderName","Anonymous"),
          "senderId"  -> msg.getOrElse("senderId","")
        )

      case unknown =>
        Map(
          "tag"     -> "system",
          "error"   -> INVALID_CMD,
          "seq"     -> msg.getOrElse("seq", -1),
          "targets" -> msg.getOrElse("uuid", "")
        )
    }
  }

  override def handlePull(msg: Map[String, Any]):  Map[String, Any] = {
    msg.getOrElse("cmd", "invalid") match {
      case "count" =>
        Map(
          "tag"     -> "system",
          "error"   -> SUCCESS,
          "seq"     -> msg.getOrElse("seq", -1),
          "count"   -> clients.size
        )
      case unknown =>
        Map(
          "tag"     -> "system",
          "error"   -> INVALID_CMD,
          "seq"     -> msg.getOrElse("seq", -1)
        )
    }
  }
}

@SOCKJS("connect")
class HubClientActor extends SockJsAction with HubClient {
  private val hubKey    = "glokkaExampleHub"
  private val hubProps  = Props[HubImpl]

  def execute() {
    log.debug(s"[HubClient][$node] is assigned to client")
    checkAPIKey()
  }

  private def checkAPIKey() {
    context.become {
      case SockJsText(msg) =>
        log.debug(s"[HubClient][$node] Received first frame from client")
        parse2MapWithTag(msg) match {
          case ("login", parsed) =>
            if (Utils.auth(parsed.getOrElse("apikey", ""))) {
              lookUpHub(hubKey, hubProps, parsed)
            } else {
              respondSockJsTextWithLog(
                parse2JSON(
                  Map(
                    "error"   -> INVALID_APIKEY,
                    "tag"     -> "system",
                    "seq"     -> parsed.getOrElse("seq", -1),
                    "message" -> "Invalid api key"
                  )
                )
              )
              log.debug(s"Auth error: ${parsed.toString}")
              respondSockJsClose()
            }
          case (_, parsed) =>
            respondSockJsTextWithLog(
              parse2JSON(
                Map(
                  "error"   -> NOT_CONNECTED,
                  "tag"     -> "system",
                  "seq"     -> parsed.getOrElse("seq", -1),
                  "message" -> "First frame must be `login` request"
                )
              )
            )
            log.debug(s"Unexpected first frame: ${parsed.toString}")
            respondSockJsClose()
        }

      case ignore =>
        log.warn(s"Unexpected message: ${ignore}")
    }
  }

  override def doWithHub(hub: ActorRef, option: Any) {
    val loginRequest = option.asInstanceOf[Map[String, Any]]
    val name         = loginRequest.getOrElse("name", "Anonymous").toString
    val uuid         = node //Utils.md5(node)

    log.debug(s"[HubClient][${uuid}] HUB found: " + hub.toString)

    // Start subscribing
    log.debug(s"[HubClient][${uuid}] Start Subscribing HUB")
    hub ! Subscribe()
    context.watch(hub)

    respondSockJsText(
      parse2JSON(
        Map(
          "error"   -> SUCCESS,
          "seq"     -> loginRequest.getOrElse("seq", -1),
          "tag"     -> "system",
          "node"    -> node,
          "hub"     -> hub.toString,
          "uuid"    -> uuid,
          "message" -> s"Welcome ${name}!. Your uuid is ${uuid}"
        )
      )
    )

    context.become {

      // (AnotherNode -> ) Hub -> LocalNode
      case Publish(msg) =>
        if (!msg.isEmpty) {
          log.debug(s"[HubClient][${uuid}] Received Publish message from HUB")
          msg.getOrElse("targets", "*") match {
            case list:Array[String] if (list.contains(uuid)) =>
              // LocalNode -> client
              respondSockJsTextWithLog(parse2JSON(msg - ("error", "seq", "targets")))
            case targetId:String if (targetId == uuid) =>
              // LocalNode -> client
              respondSockJsTextWithLog(parse2JSON(msg - ("error", "seq", "targets")))
            case "*" =>
              // LocalNode -> client
              respondSockJsTextWithLog(parse2JSON(msg - ("error", "seq", "targets")))
            case ignore =>
          }
        }

      // (LocalNode ->) Hub -> LocalNode
      case Done(result) =>
        log.debug(s"[HubClient][${uuid}] Received Done message from HUB")
        if (!result.isEmpty) respondSockJsTextWithLog(parse2JSON(result + ("tag" -> "system")))

        // Client -> LocalNode
      case SockJsText(msg) =>
        log.debug(s"[HubClient][${uuid}] Received message from client: $msg")
        parse2MapWithTag(msg) match {
          case ("subscribe", parsed) =>
            // LocalNode -> Hub (-> LocalNode)
            log.debug(s"[HubClient][${uuid}] Send Subscribe request to HUB")
            hub ! Subscribe(Map(
                                "error"   -> SUCCESS,
                                "tag"     -> "system",
                                "seq"     -> parsed.getOrElse("seq", -1)
                              ))

          case ("unsubscribe", parsed) =>
            // LocalNode -> Hub (-> LocalNode)
            log.debug(s"[HubClient][${uuid}] Send UnSubscribe request to HUB")
            hub ! UnSubscribe(Map(
                                "error"   -> SUCCESS,
                                "tag"     -> "system",
                                "seq"     -> parsed.getOrElse("seq", -1)
                              ))

          case ("pull", parsed) =>
            // LocalNode -> Hub (-> LocalNode)
            log.debug(s"[${uuid}] Send Pull request to HUB")
            hub ! Pull(parsed + ("uuid" -> uuid))

          case ("push", parsed) =>
            // LocalNode -> Hub (-> AnotherNode)
            log.debug(s"[HubClient][${uuid}] Send Push request to HUB")
            hub ! Push(parsed + ("senderName" -> name, "senderId" -> uuid))

          case (invalid, parsed) =>
            // LocalNode -> client
            respondSockJsTextWithLog(
              parse2JSON(
                Map(
                  "error"   -> INVALID_TAG,
                  "tag"     -> "system",
                  "seq"     -> parsed.getOrElse("seq", -1),
                  "message" -> s"Invalid tag:${invalid}. Tag must be `subscribe` or `unsubscribe` or `pull` or `push`."
                )
              )
            )
        }

      case Terminated(hub) =>
        log.warn("Hub is terminatad")
        // Retry to lookup hub
        Thread.sleep(100L * (scala.util.Random.nextInt(3) + 1))
        lookUpHub(hubKey, hubProps, option)

      case ignore =>
        log.warn(s"Unexpected message: $ignore")
    }
  }

  private def parse2MapWithTag(jsonStr: String): (String, Map[String, String]) = {
    SeriDeseri.fromJson[Map[String, String]](jsonStr) match {
      case Some(json) =>
          (json.getOrElse("tag", "invalidTag"), json)
      case None =>
        log.warn(s"Failed to parse request: $jsonStr")
        ("invalid", Map.empty)
    }
  }

  private def parse2JSON(ref: AnyRef) = SeriDeseri.toJson(ref)

  private def respondSockJsTextWithLog(text: String):Unit = {
    log.debug(s"[HubClient][${node}] send message to client")
    respondSockJsText(text)
  }
}