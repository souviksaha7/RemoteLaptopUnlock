"""
Remote Laptop Biometric Unlock - Windows Agent
==============================================
Licensed and Maintained by: specxy.net
Official Website: https://specxy.net
==============================================
A robust background service that:
1. Runs silently in the background on startup / system boot.
2. Broadcasts UDP beacons (LAPTOP_AWAKE) on port 5005 across the LAN upon boot.
3. Runs a Flask HTTP server on port 5000.
4. Authenticates incoming unlock/lock requests with a pre-shared Bearer token.
5. Injects simulated input directly into the Windows Hello PIN/Password screen
   using dual execution paths:
   - When running as SYSTEM: Spawns helper into 'WinSta0\\Winlogon' via duplicated winlogon token.
   - When running as User: Uses low-level keybd_event and WScript.Shell native automation.
6. Locks the workstation via LockWorkStation API, WTSDisconnectSession, and rundll32 fallback.
"""

import os
import sys
import time
import socket
import threading
import logging
import ctypes
from ctypes import wintypes
import hmac
import subprocess
from functools import wraps
from pathlib import Path
from flask import Flask, request, jsonify
from dotenv import load_dotenv

# Ensure PyAutoGUI does not trigger fail-safe when mouse is near screen edge
try:
    import pyautogui
    pyautogui.FAILSAFE = False
except Exception:
    pyautogui = None

# ---------------------------------------------------------------------------
# Configuration & Environment Setup
# ---------------------------------------------------------------------------
BASE_DIR = Path(__file__).resolve().parent
ENV_PATH = BASE_DIR / ".env"

if ENV_PATH.exists():
    load_dotenv(dotenv_path=ENV_PATH)

SECRET_TOKEN = os.getenv("SECRET_TOKEN", "change_this_to_a_secure_token_123456")
WINDOWS_PIN = os.getenv("WINDOWS_PIN", "")
WINDOWS_PASSWORD = os.getenv("WINDOWS_PASSWORD", "")
HTTP_PORT = int(os.getenv("HTTP_PORT", 5000))
UDP_PORT = int(os.getenv("UDP_PORT", 5005))
BROADCAST_COUNT = int(os.getenv("BROADCAST_COUNT", 5))
BROADCAST_INTERVAL = float(os.getenv("BROADCAST_INTERVAL", 2.0))
BROADCAST_INITIAL_DELAY = float(os.getenv("BROADCAST_INITIAL_DELAY", 3.0))

AUTHOR = "specxy.net"
LICENSE_NAME = "specxy.net"

# ---------------------------------------------------------------------------
# Logging Configuration
# ---------------------------------------------------------------------------
LOG_FILE = BASE_DIR / "agent.log"
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(threadName)s: %(message)s",
    handlers=[
        logging.StreamHandler(sys.stdout),
        logging.FileHandler(LOG_FILE, encoding="utf-8")
    ]
)
logger = logging.getLogger("WindowsAgent")

# ---------------------------------------------------------------------------
# Helper: Network IP Detection & System Environment
# ---------------------------------------------------------------------------
def get_local_ip() -> str:
    """Discovers the local LAN IP address of this machine."""
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(("8.8.8.8", 80))
            return s.getsockname()[0]
    except Exception:
        try:
            return socket.gethostbyname(socket.gethostname())
        except Exception:
            return "127.0.0.1"

def is_running_as_system() -> bool:
    """Checks whether the current process is running as NT AUTHORITY\\SYSTEM."""
    try:
        import win32api, win32security
        tok = win32security.OpenProcessToken(win32api.GetCurrentProcess(), win32security.TOKEN_QUERY)
        sid, _ = win32security.GetTokenInformation(tok, win32security.TokenUser)
        sid_str = win32security.ConvertSidToStringSid(sid)
        return sid_str == "S-1-5-18"
    except Exception:
        return False

