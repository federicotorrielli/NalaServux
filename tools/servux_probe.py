#!/usr/bin/env python3
"""Headless 26.2 client that checks the Servux handshakes of a server.

It logs in offline, declares the Servux channels like a client with the masa mods,
sends each provider's metadata request and prints the decoded replies.
Only the Python standard library is used.

  python3 tools/servux_probe.py [host] [port] [seconds]
"""

import gzip
import io
import socket
import struct
import sys
import time
import uuid
import zlib

PROTOCOL = 776  # 26.2

# Packet ids from the vanilla data generator report (packets.json).
C_CONFIG = {"custom_payload": 1, "disconnect": 2, "finish_configuration": 3, "keep_alive": 4, "ping": 5, "select_known_packs": 14}
S_CONFIG = {"client_information": 0, "custom_payload": 2, "finish_configuration": 3, "keep_alive": 4, "pong": 5, "select_known_packs": 7}
C_PLAY = {"update_recipes": 133, "custom_payload": 24, "disconnect": 32, "keep_alive": 44, "login": 49, "ping": 61, "player_position": 72, "system_chat": 121,
          "chunk_batch_finished": 11, "start_configuration": 118}
S_PLAY = {"accept_teleportation": 0, "chunk_batch_received": 11, "custom_payload": 22, "keep_alive": 28, "player_loaded": 44, "pong": 45}

CHANNELS = {
    "servux:hud_metadata": (3, 2),   # (protocol version, metadata request type)
    "servux:entity_data": (2, 2),
    "servux:tweaks": (2, 2),
    "servux:structures": (3, 3),
    "servux:litematics": (2, 2),
}


# ---------------------------------------------------------------- encoding

def varint(n):
    out = b""
    n &= 0xFFFFFFFF
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out += bytes([b | 0x80])
        else:
            return out + bytes([b])


def string(s):
    data = s.encode()
    return varint(len(data)) + data


def read_varint(buf):
    n = shift = 0
    while True:
        b = buf.read(1)[0]
        n |= (b & 0x7F) << shift
        shift += 7
        if not b & 0x80:
            break
    return n - (1 << 32) if n & (1 << 31) else n


def read_string(buf):
    return buf.read(read_varint(buf)).decode()


def nbt_payload(value):
    if isinstance(value, bool):
        return 1, struct.pack(">b", value)
    if isinstance(value, int):
        return 3, struct.pack(">i", value)
    if isinstance(value, str):
        data = value.encode()
        return 8, struct.pack(">H", len(data)) + data
    if isinstance(value, dict):
        out = b""
        for k, v in value.items():
            tag, payload = nbt_payload(v)
            key = k.encode()
            out += bytes([tag]) + struct.pack(">H", len(key)) + key + payload
        return 10, out + b"\x00"
    raise TypeError(value)


def network_nbt(value):
    """FriendlyByteBuf.writeNbt: tag type, then the unnamed root."""
    tag, payload = nbt_payload(value)
    return bytes([tag]) + payload


def data_tag(value):
    """malilib DataTag framing: int32 compressed length, then GZIP of a named root compound."""
    tag, payload = nbt_payload(value)
    raw = bytes([tag]) + struct.pack(">H", 0) + payload
    packed = gzip.compress(raw)
    return struct.pack(">i", len(packed)) + packed


# ---------------------------------------------------------------- decoding

def read_nbt_payload(buf, tag):
    if tag == 1: return struct.unpack(">b", buf.read(1))[0]
    if tag == 2: return struct.unpack(">h", buf.read(2))[0]
    if tag == 3: return struct.unpack(">i", buf.read(4))[0]
    if tag == 4: return struct.unpack(">q", buf.read(8))[0]
    if tag == 5: return struct.unpack(">f", buf.read(4))[0]
    if tag == 6: return struct.unpack(">d", buf.read(8))[0]
    if tag == 7: return buf.read(struct.unpack(">i", buf.read(4))[0])
    if tag == 8: return buf.read(struct.unpack(">H", buf.read(2))[0]).decode()
    if tag == 9:
        inner = buf.read(1)[0]
        return [read_nbt_payload(buf, inner) for _ in range(struct.unpack(">i", buf.read(4))[0])]
    if tag == 10:
        out = {}
        while True:
            t = buf.read(1)[0]
            if t == 0:
                return out
            name = buf.read(struct.unpack(">H", buf.read(2))[0]).decode()
            out[name] = read_nbt_payload(buf, t)
    if tag == 11:
        n = struct.unpack(">i", buf.read(4))[0]
        return list(struct.unpack(">%di" % n, buf.read(4 * n)))
    if tag == 12:
        n = struct.unpack(">i", buf.read(4))[0]
        return list(struct.unpack(">%dq" % n, buf.read(8 * n)))
    raise ValueError("tag %d" % tag)


