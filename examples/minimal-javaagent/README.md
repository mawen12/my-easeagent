# minimal-javaagent

A minimal Java Agent example with:

- `pom.xml` (directly buildable)
- `premain` entry class
- one sample plugin (`LoggingPlugin`)
- one demo app entry (`DemoApplication`)

The plugin now uses an interceptor chain (EaseAgent-style):

- `before` executes in forward order
- `after` executes in reverse order
- add new advice behavior by registering new interceptors

## Build

```bash
cd examples/minimal-javaagent
mvn clean package
```

After build, use:

- Agent jar: `target/minimal-javaagent-1.0.0.jar`

## Run without agent

```bash
java -cp target/minimal-javaagent-1.0.0.jar com.example.app.DemoApplication
```

Expected output:

```text
[app] Hello, EaseAgent
```

## Run with agent

```bash
java -javaagent:target/minimal-javaagent-1.0.0.jar -cp target/minimal-javaagent-1.0.0.jar com.example.app.DemoApplication
```

Expected output:

```text
[agent] start, args=
[agent] installed plugin: logging
[agent] enter com.example.app.GreetingService.sayHello
[agent] exit  com.example.app.GreetingService.sayHello
[app] Hello, EaseAgent
```

## Optional: pass agent args

```bash
java -javaagent:target/minimal-javaagent-1.0.0.jar=targetClass=com.example.app.GreetingService,targetMethod=sayHello,plugin.logging.enabled=true -cp target/minimal-javaagent-1.0.0.jar com.example.app.DemoApplication
```

## Optional: configure interceptors

```bash
java -javaagent:target/minimal-javaagent-1.0.0.jar=interceptor.logging.enabled=true,interceptor.timing.enabled=true,interceptor.timing.warnMs=1 -cp target/minimal-javaagent-1.0.0.jar com.example.app.DemoApplication
```

Built-in interceptors:

- `logging`: print enter/exit
- `timing`: print slow-call logs when elapsed time >= `interceptor.timing.warnMs`

## Replace package names

You can replace `com.example` with your own package name and keep the same structure.

