#!/usr/bin/env python3
"""Pastes a small schematic through servux:litematics and prints the placed rails.

The schematic is 4x2x1. The paste goes y first, then z, then x, so:
- the ascending powered rail at x=0 is placed before the block it leans on (x=1),
- the two north-south rails at x=2 and x=3 are neighbours.
With vanilla onPlace the first rail pops and the other two turn east-west.
With the upstream behaviour (no onPlace during paste) all three keep their shape.

The test server console fifo is used to op the probe player and to read the blocks back.
  python3 tools/paste_test.py [console fifo] [log file]
"""

import struct
import sys
import time

import servux_probe as p

ORIGIN = (10, -60, 10)


class IntArray(list):
    pass


class LongArray(list):
    pass


class Long(int):
    pass


class Byte(int):
    pass


def payload(value):
    if isinstance(value, Byte):
        return 1, struct.pack(">b", value)
    if isinstance(value, Long):
        return 4, struct.pack(">q", value)
    if isinstance(value, IntArray):
        return 11, struct.pack(">i", len(value)) + b"".join(struct.pack(">i", v) for v in value)
    if isinstance(value, LongArray):
        return 12, struct.pack(">i", len(value)) + b"".join(struct.pack(">q", v) for v in value)
    if isinstance(value, list):
        if not value:
            return 9, bytes([0]) + struct.pack(">i", 0)
        tags = [payload(v) for v in value]
        return 9, bytes([tags[0][0]]) + struct.pack(">i", len(tags)) + b"".join(t[1] for t in tags)
    if isinstance(value, dict):
        out = b""
        for k, v in value.items():
            tag, data = payload(v)
            key = k.encode()
            out += bytes([tag]) + struct.pack(">H", len(key)) + key + data
        return 10, out + b"\x00"
    return p.nbt_payload(value)


def data_tag(value):
    import gzip
    tag, data = payload(value)
    packed = gzip.compress(bytes([tag]) + struct.pack(">H", 0) + data)
    return struct.pack(">i", len(packed)) + packed


def pack_states(indexes, bits):
    total = 0
    for i, v in enumerate(indexes):
        total |= v << (i * bits)
    count = (len(indexes) * bits + 63) // 64
    longs = []
    for n in range(count):
        v = (total >> (64 * n)) & 0xFFFFFFFFFFFFFFFF
        longs.append(v - (1 << 64) if v >= 1 << 63 else v)
    return LongArray(longs)


def schematic():
    rail = {"shape": "north_south", "powered": "false", "waterlogged": "false"}
    palette = [
        {"Name": "minecraft:air"},
        {"Name": "minecraft:stone"},
        {"Name": "minecraft:powered_rail", "Properties": dict(rail, shape="ascending_east")},
        {"Name": "minecraft:powered_rail", "Properties": rail},
    ]
    # index = y * (sizeX * sizeZ) + z * sizeX + x
    blocks = [1, 1, 1, 1,   # y=0
              2, 1, 3, 3]   # y=1
    region = {
        "Position": {"x": 0, "y": 0, "z": 0},
        "Size": {"x": 4, "y": 2, "z": 1},
        "BlockStatePalette": palette,
        "BlockStates": pack_states(blocks, 2),
        "TileEntities": [],
        "Entities": [],
        "PendingBlockTicks": [],
        "PendingFluidTicks": [],
    }
    now = Long(int(time.time() * 1000))
    return {
        "MinecraftDataVersion": 4903,
        "Version": 7,
        "SubVersion": 1,
        "Metadata": {"Name": "rails", "Author": "probe", "Description": "", "RegionCount": 1,
                     "TotalBlocks": 7, "TotalVolume": 8, "EnclosingSize": {"x": 4, "y": 2, "z": 1},
                     "TimeCreated": now, "TimeModified": now},
        "Regions": {"Main": region},
    }


def paste_request():
    return {
        "Task": "LitematicaPaste",
        "Name": "rails",
        "Origin": IntArray(ORIGIN),
        "Rotation": 0,
        "Mirror": 0,
        "ReplaceMode": "ALL",
        "RenderLayerRange": {"mode": "all", "axis": "y", "layer_single": 0, "layer_above": 0, "layer_below": 0,
                             "layer_range_min": 0, "layer_range_max": 0, "hotkey_range_min": Byte(0), "hotkey_range_max": Byte(0)},
        "SubRegions": {"Main": {"Name": "Main", "Pos": IntArray([0, 0, 0]), "Rotation": 0, "Mirror": 0, "Enabled": Byte(1)}},
        "Schematics": schematic(),
    }


