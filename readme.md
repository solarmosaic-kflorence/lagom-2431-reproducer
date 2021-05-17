# Reproducer for Lagom#2341
In a Lagom application which uses Akka Persistence (Typed) and JDBC, there seems to be a race condition when setting up the journal in relation to `slickProvider`. Normally it seems `slickProvider` would be initialized by including a non-lazy reference to `persistentEntityRegistry` -- however, if the service has no need for that, I'm not sure what the proper way to initialize the `slickProvider` is.

To reproduce, run `sbt test`. You will see the following:
```
[ERROR][2021-05-17 18:24:46,403][akka.actor.OneForOneStrategy][application-akka.actor.default-dispatcher-5][akkaAddress=akka://application@127.0.0.1:62939, sourceThread=application-akka.actor.internal-dispatcher-4, akkaSource=akka://application/system/jdbc-journal, sourceActorSystem=application, akkaTimestamp=23:24:46.403UTC] DefaultDB not found
akka.actor.ActorInitializationException: akka://application/system/jdbc-journal: exception during creation
	at akka.actor.ActorInitializationException$.apply(Actor.scala:196)
	at akka.actor.ActorCell.create(ActorCell.scala:663)
	at akka.actor.ActorCell.invokeAll$1(ActorCell.scala:513)
	at akka.actor.ActorCell.systemInvoke(ActorCell.scala:535)
	at akka.dispatch.Mailbox.processAllSystemMessages(Mailbox.scala:295)
	at akka.dispatch.Mailbox.run(Mailbox.scala:230)
	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)
	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:624)
	at java.lang.Thread.run(Thread.java:748)
Caused by: java.lang.reflect.InvocationTargetException: null
	at sun.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)
	at sun.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java:62)
	at sun.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:45)
	at java.lang.reflect.Constructor.newInstance(Constructor.java:423)
	at akka.util.Reflect$.instantiate(Reflect.scala:73)
	at akka.actor.ArgsReflectConstructor.produce(IndirectActorProducer.scala:101)
	at akka.actor.Props.newActor(Props.scala:226)
	at akka.actor.ActorCell.newActor(ActorCell.scala:615)
	at akka.actor.ActorCell.create(ActorCell.scala:642)
	... 7 common frames omitted
Caused by: javax.naming.NameNotFoundException: DefaultDB not found
	at tyrex.naming.MemoryContext.internalLookup(Unknown Source)
	at tyrex.naming.MemoryContext.lookup(Unknown Source)
	at javax.naming.InitialContext.lookup(InitialContext.java:417)
	at akka.persistence.jdbc.util.SlickDatabase$.$anonfun$database$3(SlickDatabase.scala:68)
	at scala.Option.map(Option.scala:230)
	at akka.persistence.jdbc.util.SlickDatabase$.$anonfun$database$2(SlickDatabase.scala:68)
	at scala.Option.orElse(Option.scala:447)
	at akka.persistence.jdbc.util.SlickDatabase$.database(SlickDatabase.scala:67)
	at akka.persistence.jdbc.util.SlickDatabase$.initializeEagerly(SlickDatabase.scala:79)
	at akka.persistence.jdbc.util.DefaultSlickDatabaseProvider.database(SlickExtension.scala:84)
	at akka.persistence.jdbc.util.SlickExtensionImpl.database(SlickExtension.scala:43)
	at akka.persistence.jdbc.journal.JdbcAsyncWriteJournal.<init>(JdbcAsyncWriteJournal.scala:61)
	... 16 common frames omitted [---]
```

If the `ExampleApplication` is updated to reference `slickProvider`:

```scala
val (_, _) = (slickProvider.getClass, clusterSharding.init(Entity(Example.Behavior.typeKey)(Example.Behavior.apply)))
```

The problem goes away.