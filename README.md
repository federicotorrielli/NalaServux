# NalaServux

Servux, Syncmatica and JEI server side for Paper/Purpur 26.2, built for Nala Mc.

Ports [Servux](https://github.com/sakura-ryoko/servux) 0.11.6, [Syncmatica](https://github.com/sakura-ryoko/syncmatica) 0.3.20 and the [JEI](https://github.com/mezz/JustEnoughItems) server code (branch 26.2).
Upstream files are kept 1:1 under `mc.nala.servux`; Fabric and mixin parts are replaced in `paper/`.

Not ported (need bytecode patches): stackable shulkers, allay gathering fix. The Litematica file transmit code is removed.

## Use

Drop the jar in `plugins/`. Config lives in `plugins/NalaServux/`. Commands: `/servux`, `/syncmatica`.

## Build and test

```
./gradlew build
python3 tools/servux_probe.py    # needs a local test server
```

License: LGPL-3.0.
