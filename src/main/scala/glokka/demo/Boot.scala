package glokka.demo

import xitrum.Server

import glokka.demo.model.Hub

object Boot {
  def main(args: Array[String]) {
    Hub.start()
    Server.start()
  }
}
