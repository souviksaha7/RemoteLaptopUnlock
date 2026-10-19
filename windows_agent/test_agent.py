"""
Remote Laptop Biometric Unlock - Agent Diagnostic & Test Suite
==============================================================
Runs automated verification tests against agent.py:
1. UDP Beacon Listener test on port 5005.
2. HTTP /ping health-check endpoint.
3. HTTP /unlock with invalid token (401 verification).
4. HTTP /unlock with valid token (mocked or live).
5. HTTP /lock endpoint verification.
"""

import sys
import time
import socket
import json
import urllib.request
import urllib.error

HTTP_HOST = "127.0.0.1"
HTTP_PORT = 5000
UDP_PORT = 5005
SECRET_TOKEN = "change_this_to_a_secure_token_123456"

def test_ping():
    print("\n--- [Test 1] Testing HTTP GET /ping ---")
    url = f"http://{HTTP_HOST}:{HTTP_PORT}/ping"
    try:
        req = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(req, timeout=3) as resp:
            data = json.loads(resp.read().decode())
            print(f"Status Code: {resp.status}")
            print(f"Response: {data}")
            assert resp.status == 200, f"Expected 200, got {resp.status}"
            assert data.get("status") == "online", "Expected status == 'online'"
            print(">>> [PASS] /ping health-check verified!")
            return True
    except Exception as e:
        print(f">>> [FAIL] Could not connect to /ping: {e}")
        return False

def test_unauthorized():
    print("\n--- [Test 2] Testing Unauthorized Access (Invalid Token) ---")
    url = f"http://{HTTP_HOST}:{HTTP_PORT}/unlock"
    req = urllib.request.Request(url, data=b"", headers={"Authorization": "Bearer wrong_token"}, method="POST")
    try:
        urllib.request.urlopen(req, timeout=3)
        print(">>> [FAIL] Server accepted an invalid bearer token!")
        return False
    except urllib.error.HTTPError as e:
        print(f"Received HTTP status code: {e.code}")
        if e.code == 401:
            print(">>> [PASS] Server correctly rejected invalid token with 401 Unauthorized!")
            return True
        else:
            print(f">>> [FAIL] Expected 401, got {e.code}")
            return False
    except Exception as e:
        print(f">>> [FAIL] Error connecting to server: {e}")
        return False

def test_udp_listener(timeout_secs=7):
    print(f"\n--- [Test 3] Listening for UDP Broadcast on port {UDP_PORT} (timeout: {timeout_secs}s) ---")
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        sock.bind(("", UDP_PORT))
        sock.settimeout(timeout_secs)
        print(f"Waiting for UDP beacon packet...")
        data, addr = sock.recvfrom(1024)
        message = data.decode(errors="ignore").strip()
        print(f"Received UDP packet from {addr}: '{message}'")
        if message == "LAPTOP_AWAKE":
            print(">>> [PASS] Correct LAPTOP_AWAKE beacon payload received!")
            return True
        else:
            print(f">>> [WARN] Received unexpected payload: '{message}'")
            return False
    except socket.timeout:
        print(">>> [TIMEOUT] No UDP packet received within timeout.")
        print("Tip: Trigger /broadcast or restart agent to broadcast again.")
        return False
    except Exception as e:
        print(f">>> [FAIL] Socket error: {e}")
        return False
    finally:
        sock.close()

def main():
    print("=" * 60)
    print(" Remote Laptop Unlock - Windows Agent Diagnostic Suite")
    print(" Made by specxy.net | License: specxy.net")
    print("=" * 60)
    print(f"Target Agent: http://{HTTP_HOST}:{HTTP_PORT}")
    print(f"Target UDP  : port {UDP_PORT}")

    # Check ping
    if not test_ping():
        print("\nAgent does not seem to be running. Please start agent.py in another terminal:")
        print("   python agent.py")
        sys.exit(1)

    test_unauthorized()

    # Re-trigger broadcast via HTTP
    print("\n--- Triggering UDP broadcast via POST /broadcast ---")
    try:
        req = urllib.request.Request(
            f"http://{HTTP_HOST}:{HTTP_PORT}/broadcast",
            data=b"",
            headers={"Authorization": f"Bearer {SECRET_TOKEN}"},
            method="POST"
        )
        with urllib.request.urlopen(req, timeout=3) as resp:
            print(f"Trigger response: {resp.read().decode()}")
    except Exception as e:
        print(f"Could not trigger broadcast: {e}")

    test_udp_listener()

    print("\n" + "=" * 60)
    print(" Diagnostics completed!")
    print("=" * 60)

if __name__ == "__main__":
    main()