# ---------------------------------------------------------------------------
# UDP Discovery Beacon
# ---------------------------------------------------------------------------
def broadcast_presence(count: int = BROADCAST_COUNT, interval: float = BROADCAST_INTERVAL, initial_delay: float = BROADCAST_INITIAL_DELAY):
    """
    Broadcasts UDP packets containing 'LAPTOP_AWAKE' on the local subnet.
    """
    if initial_delay > 0:
        logger.info(f"UDP Broadcaster: Waiting {initial_delay}s for network interfaces to stabilize...")
        time.sleep(initial_delay)

    lan_ip = get_local_ip()
    logger.info(f"UDP Broadcaster starting. Local LAN IP: {lan_ip}, Target: <broadcast>:{UDP_PORT}")

    payload = b"LAPTOP_AWAKE"

    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP) as sock:
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
            sock.settimeout(1.0)

            for i in range(1, count + 1):
                try:
                    sock.sendto(payload, ("255.255.255.255", UDP_PORT))
                    logger.info(f"[UDP Beacon {i}/{count}] Broadcast packet sent to 255.255.255.255:{UDP_PORT}")
                except Exception as send_err:
                    logger.error(f"[UDP Beacon {i}/{count}] Failed to send broadcast: {send_err}")

                if i < count:
                    time.sleep(interval)

        logger.info("UDP Broadcaster: Finished startup broadcast sequence.")
    except Exception as e:
        logger.error(f"UDP Broadcaster encountered an error: {e}", exc_info=True)

def start_broadcast_thread(initial_delay: float = BROADCAST_INITIAL_DELAY):
    t = threading.Thread(
        target=broadcast_presence,
        kwargs={"count": BROADCAST_COUNT, "interval": BROADCAST_INTERVAL, "initial_delay": initial_delay},
        name="UDP-Broadcaster",
        daemon=True
    )
    t.start()
    return t

# ---------------------------------------------------------------------------
# Lock & Unlock Operations
# ---------------------------------------------------------------------------
def find_winlogon_pid(session_id: int) -> int:
    """Finds the PID of winlogon.exe running in the specified console session."""
    class PROCESSENTRY32(ctypes.Structure):
        _fields_ = [
            ('dwSize', wintypes.DWORD),
            ('cntUsage', wintypes.DWORD),
            ('th32ProcessID', wintypes.DWORD),
            ('th32DefaultHeapID', ctypes.c_size_t),
            ('th32ModuleID', wintypes.DWORD),
            ('cntThreads', wintypes.DWORD),
            ('th32ParentProcessID', wintypes.DWORD),
            ('pcPriClassBase', wintypes.LONG),
            ('dwFlags', wintypes.DWORD),
            ('szExeFile', ctypes.c_wchar * 260)
        ]

    hSnap = ctypes.windll.kernel32.CreateToolhelp32Snapshot(0x00000002, 0)
    if hSnap == -1:
        return 0

    entry = PROCESSENTRY32()
    entry.dwSize = ctypes.sizeof(PROCESSENTRY32)
    target_pid = 0

    if ctypes.windll.kernel32.Process32FirstW(hSnap, ctypes.byref(entry)):
        while True:
            if entry.szExeFile.lower() == 'winlogon.exe':
                pid = entry.th32ProcessID
                proc_session = wintypes.DWORD()
                if ctypes.windll.kernel32.ProcessIdToSessionId(pid, ctypes.byref(proc_session)):
                    if proc_session.value == session_id:
                        target_pid = pid
                        break
            if not ctypes.windll.kernel32.Process32NextW(hSnap, ctypes.byref(entry)):
                break

    ctypes.windll.kernel32.CloseHandle(hSnap)
    return target_pid

