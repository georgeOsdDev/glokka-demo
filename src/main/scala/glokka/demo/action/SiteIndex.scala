package glokka.demo.action


import akka.actor.ActorSystem
import glokka.Registry

import xitrum.annotation.GET

@GET("")
class SiteIndex extends DefaultLayout {
  def execute() {
    respondView()
  }
}
