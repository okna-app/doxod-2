#!/usr/bin/env python3
"""Generate a Depter Pro activation code.

Usage:
  python3 depter_license.py --key depter_license_private.pem --id A1B2-C3D4-E5F6-0718 --days 30
  python3 depter_license.py --key depter_license_private.pem --id A1B2C3D4E5F60718 --forever

The private key never goes into the app or the git repository. Keep it safe:
whoever has it can create Pro codes.
"""
import argparse
import base64
import datetime as dt
import sys

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.asymmetric.utils import decode_dss_signature

PREFIX = "DPT1-"
FOREVER = 0xFFFF


def make_code(private_pem: bytes, install_id: str, expiry_day: int, plan: int = 1) -> str:
    hex_id = "".join(c for c in install_id if c.isalnum()).upper()
    if len(hex_id) != 16:
        raise ValueError("install id must be 16 hex characters")
    payload = bytes([1]) + bytes.fromhex(hex_id) + expiry_day.to_bytes(2, "big") + bytes([plan])
    key = serialization.load_pem_private_key(private_pem, password=None)
    der = key.sign(payload, ec.ECDSA(hashes.SHA256()))
    r, s = decode_dss_signature(der)
    raw = r.to_bytes(32, "big") + s.to_bytes(32, "big")
    return PREFIX + base64.urlsafe_b64encode(payload + raw).decode().rstrip("=")


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--key", required=True)
    p.add_argument("--id", required=True, help="install id shown on the Pro screen")
    g = p.add_mutually_exclusive_group(required=True)
    g.add_argument("--days", type=int)
    g.add_argument("--until", help="YYYY-MM-DD, inclusive")
    g.add_argument("--forever", action="store_true")
    a = p.parse_args()
    epoch = dt.date(1970, 1, 1)
    if a.forever:
        expiry = FOREVER
    elif a.until:
        expiry = (dt.date.fromisoformat(a.until) - epoch).days
    else:
        expiry = (dt.date.today() + dt.timedelta(days=a.days - 1) - epoch).days
    with open(a.key, "rb") as f:
        print(make_code(f.read(), a.id, expiry))
    return 0


if __name__ == "__main__":
    sys.exit(main())
