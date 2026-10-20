"""Uji gateway: tangkap sinyal HP, tampilkan RSSI, rata-rata, dan estimasi jarak.
Pasang: pip install bleak   (jalankan di Windows/Linux; macOS tidak membaca data iBeacon)
"""
import asyncio
import uuid
from collections import defaultdict, deque

from bleak import BleakScanner

APP_UUID = uuid.UUID("5A4BCFCE-174E-4BAC-A814-092E77F6B7E5")  # sama dengan di aplikasi
A = -59      # RSSI pada 1 m -> kalibrasi di lokasi
N = 2.5      # path-loss exponent -> kalibrasi di lokasi
WINDOW = 8   # ukuran jendela Moving Average

buf = defaultdict(lambda: deque(maxlen=WINDOW))


def on_adv(device, adv):
    data = adv.manufacturer_data.get(0x004C)
    if not data or len(data) < 23 or data[0] != 0x02 or data[1] != 0x15:
        return
    if uuid.UUID(bytes=bytes(data[2:18])) != APP_UUID:
        return
    emp_id = (int.from_bytes(data[18:20], "big") << 16) | int.from_bytes(data[20:22], "big")
    buf[emp_id].append(adv.rssi)
    avg = sum(buf[emp_id]) / len(buf[emp_id])
    dist = 10 ** ((A - avg) / (10 * N))
    print(f"ID {emp_id} | RSSI {adv.rssi} | avg {avg:.1f} | ~{dist:.1f} m")


async def main():
    async with BleakScanner(on_adv):
        await asyncio.Event().wait()


asyncio.run(main())
