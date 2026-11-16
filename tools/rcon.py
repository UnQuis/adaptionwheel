#!/usr/bin/env python3
"""Minimal Source-RCON client for headless server smoke tests.

Gradle does NOT forward stdin to the game server, so `./gradlew runServer` cannot
be driven by piping commands. RCON is the supported way to ask a running dev
server questions ("list", "seed", "save-all", "stop") and get a real answer.

Defaults are read from runs/server/server.properties and the RCON_PASSWORD env
var, so the usual call is simply:

    python3 tools/rcon.py list seed
    python3 tools/rcon.py --host 127.0.0.1 --port 25575 --password secret stop

Multiple commands are executed in order; the responses are printed to stdout.
Exit status is non-zero on connection/auth/protocol failure.
"""
import argparse
import os
import socket
import struct
import sys
import time

AUTH = 3
EXEC = 2
RESPONSE = 0
MAX_PACKET = 1024 * 1024


class RconError(Exception):
    pass


def _packet(request_id, kind, payload):
    body = payload.encode("utf-8", "replace") + b"\x00\x00"
    # Size counts id + type + payload + the two terminators = len(payload) + 10.
    # Minecraft's RconClient checks size == bytes_read - 4 on every packet.
    size = 4 + 4 + len(body)
    return struct.pack("<iii", size, request_id, kind) + body


def _recv_exact(sock, count):
    data = bytearray()
    while len(data) < count:
        chunk = sock.recv(count - len(data))
        if not chunk:
            raise RconError("connection closed by server")
        data.extend(chunk)
    return bytes(data)


def _read(sock):
    length = struct.unpack("<i", _recv_exact(sock, 4))[0]
    if not 10 <= length <= MAX_PACKET:
        raise RconError(f"invalid RCON packet length: {length}")
    request_id, kind = struct.unpack("<ii", _recv_exact(sock, 8))
    # After the 8 id/type bytes, `length` still covers the payload and its two
    # terminators. Reading only length-10 leaves the terminators in the stream
    # and desynchronises every following packet.
    payload = _recv_exact(sock, length - 8).rstrip(b"\x00").decode("utf-8", "replace")
    return request_id, kind, payload


class Rcon:
    def __init__(self, host, port, password, timeout=5.0):
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self.next_id = 0
        self.sock.settimeout(timeout)
        self.next_id += 1
        auth_id = self.next_id
        self.sock.sendall(_packet(auth_id, AUTH, password))
        deadline = time.time() + timeout
        while time.time() < deadline:
            request_id, _kind, _payload = _read(self.sock)
            if request_id == -1:
                raise RconError("RCON authentication failed (bad password)")
            if request_id == auth_id:
                return
        raise RconError("RCON authentication timed out")

    def command(self, text, timeout=10.0):
        self.next_id += 1
        command_id = self.next_id
        self.sock.sendall(_packet(command_id, EXEC, text))
        parts = []
        deadline = time.time() + timeout
        self.sock.settimeout(min(1.0, max(0.1, deadline - time.time())))
        while time.time() < deadline:
            try:
                request_id, _kind, payload = _read(self.sock)
            except socket.timeout:
                if parts:  # trailing packets drained
                    break
                continue
            if request_id != command_id:
                continue  # unrelated broadcast / another client's packet
            if payload:
                parts.append(payload)
            if not payload:  # vanilla's terminating empty response
                break
        self.sock.settimeout(timeout)
        return "\n".join(parts).strip()

    def send_only(self, text):
        """Send a command without reading a reply - `stop` closes the socket first."""
        self.next_id += 1
        self.sock.sendall(_packet(self.next_id, EXEC, text))

    def close(self):
        try:
            self.sock.close()
        except OSError:
            pass


def server_properties(path):
    values = {}
    try:
        with open(path, encoding="utf-8", errors="replace") as handle:
            for line in handle:
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    key, value = line.split("=", 1)
                    values[key.strip()] = value.strip()
    except OSError:
        pass
    return values


def main(argv=None):
    props = server_properties(os.path.join("runs", "server", "server.properties"))
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("commands", nargs="*", help="commands to send (or read stdin)")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=int(props.get("rcon.port", "25575")))
    parser.add_argument("--password", default=os.environ.get("RCON_PASSWORD") or props.get("rcon.password"))
    parser.add_argument("--timeout", type=float, default=5.0)
    parser.add_argument("--no-wait", action="store_true",
                        help="send commands without waiting for a reply (for 'stop')")
    args = parser.parse_args(argv)

    if not args.password:
        parser.error("no RCON password: set rcon.password in runs/server/server.properties or RCON_PASSWORD")
    commands = args.commands
    if not commands:
        commands = [line.strip() for line in sys.stdin if line.strip()]
    if not commands:
        parser.error("no commands given")

    try:
        client = Rcon(args.host, args.port, args.password, args.timeout)
    except (OSError, RconError) as exc:
        print(f"rcon: {exc}", file=sys.stderr)
        return 1

    failed = False
    try:
        for command in commands:
            print(f"> {command}")
            if args.no_wait:
                try:
                    client.send_only(command)
                except OSError as exc:
                    print(f"rcon: {command}: {exc}", file=sys.stderr)
                    failed = True
                    break
                continue
            try:
                answer = client.command(command)
            except (OSError, RconError) as exc:
                print(f"rcon: {command}: {exc}", file=sys.stderr)
                failed = True
                break
            if answer:
                print(answer)
    finally:
        client.close()
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
