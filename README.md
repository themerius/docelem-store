# docelem-store

## Connecting

Your 'client' also needs Akka-Remoting (see application.conf) and
the Actor-Interface-Stubs of the docelem-store.
Then you can connect like this:

    // Imports
    import akka.actor.{ ActorRef, ActorSystem, Props, Actor, Inbox }
    import scala.concurrent.duration._
    // Imports for ask pattern
    import akka.pattern.ask
    import akka.util.Timeout
    import scala.concurrent.Await
    // Imports for remoting
    import akka.actor.{ Props, Deploy, Address, AddressFromURIString }
    import akka.remote.RemoteScope

    // Creating a client actor system (with enabled remoting)
    val system = ActorSystem("su")
    val adr = Address("akka.tcp", "docelem-store", "0.0.0.0", 2552)
    val store = system.actorOf(Props[Store].withDeploy(Deploy(scope=RemoteScope(adr))))
    // The `store` actors behaves like a local actor, but is remote
    
    // Let's receive some DocElem actors
    val inbox = Inbox.create(system)
    implicit val timeout = Timeout(5.seconds)
    inbox.send(store, GetFlatTopology("about-003"))
    val Response(des) = inbox.receive(timeout.duration)
    // Using the ask pattern, to get a projection
    val msgs = des.view.map(_ ? Projection("PlainText"))
    val awaited = msgs.map(Await.result(_, timeout.duration).asInstanceOf[String])
    // Go back to serialized code
    println(awaited.force)