def enable_privilege(privilege_name: str):
    """Enables a specific privilege in the current process token."""
    try:
        import win32api, win32security
        hToken = win32security.OpenProcessToken(
            win32api.GetCurrentProcess(),
            win32security.TOKEN_ADJUST_PRIVILEGES | win32security.TOKEN_QUERY
        )
        luid = win32security.LookupPrivilegeValue(None, privilege_name)
        win32security.AdjustTokenPrivileges(hToken, False, [(luid, win32security.SE_PRIVILEGE_ENABLED)])
    except Exception as e:
        logger.debug(f"Could not enable {privilege_name}: {e}")

def execute_unlock_as_system(credential: str) -> bool:
    """
    When running as SYSTEM (Session 0), duplicates the token of winlogon.exe in the
    active console session and spawns winsta0_unlock.py directly on 'WinSta0\\Winlogon'.
    """
    logger.info("Executing unlock from SYSTEM context via Winlogon token duplication...")
    try:
        import win32api, win32security, win32process, win32con

        for priv in ["SeDebugPrivilege", "SeAssignPrimaryTokenPrivilege", "SeIncreaseQuotaPrivilege", "SeTcbPrivilege"]:
            enable_privilege(priv)

        session_id = ctypes.windll.kernel32.WTSGetActiveConsoleSessionId()
        if session_id == 0xFFFFFFFF:
            session_id = 1
        logger.info(f"Target active console session: {session_id}")

        winlogon_pid = find_winlogon_pid(session_id)
        if not winlogon_pid:
            logger.error(f"Could not locate winlogon.exe in session {session_id}")
            return False

        logger.info(f"Found winlogon.exe at PID {winlogon_pid}")

        # Open winlogon process and token
        hProcess = win32api.OpenProcess(win32con.PROCESS_QUERY_INFORMATION | win32con.PROCESS_DUP_HANDLE, False, winlogon_pid)
        hToken = win32security.OpenProcessToken(
            hProcess,
            win32security.TOKEN_DUPLICATE | win32security.TOKEN_ASSIGN_PRIMARY | win32security.TOKEN_QUERY
        )

        # Duplicate token as Primary Token
        duplicated_token = win32security.DuplicateTokenEx(
            hToken,
            win32security.SecurityImpersonation,
            win32con.MAXIMUM_ALLOWED,
            win32security.TokenPrimary
        )

        # Configure startup info targeting WinSta0\Winlogon
        startup_info = win32process.STARTUPINFO()
        startup_info.lpDesktop = r"WinSta0\Winlogon"

        python_exe = sys.executable
        helper_script = str(BASE_DIR / "winsta0_unlock.py")
        cmd_line = f'"{python_exe}" "{helper_script}" "{credential}"'

        creation_flags = win32con.NORMAL_PRIORITY_CLASS | win32con.CREATE_NO_WINDOW

        logger.info(f"Spawning unlock helper into WinSta0\\Winlogon...")
        proc_info = win32process.CreateProcessAsUser(
            duplicated_token,
            None,
            cmd_line,
            None,
            None,
            False,
            creation_flags,
            None,
            str(BASE_DIR),
            startup_info
        )

        child_pid = proc_info[2]
        logger.info(f"Successfully launched winsta0_unlock.py inside WinSta0\\Winlogon! Child PID: {child_pid}")
        return True

    except Exception as e:
        logger.error(f"Failed to launch unlock helper via SYSTEM token: {e}", exc_info=True)
        return False

