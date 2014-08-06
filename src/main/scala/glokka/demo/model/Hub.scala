package glokka.demo.model

import akka.actor.{Actor, ActorRef, Props, Terminated}
import glokka.Registry
import xitrum.Config


case class Done(option: Map[String, Any] = Map.empty)        // Hub       -> LocalNode
case class Publish(option: Map[String, Any] = Map.empty)     // Hub       -> LocalNode
case class Pull(option: Map[String, Any] = Map.empty)        // LocalNode -> Hub
case class Push(option: Map[String, Any] = Map.empty)        // LocalNode -> Hub
case class Subscribe(option: Map[String, Any] = Map.empty)   // LocalNode -> Hub
case class UnSubscribe(option: Map[String, Any] = Map.empty) // LocalNode -> Hub


object Hub {
  val KEY_PROXY = "HUB_PROXY"
  // Glokka registry
  val actorRegistry = Registry.start(Config.actorSystem, KEY_PROXY)

  // To force start registry at process start up,
  // Call this method at `main` before start `xitrum.Server`
  def start(){}
}


trait Hub extends Actor {
  protected var clients = Seq[ActorRef]()
  private lazy val node = self.toString

  def receive = {
    case Push(option) =>
      xitrum.Log.debug(s"[Hub][${node}] Received Push request")
      val result = handlePush(option)
      clients.foreach { client =>
        if (client != sender) client ! Publish(result)
      }
      sender ! Done(result)

    case Pull(option) =>
      xitrum.Log.debug(s"[Hub][${node}] Received Pull request")
      sender ! Done(handlePull(option))

    case Subscribe(option) =>
      xitrum.Log.debug(s"[Hub][${node}] Received Subscribe request")
      clients = clients.filterNot(_ == sender) :+ sender
      context.watch(sender)
      sender ! Done(option)

    case UnSubscribe(option) =>
      xitrum.Log.debug(s"[Hub][${node}] Received UnSubscribe request")
      clients =  clients.filterNot(_ == sender)
      context.unwatch(sender)
      sender ! Done(option)

    case Terminated(client) =>
      xitrum.Log.debug(s"[Hub][${node}] Received Terminated event"+client.toString)
      clients = clients.filterNot(_ == client)

    case ignore =>
      xitrum.Log.warn(s"[Hub][${node}] Unexpected message: $ignore")
  }

  // Implement these method as you like
  def handlePush(msg: Map[String, Any]): Map[String, Any]
  def handlePull(option: Map[String, Any]): Map[String, Any]
}

trait HubClient extends Actor {
  protected lazy val node = self.toString
  def lookUpHub(key: String, hubProps: Props, option: Any = None) {
    xitrum.Log.debug(s"[HubClient][${node}] Searching HUB node...")
    Hub.actorRegistry ! Registry.Register(key, hubProps)
    context.become {
      case result: Registry.FoundOrCreated => doWithHub(result.ref, option)
      case ignore =>
        xitrum.Log.warn(s"[HubClient][${node}] Unexpected message: $ignore")
    }
  }

  // Implement these method as you like
  def doWithHub(publisher: ActorRef, option: Any)
}