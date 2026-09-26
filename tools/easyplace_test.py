#!/usr/bin/env python3
"""Checks Easy Place protocol V3: places pistons with an encoded hit x and reads the facing back.

  python3 tools/easyplace_test.py [console fifo] [log file]
"""

import struct
import sys
import time

import servux_probe as p

USE_ITEM_ON = 66


def block_pos(x, y, z):
    return struct.pack(">Q", ((x & 0x3FFFFFF) << 38) | ((z & 0x3FFFFFF) << 12) | (y & 0xFFF))


def use_item_on(conn, x, y, z, cursor_x, sequence):
    # hand, clicked block, face (1 = up), cursor relative to the block, inside, world border hit, sequence
    data = p.varint(0) + block_pos(x, y, z) + p.varint(1) + struct.pack(">fff", cursor_x, 1.0, 0.5) + b"\x00\x00" + p.varint(sequence)
    conn.send(USE_ITEM_ON, data)


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
    deadline = time.time() + 12
    while time.time() < deadline:
        try:
            pid, buf = conn.recv()
        except Exception:
            continue
        if pid == p.C_PLAY["login"] and stage == 0:
            conn.send(p.S_PLAY["player_loaded"])
            console("op Probe")
            console("gamemode creative Probe")
            console("tp Probe 0 -60 0 0 0")
            console("fill -3 -60 1 3 -59 3 air")
            console("item replace entity Probe hotbar.0 with piston 64")
            stage = 1
            start = time.time()
        elif pid == p.C_PLAY["keep_alive"]:
            conn.send(p.S_PLAY["keep_alive"], buf.read())
        elif pid == p.C_PLAY["ping"]:
            conn.send(p.S_PLAY["pong"], buf.read())
        elif pid == p.C_PLAY["player_position"]:
            conn.send(p.S_PLAY["accept_teleportation"], p.varint(p.read_varint(buf)))
        elif pid == p.C_PLAY["chunk_batch_finished"]:
            conn.send(p.S_PLAY["chunk_batch_received"], struct.pack(">f", 25.0))
        if stage == 1 and time.time() - start > 3:
            # Protocol value 2 = facing index 1 (up) in bits 1..3. Encoded cursor x = relX + 2 + value.
            use_item_on(conn, 1, -61, 2, 0.5 + 2 + 2, 1)
            # Plain click for comparison.
            use_item_on(conn, -1, -61, 2, 0.5, 2)
            console("item replace entity Probe hotbar.0 with oak_door 64")
            stage = 2
            start = time.time()
        if stage == 2 and time.time() - start > 2:
            # Facing index 5 (east): protocol value 10.
            use_item_on(conn, 2, -61, 3, 0.5 + 2 + 10, 3)
            use_item_on(conn, -2, -61, 3, 0.5, 4)
            stage = 3

    console("execute if block 1 -60 2 minecraft:piston[facing=up] run say EASYPLACE_OK encoded piston faces up")
    console("execute unless block 1 -60 2 minecraft:piston[facing=up] run say EASYPLACE_BAD encoded piston does not face up")
    console("execute if block -1 -60 2 minecraft:piston run say EASYPLACE_OK plain click placed a piston")
    console("execute if block -1 -60 2 minecraft:piston[facing=up] run say EASYPLACE_BAD plain click also faces up")
    console("execute if block 2 -60 3 minecraft:oak_door[facing=east,half=lower] if block 2 -59 3 minecraft:oak_door[facing=east,half=upper] run say EASYPLACE_OK door faces east, both halves")
    console("execute unless block 2 -59 3 minecraft:oak_door[facing=east,half=upper] run say EASYPLACE_BAD door upper half wrong")
    console("execute unless block 2 -60 3 minecraft:oak_door[facing=east,half=lower] run say EASYPLACE_BAD door lower half wrong")
    console("execute if block -2 -60 3 minecraft:oak_door run say EASYPLACE_INFO plain door placed")
    time.sleep(2)
    if log:
        with open(log) as fh:
            for line in fh:
                if "EASYPLACE_" in line:
                    print(line.strip())


if __name__ == "__main__":
    main()