def execute_unlock_as_user(credential: str) -> bool:
    """
    When running in the user's interactive session, types directly into the login screen
    using low-level Win32 keybd_event and WScript.Shell.
    """
    logger.info(f"Executing unlock in user session for credential ({len(credential)} characters)...")
    try:
        from winsta0_unlock import run_unlock
        run_unlock(credential)
        logger.info("Unlock keystroke sequence executed in user session!")
        return True
    except Exception as e:
        logger.warning(f"Native unlock raised error: {e}. Trying WScript.Shell fallback...")

    try:
        import win32com.client
        ctypes.windll.ole32.CoInitialize(None)
        wsh = win32com.client.Dispatch("WScript.Shell")
        wsh.SendKeys(" ")
        time.sleep(1.0)
        for _ in range(6):
            wsh.SendKeys("{BACKSPACE}")
            time.sleep(0.03)
        time.sleep(0.1)
        wsh.SendKeys(credential)
        time.sleep(0.25)
        wsh.SendKeys("{ENTER}")
        ctypes.windll.ole32.CoUninitialize()
        logger.info("WScript.Shell unlock succeeded!")
        return True
    except Exception as wsh_err:
        logger.error(f"WScript.Shell unlock failed: {wsh_err}", exc_info=True)
        return False

def execute_unlock(credential: str) -> bool:
    """Unified unlock dispatcher: chooses SYSTEM or User execution path."""
    if is_running_as_system():
        return execute_unlock_as_system(credential)
    else:
        return execute_unlock_as_user(credential)

def lock_workstation() -> bool:
    """
    Locks the Windows workstation using LockWorkStation API, WTSDisconnectSession,
    or rundll32 fallback.
    """
    logger.info("Executing workstation lock command...")

    # 1. Try native LockWorkStation
    try:
        res = ctypes.windll.user32.LockWorkStation()
        if res != 0:
            logger.info("LockWorkStation API call succeeded")
            return True
        else:
            err = ctypes.GetLastError()
            logger.warning(f"LockWorkStation returned 0 with GetLastError: {err}")
    except Exception as e:
        logger.warning(f"LockWorkStation call raised: {e}")

    # 2. If running as SYSTEM or error 5, disconnect active console session
    try:
        session_id = ctypes.windll.kernel32.WTSGetActiveConsoleSessionId()
        if session_id != 0xFFFFFFFF:
            logger.info(f"Locking session {session_id} via WTSDisconnectSession...")
            res = ctypes.windll.wtsapi32.WTSDisconnectSession(0, session_id, False)
            if res != 0:
                logger.info(f"WTSDisconnectSession succeeded for session {session_id}")
                return True
    except Exception as we:
        logger.warning(f"WTSDisconnectSession failed: {we}")

    # 3. Fallback: rundll32 user32.dll,LockWorkStation
    try:
        subprocess.run(["rundll32.exe", "user32.dll,LockWorkStation"], check=False)
        logger.info("rundll32 LockWorkStation executed")
        return True
    except Exception as re:
        logger.error(f"rundll32 lock failed: {re}")
        return False

# ---------------------------------------------------------------------------
# Flask HTTP Server & Authentication
# ---------------------------------------------------------------------------
app = Flask(__name__)

