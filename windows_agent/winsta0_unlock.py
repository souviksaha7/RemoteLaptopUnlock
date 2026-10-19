"""
Remote Laptop Biometric Unlock - Winlogon Keystroke Injector
============================================================
Licensed and Maintained by: specxy.net
Official Website: https://specxy.net
============================================================
This script is executed natively inside the 'WinSta0\\Winlogon' desktop
under Session 1 (the physical console session) to simulate user input
directly on the Windows Hello PIN and Password login screen.
"""

import sys
import time
import ctypes
from ctypes import wintypes
from pathlib import Path

LOG_FILE = Path(__file__).resolve().parent / "unlock_helper.log"

def log(msg: str):
    timestamp = time.strftime("%Y-%m-%d %H:%M:%S")
    line = f"{timestamp} [WinlogonHelper] {msg}\n"
    try:
        with open(LOG_FILE, "a", encoding="utf-8") as f:
            f.write(line)
    except Exception:
        pass

# Key Constants
VK_SPACE = 0x20
VK_BACK = 0x08
VK_RETURN = 0x0D
KEYEVENTF_KEYUP = 0x0002
KEYEVENTF_UNICODE = 0x0004

def press_vk(vk: int):
    """Sends keydown and keyup for a virtual key code."""
    ctypes.windll.user32.keybd_event(vk, 0, 0, 0)
    time.sleep(0.04)
    ctypes.windll.user32.keybd_event(vk, 0, KEYEVENTF_KEYUP, 0)
    time.sleep(0.04)

def press_char(ch: str):
    """Sends a character via virtual key code or Unicode event."""
    if ch.isdigit():
        press_vk(ord(ch))
    else:
        vk = ctypes.windll.user32.VkKeyScanW(ord(ch)) & 0xFF
        if vk != 0xFF:
            press_vk(vk)
        else:
            ctypes.windll.user32.keybd_event(0, ord(ch), KEYEVENTF_UNICODE, 0)
            time.sleep(0.04)
            ctypes.windll.user32.keybd_event(0, ord(ch), KEYEVENTF_UNICODE | KEYEVENTF_KEYUP, 0)
            time.sleep(0.04)

def run_unlock(credential: str):
    log(f"Starting unlock sequence for credential ({len(credential)} chars)...")

    # 1. Wake the display with synthetic mouse movement
    try:
        ctypes.windll.user32.mouse_event(0x0001, 2, 2, 0, 0)
        time.sleep(0.05)
        ctypes.windll.user32.mouse_event(0x0001, -2, -2, 0, 0)
        time.sleep(0.05)
        log("Dispatched mouse wake events")
    except Exception as e:
        log(f"Mouse wake error: {e}")

    # 2. Press Spacebar to raise the lock screen curtain
    press_vk(VK_SPACE)
    log("Dispatched Spacebar to raise lock screen curtain")

    # 3. Wait for lock screen transition animation to reveal PIN field
    time.sleep(1.2)

    # 4. Clear any stray character with backspaces
    for _ in range(6):
        press_vk(VK_BACK)
        time.sleep(0.03)
    time.sleep(0.1)
    log("Dispatched backspaces to clear input field")

    # 5. Type the PIN / Password digits
    log("Injecting credential characters...")
    for ch in credential:
        press_char(ch)
        time.sleep(0.05)

    time.sleep(0.25)

    # 6. Press Enter to submit login
    press_vk(VK_RETURN)
    log("Dispatched Enter key to submit login")
    time.sleep(0.3)

    log("Unlock sequence completed successfully!")

if __name__ == "__main__":
    if len(sys.argv) > 1:
        pin = sys.argv[1]
    else:
        pin = ""
    if pin:
        run_unlock(pin)
    else:
        log("No credential supplied to winsta0_unlock.py")
