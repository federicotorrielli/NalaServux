# NalaServux

Servux, Syncmatica and JEI server side, hyperoptimized for Paper/Purpur 26.2.
It works on my server so... it should work on yours too!

Ports [Servux](https://github.com/sakura-ryoko/servux) 0.11.6, [Syncmatica](https://github.com/sakura-ryoko/syncmatica) 0.3.20 and the [JEI](https://github.com/mezz/JustEnoughItems) server code (branch 26.2).

Not ported (need bytecode patches): stackable shulkers, allay gathering fix. The Litematica file transmit code was intentionally removed.

## Use

Drop the jar in `plugins/`. Config is in `plugins/NalaServux/`. Commands: `/servux`, `/syncmatica`.

## Build and test

```
./gradlew build
python3 tools/servux_probe.py    # needs a local test server
```

License: LGPL-3.0.
