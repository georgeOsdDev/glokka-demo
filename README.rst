Glokka Demo
===========

This is a demo project for
Clustering `Xitrum <https://github.com/xitrum-framework>`_- Servers with `Glokka <https://github.com/xitrum-framework/glokka>`_.

::

     +--------------------+             +--------------------+
     |        Xitrum      |             |        Xitrum      |
     | +~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~+ |
     | | Glokka                                            | |
     | |             _ _ _ [ Hub Actor ] _ _ _             | |
     | |           /                           \           | |
     | +~~~~~~~~~~/~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\~~~~~~~~~~+ |
     |           /        |             |        \           |
     |  [HubClientActor]  |             |  [HubClientActor]  |
     |         |          |             |          |         |
     +---------|----------+             +----------|---------+
               |                                   |
  +------------|------------+         +------------|------------+
  |  Browser                |         |  Browser                |
  |                         |         |                         |
  | var sock = SockJS       |         | var sock = SockJS       |
  | (http://localhost:8000) |         | (http://localhost:8001) |
  |                         |         |                         |
  +-------------------------+         +-------------------------+

How Hub Actor works
~~~~~~~~~~~~~~~~~~~

* ``Subscribe(option:Map[String, Any])``

Join to HUB as subscriber. ``Done(option:Map[String, Any])`` message will be return from HUB.

::

 [HubClientActor] - Subscribe -> [Hub Actor]
                                     |
                                     |
                   <--- Done --------」

* ``Unsubscribe(option:Map[String, Any])``

Leave from HUB, ``Done(option:Map[String, Any])`` message will be return from HUB.

::

 [HubClientActor] - Unubscribe -> [Hub Actor]
                                     |
                                     |
                   <--- Done --------」

* ``Push(option:Map[String, Any])``

Send message to HUB, HUB will handle that request then ``Publish(option:Map[String, Any])`` to all subscriber except sender.

::

  [HubClientActor] - Push -> [Hub Actor] ---- Publish ---> [(another) HubClient Actor]
                                |          \
                                |           ---- Publish ---> [(another) HubClient Actor]
                   <--- Done ---」           \
                                              --- Publish ---> [(another) HubClient Actor]

* ``Pull(option:Map[String, Any])``

Send command to HUB, HUB will handle that request(eg: count subscriber num) then response result to sender as ``Done(option:Map[String, Any])`` message.

::

 [HubClientActor] - Pull -> [Hub Actor]
                                 |
                                 |
                   <--- Done ----」

How to Run
==========

Clone and compile project

::

	cd /path/to/temp/directory
	git clone https://github.com/georgeosddev/glokka-demo
	cd glokka-demo
	sbt/sbt xitrum-package

Copy packaged application 2 times

::

	cd ../
	cp -r glokka-demo/target/xitrum node1
	cp -r glokka-demo/target/xitrum node2

Edit config for node2 to include "akka_for2" and "xitrum_for2"

::

	cd node2
	vi config/application.conf

Run 2 application from separate terminal
node1 will listen http://localhost:8000、and node2 will listen http://localhost:8001.

::

	cd /path/to/temp/directory/node1
	./script/runner glokka.demo.Boot

::

	cd /path/to/temp/directory/node2
	./script/runner glokka.demo.Boot