def read_network_nbt(buf):
    tag = buf.read(1)[0]
    return None if tag == 0 else read_nbt_payload(buf, tag)


def read_data_tag(buf):
    size = struct.unpack(">i", buf.read(4))[0]
    raw = io.BytesIO(gzip.decompress(buf.read(size)))
    tag = raw.read(1)[0]
    raw.read(struct.unpack(">H", raw.read(2))[0])
    return read_nbt_payload(raw, tag)


# ---------------------------------------------------------------- connection

class Conn:
    def __init__(self, host, port):
        self.sock = socket.create_connection((host, port), timeout=10)
        self.threshold = -1

    def send(self, pid, data=b""):
        body = varint(pid) + data
        if self.threshold >= 0:
            if len(body) >= self.threshold:
                body = varint(len(body)) + zlib.compress(body)
            else:
                body = varint(0) + body
        self.sock.sendall(varint(len(body)) + body)

    def _read_exact(self, n):
        data = b""
        while len(data) < n:
            chunk = self.sock.recv(n - len(data))
            if not chunk:
                raise EOFError("connection closed")
            data += chunk
        return data

    def recv(self):
        n = shift = 0
        while True:
            b = self._read_exact(1)[0]
            n |= (b & 0x7F) << shift
            shift += 7
            if not b & 0x80:
                break
        buf = io.BytesIO(self._read_exact(n))
        if self.threshold >= 0:
            size = read_varint(buf)
            if size:
                buf = io.BytesIO(zlib.decompress(buf.read()))
        return read_varint(buf), buf


def custom_payload(conn, pid, channel, data):
    conn.send(pid, string(channel) + data)


def describe(channel, data):
    buf = io.BytesIO(data)
    ptype = read_varint(buf)
    try:
        if ptype == 1 or (channel == "servux:structures" and ptype == 1):
            return ptype, read_network_nbt(buf)
        if channel == "servux:structures" and ptype == 2:
            return ptype, "slice of %d bytes" % len(buf.read())
        if channel in ("servux:entity_data", "servux:litematics", "servux:tweaks") and ptype == 5:
            buf.read(8)  # BlockPos
            tag = read_data_tag(buf)
            return ptype, "block entity %s, keys %s, Items %s" % (tag.get("id"), sorted(tag)[:8], tag.get("Items"))
        if channel in ("servux:entity_data", "servux:litematics", "servux:tweaks") and ptype == 6:
            read_varint(buf)  # entity id
            tag = read_data_tag(buf)
            return ptype, "entity, %d keys, Pos %s, has Inventory %s" % (len(tag), tag.get("Pos"), "Inventory" in tag)
        if ptype in (10, 11):
            return ptype, "slice of %d bytes" % len(buf.read())
        return ptype, read_data_tag(buf)
    except Exception as exc:  # noqa: BLE001
        return ptype, "undecoded (%s): %s" % (exc, data[:40].hex())


