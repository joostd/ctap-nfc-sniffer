#!/usr/bin/env python

"""Echo server using the asyncio API."""

import asyncio
from websockets.asyncio.server import serve

import sys
from smartcard.System import readers
from smartcard.util import toHexString, toBytes
from smartcard import CardConnection;

r = readers()
print("Available readers: ", r)

i = 0
if len(sys.argv) > 1:
    i = int(sys.argv[1])
print("Using: %s" % r[i])

connection = r[i].createConnection()
connection.connect()
print("Ready")

async def relay(websocket):
    async for command_apdu in websocket:
        print(f">>> {command_apdu.hex()}")
        data, sw1, sw2 = connection.transmit(list(command_apdu))
        print("<<< {:s} {:02X} {:02X}".format(bytes(data).hex(),sw1, sw2))
        await websocket.send(bytes(data)+bytes([sw1,sw2]))


async def main():
    async with serve(relay, None, 8765) as server:
        await server.serve_forever()


if __name__ == "__main__":
    asyncio.run(main())