def main():
    fifo = sys.argv[1] if len(sys.argv) > 1 else "/tmp/nala-test/console"
    log = sys.argv[2] if len(sys.argv) > 2 else None

    def console(cmd):
        with open(fifo, "w") as fh:
            fh.write(cmd + "\n")

    conn = p.Conn("127.0.0.1", 25599)
    conn.send(0, p.varint(p.PROTOCOL) + p.string("127.0.0.1") + struct.pack(">H", 25599) + p.varint(2))
    conn.send(0, p.string("Probe") + p.uuid.uuid3(p.uuid.NAMESPACE_DNS, "OfflinePlayer:Probe").bytes)
    while True:
        pid, buf = conn.recv()
        if pid == 3:
            conn.threshold = p.read_varint(buf)
        elif pid == 2:
            conn.send(3)
            break
    register = "\0".join(p.CHANNELS).encode()
    p.custom_payload(conn, p.S_CONFIG["custom_payload"], "minecraft:register", register)
    while True:
        pid, buf = conn.recv()
        if pid == p.C_CONFIG["select_known_packs"]:
            conn.send(p.S_CONFIG["select_known_packs"], buf.read())
        elif pid == p.C_CONFIG["keep_alive"]:
            conn.send(p.S_CONFIG["keep_alive"], buf.read())
        elif pid == p.C_CONFIG["finish_configuration"]:
            conn.send(p.S_CONFIG["finish_configuration"])
            break

    stage = 0
    deadline = time.time() + 20
    while time.time() < deadline:
        try:
            pid, buf = conn.recv()
        except Exception:
            pid, buf = None, None
        if pid == p.C_PLAY["login"] and stage == 0:
            conn.send(p.S_PLAY["player_loaded"])
            console("op Probe")
            console("gamemode creative Probe")
            console("forceload add 0 0 1 1")
            p.custom_payload(conn, p.S_PLAY["custom_payload"], "servux:litematics", p.varint(2) + p.network_nbt({"version": 2}))
            stage = 1
        elif pid == p.C_PLAY["keep_alive"]:
            conn.send(p.S_PLAY["keep_alive"], buf.read())
        elif pid == p.C_PLAY["ping"]:
            conn.send(p.S_PLAY["pong"], buf.read())
        elif pid == p.C_PLAY["player_position"]:
            conn.send(p.S_PLAY["accept_teleportation"], p.varint(p.read_varint(buf)))
        elif pid == p.C_PLAY["chunk_batch_finished"]:
            conn.send(p.S_PLAY["chunk_batch_received"], struct.pack(">f", 25.0))
        elif pid == p.C_PLAY["custom_payload"]:
            channel = p.read_string(buf)
            data = buf.read()
            if channel == "servux:litematics":
                ptype, content = p.describe(channel, data)
                print("litematics type", ptype, str(content)[:150])
                if ptype == 1 and stage == 1:
                    time.sleep(1.5)  # let the console commands run
                    frame = data_tag(paste_request())
                    body = p.varint(len(frame)) + frame  # PacketSplitter: first slice starts with the total length
                    p.custom_payload(conn, p.S_PLAY["custom_payload"], "servux:litematics", p.varint(13) + body)
                    stage = 2
                    deadline = time.time() + 5
        elif pid == p.C_PLAY["system_chat"]:
            print("chat:", buf.read()[:160])

    x, y, z = ORIGIN
    for dx, what in ((0, "ascending_east"), (2, "north_south"), (3, "north_south")):
        console("execute if block %d %d %d minecraft:powered_rail[shape=%s] run say RAIL_OK x=%d %s" % (x + dx, y + 1, z, what, dx, what))
        console("execute unless block %d %d %d minecraft:powered_rail[shape=%s] run say RAIL_BAD x=%d expected %s" % (x + dx, y + 1, z, what, dx, what))
    time.sleep(2)
    if log:
        with open(log) as fh:
            for line in fh:
                if "RAIL_" in line:
                    print(line.strip())


if __name__ == "__main__":
    main()