def require_auth(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        auth_header = request.headers.get("Authorization", "")
        if not auth_header.startswith("Bearer "):
            logger.warning(f"Unauthorized request from {request.remote_addr}: Missing Bearer token header")
            return jsonify({
                "error": "Unauthorized",
                "message": "Missing Bearer token. Please configure the Secret Token in your Android app to match windows_agent/.env"
            }), 401

        token = auth_header[7:].strip()
        if not hmac.compare_digest(token, SECRET_TOKEN):
            logger.warning(f"Unauthorized request from {request.remote_addr}: Token mismatch! Provided: '{token[:4]}***', Expected: '{SECRET_TOKEN[:4]}***'")
            return jsonify({
                "error": "Unauthorized",
                "message": "Invalid Secret Bearer Token! Ensure the token in the Android app matches SECRET_TOKEN in windows_agent/.env (Default: change_this_to_a_secure_token_123456)"
            }), 401

        return f(*args, **kwargs)
    return decorated

@app.route("/ping", methods=["GET"])
def ping():
    lan_ip = get_local_ip()
    hostname = socket.gethostname()
    as_sys = is_running_as_system()
    return jsonify({
        "status": "online",
        "hostname": hostname,
        "ip": lan_ip,
        "http_port": HTTP_PORT,
        "udp_port": UDP_PORT,
        "running_as_system": as_sys,
        "author": AUTHOR,
        "license": LICENSE_NAME
    }), 200

@app.route("/unlock", methods=["POST"])
@require_auth
def unlock():
    logger.info(f"Unlock request received from {request.remote_addr}")

    # Determine credential: request body takes priority, then WINDOWS_PIN, then WINDOWS_PASSWORD
    credential = ""
    if request.is_json and request.json:
        credential = request.json.get("pin") or request.json.get("password") or ""

    if not credential:
        credential = os.getenv("WINDOWS_PIN") or os.getenv("WINDOWS_PASSWORD") or ""

    if not credential or credential == "YourWindowsPasswordHere":
        logger.warning("Unlock requested, but no valid Windows PIN or Password was found!")
        return jsonify({
            "status": "warning",
            "message": "Windows PIN is not configured! Please type your Windows Hello PIN in the Android app or configure WINDOWS_PIN in windows_agent/.env."
        }), 400

    success = execute_unlock(credential)

    if success:
        return jsonify({
            "status": "unlocked",
            "message": "Unlock command dispatched to Windows login screen.",
            "author": AUTHOR
        }), 200
    else:
        return jsonify({
            "status": "error",
            "message": "Failed to send keystrokes to Windows screen."
        }), 500

@app.route("/lock", methods=["POST"])
@require_auth
def lock():
    logger.info(f"Lock request received from {request.remote_addr}")
    success = lock_workstation()
    if success:
        return jsonify({
            "status": "locked",
            "message": "Workstation locked successfully.",
            "author": AUTHOR
        }), 200
    else:
        return jsonify({
            "status": "error",
            "message": "Could not lock workstation."
        }), 500

@app.route("/broadcast", methods=["POST"])
@require_auth
def trigger_broadcast():
    logger.info(f"Manual broadcast trigger requested by {request.remote_addr}")
    start_broadcast_thread(initial_delay=0.0)
    return jsonify({
        "status": "broadcasting",
        "message": f"Triggered {BROADCAST_COUNT} UDP beacons to port {UDP_PORT}",
        "author": AUTHOR
    }), 200

# ---------------------------------------------------------------------------
# Main Execution Entry Point
# ---------------------------------------------------------------------------
def main():
    lan_ip = get_local_ip()
    as_sys = is_running_as_system()
    logger.info("=" * 60)
    logger.info(" REMOTE LAPTOP BIOMETRIC UNLOCK - WINDOWS AGENT")
    logger.info(f" Made by: {AUTHOR} | License: {LICENSE_NAME}")
    logger.info("=" * 60)
    logger.info(f"Local IP Address : {lan_ip}")
    logger.info(f"HTTP Server Port : {HTTP_PORT}")
    logger.info(f"UDP Beacon Port  : {UDP_PORT}")
    logger.info(f"Running as SYSTEM: {as_sys}")
    logger.info(f"Log File         : {LOG_FILE}")
    if WINDOWS_PIN:
        logger.info(f"Configured PIN   : [Configured - {len(WINDOWS_PIN)} digits]")
    elif WINDOWS_PASSWORD and WINDOWS_PASSWORD != "YourWindowsPasswordHere":
        logger.info("Configured Pass  : [Configured]")
    else:
        logger.warning("! ATTENTION: WINDOWS_PIN is not set in .env.")
        logger.warning("! Set WINDOWS_PIN in .env or enter your PIN in the Android app.")
    logger.info("=" * 60)

    # Launch UDP beacon thread
    start_broadcast_thread(initial_delay=BROADCAST_INITIAL_DELAY)

    # Start Flask HTTP Server
    logger.info(f"Starting HTTP server on 0.0.0.0:{HTTP_PORT}...")
    app.run(host="0.0.0.0", port=HTTP_PORT, debug=False, threaded=True)

if __name__ == "__main__":
    main()
