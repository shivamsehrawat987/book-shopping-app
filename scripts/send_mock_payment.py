import argparse
import getpass
import hashlib
import hmac
import json
import time
import urllib.error
import urllib.request
import uuid


def main():
    parser = argparse.ArgumentParser(description="Send a signed LOCAL simulated payment event. No real money is moved.")
    parser.add_argument("intent_id", help="mock_intent_ID returned by checkout")
    parser.add_argument("amount_minor", type=int, help="Order total in paise: INR 40.00 = 4000")
    parser.add_argument("--result", choices=["succeeded", "payment_failed"], default="succeeded")
    parser.add_argument("--event-id", default=None, help="Reuse an event ID to test duplicate delivery")
    parser.add_argument("--port", type=int, default=8081)
    args = parser.parse_args()
    if not args.intent_id.startswith("mock_intent_") or args.amount_minor <= 0 or not 1 <= args.port <= 65535:
        parser.error("Provide a mock intent, positive amount and valid local port")

    secret = getpass.getpass("Mock webhook signing secret (from local server configuration): ").encode("utf-8")
    if len(secret) < 32:
        parser.error("Signing secret must contain at least 32 bytes")
    event_id = args.event_id or "evt_mock_" + uuid.uuid4().hex
    payload = json.dumps({
        "eventId": event_id,
        "type": "payment_intent." + args.result,
        "paymentIntentId": args.intent_id,
        "amountMinor": args.amount_minor,
        "currency": "INR",
    }, separators=(",", ":")).encode("utf-8")
    timestamp = str(int(time.time()))
    signature = hmac.new(secret, timestamp.encode("ascii") + b"." + payload, hashlib.sha256).hexdigest()
    request = urllib.request.Request(
        f"http://localhost:{args.port}/api/payments/webhook",
        data=payload,
        headers={"Content-Type": "application/json", "X-Mock-Timestamp": timestamp, "X-Mock-Signature": signature},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            print(f"HTTP {response.status}; simulated event {event_id}")
    except urllib.error.HTTPError as error:
        print(f"HTTP {error.code}; simulated event {event_id}")
        print(error.read().decode("utf-8"))
        raise SystemExit(1) from error
    except urllib.error.URLError as error:
        raise SystemExit(f"Cannot reach local application: {error.reason}") from error


if __name__ == "__main__":
    main()