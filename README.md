# PHP CQRS Navigation

A PhpStorm plugin to navigate between Symfony Messenger messages and their handlers, and between EventDispatcher
events and their listeners and subscribers.

- Gutter icon on a command or event class declaration, and on `new CreateUser(...)`: go to its handlers. With several
  handlers, a list lets you pick one.
- Gutter icon on a handler or listener method: go back to the message it handles.
- Navigate > Related Symbol (Ctrl+Cmd+Up on macOS) offers the same targets.

## What counts as a handler

- `#[AsMessageHandler]` or `#[AsEventListener]` on the method or on the class. The message is the `handles` / `event`
  argument, or else the type of the first parameter. A class-level attribute targets its `method` argument, or
  `__invoke` by default.
- `__invoke` of a class implementing `MessageHandlerInterface`.
- Methods mapped in `EventSubscriberInterface::getSubscribedEvents()` with `UserCreated::class => 'method'` (all the
  array forms with priorities are supported).
- Fallback for handlers wired in `services.yaml` (`_instanceof`, custom interfaces): `__invoke` of a class whose name
  ends with `Handler`, `Listener` or `Subscriber`.

Not supported: listeners declared only in YAML/XML service config (`kernel.event_listener` tags), string event names
such as `kernel.request`, and handlers registered for a parent class or interface of the message.

## Build

The plugin is plain JVM bytecode: the zip built on one OS installs on PhpStorm for macOS, Linux and Windows alike.

Gradle needs a JDK 25 (`JAVA_HOME` or `java` on the `PATH`). PhpStorm's bundled JBR works. By default the build
downloads PhpStorm 2026.2 to compile against; pass `-PphpstormPath=...` to use a local install instead (faster, no
download).

### macOS

```sh
export JAVA_HOME=~/Applications/PhpStorm.app/Contents/jbr/Contents/Home
./gradlew buildPlugin -PphpstormPath=$HOME/Applications/PhpStorm.app
```

### Linux

```sh
# Path of your PhpStorm install, e.g. /opt/phpstorm, or with Toolbox ~/.local/share/JetBrains/Toolbox/apps/phpstorm
PHPSTORM=/opt/phpstorm
export JAVA_HOME=$PHPSTORM/jbr
./gradlew buildPlugin -PphpstormPath=$PHPSTORM
```

Without a local PhpStorm, install any JDK 25 (for example `sdk install java 25-tem` with SDKMAN, or your distribution's
`openjdk-25-jdk` package) and run `./gradlew buildPlugin`.

### Output

The plugin zip is written to `build/distributions/`. Install it with Settings > Plugins > gear icon > Install Plugin
from Disk. `./gradlew test` runs the tests, `./gradlew runIde` starts a sandboxed PhpStorm with the plugin loaded.