def main():
    host = sys.argv[1] if len(sys.argv) > 1 else "127.0.0.1"
    port = int(sys.argv[2]) if len(sys.argv) > 2 else 25599
    seconds = float(sys.argv[3]) if len(sys.argv) > 3 else 12
    name = "Probe"

    conn = Conn(host, port)
    conn.send(0, varint(PROTOCOL) + string(host) + struct.pack(">H", port) + varint(2))
    conn.send(0, string(name) + uuid.uuid3(uuid.NAMESPACE_DNS, "OfflinePlayer:" + name).bytes)

    while True:  # login
        pid, buf = conn.recv()
        if pid == 3:
            conn.threshold = read_varint(buf)
        elif pid == 2:
            conn.send(3)
            break
        elif pid == 0:
            print("login disconnect:", buf.read())
            return 1

    register = "\0".join(list(CHANNELS) + ["syncmatica:main", "fabric:recipe_sync", "jei:cheat_permission", "jei:recipe_transfer_result"]).encode()
    custom_payload(conn, S_CONFIG["custom_payload"], "minecraft:brand", string("fabric"))
    custom_payload(conn, S_CONFIG["custom_payload"], "minecraft:register", register)

    while True:  # configuration
        pid, buf = conn.recv()
        if pid == C_CONFIG["select_known_packs"]:
            conn.send(S_CONFIG["select_known_packs"], buf.read())
        elif pid == C_CONFIG["keep_alive"]:
            conn.send(S_CONFIG["keep_alive"], buf.read())
        elif pid == C_CONFIG["ping"]:
            conn.send(S_CONFIG["pong"], buf.read())
        elif pid == C_CONFIG["finish_configuration"]:
            conn.send(S_CONFIG["finish_configuration"])
            break
        elif pid == C_CONFIG["disconnect"]:
            print("config disconnect:", buf.read())
            return 1

    custom_payload(conn, S_PLAY["custom_payload"], "minecraft:register", register)
    sent = False
    deadline = time.time() + seconds
    replies = {}

    while time.time() < deadline:
        try:
            pid, buf = conn.recv()
        except socket.timeout:
            continue
        if pid == C_PLAY["login"] and not sent:
            entity_id = struct.unpack(">i", buf.read(4))[0]
            conn.send(S_PLAY["player_loaded"])
            for channel, (version, mtype) in CHANNELS.items():
                custom_payload(conn, S_PLAY["custom_payload"], channel, varint(mtype) + network_nbt({"version": version}))
            custom_payload(conn, S_PLAY["custom_payload"], "jei:request_cheat_permission", b"")
            # entity_data: block entity request (type 3, BlockPos) at the chest of paste_test.py, entity request (type 4) for ourselves
            pos = ((14 & 0x3FFFFFF) << 38) | ((10 & 0x3FFFFFF) << 12) | (-59 & 0xFFF)
            custom_payload(conn, S_PLAY["custom_payload"], "servux:entity_data", varint(3) + struct.pack(">Q", pos))
            custom_payload(conn, S_PLAY["custom_payload"], "servux:entity_data", varint(4) + varint(entity_id))
            # HUD spawn data request (type 4, DataTag framing)
            custom_payload(conn, S_PLAY["custom_payload"], "servux:hud_metadata", varint(4) + data_tag({"version": 3}))
            sent = True
        elif pid == C_PLAY["keep_alive"]:
            conn.send(S_PLAY["keep_alive"], buf.read())
        elif pid == C_PLAY["ping"]:
            conn.send(S_PLAY["pong"], buf.read())
        elif pid == C_PLAY["player_position"]:
            conn.send(S_PLAY["accept_teleportation"], varint(read_varint(buf)))
        elif pid == C_PLAY["chunk_batch_finished"]:
            conn.send(S_PLAY["chunk_batch_received"], struct.pack(">f", 25.0))
        elif pid == C_PLAY["update_recipes"]:
            print("update_recipes packet")
            replies.setdefault("order", []).append("update_recipes")
        elif pid == C_PLAY["system_chat"]:
            print("chat:", buf.read()[:120])
        elif pid == C_PLAY["disconnect"]:
            print("play disconnect:", buf.read())
            return 1
        elif pid == C_PLAY["custom_payload"]:
            channel = read_string(buf)
            if channel == "fabric:recipe_sync":
                data = buf.read()
                print("fabric:recipe_sync      %d bytes, %d serializer groups" % (len(data), data[0]))
                replies.setdefault("order", []).append("recipe_sync")
            elif channel.startswith("jei:"):
                print("%-22s %s" % (channel, buf.read()[:80]))
            elif channel == "syncmatica:main":
                sub = read_string(buf)
                rest = buf.read()
                print("%-22s %s %s" % (channel, sub, rest[:60]))
                replies.setdefault(channel, []).append(sub)
                if sub == "syncmatica:register_version":
                    custom_payload(conn, S_PLAY["custom_payload"], channel, string(sub) + string("0.3.20"))
            elif channel.startswith("servux:"):
                ptype, content = describe(channel, buf.read())
                replies.setdefault(channel, []).append(ptype)
                print("%-22s type %-2d %s" % (channel, ptype, content))

    print("summary:", {k: v for k, v in replies.items()})
    return 0


if __name__ == "__main__":
    sys.exit(main())
