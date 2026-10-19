' ==============================================================================
' Remote Laptop Biometric Unlock - Silent Windows Launcher
' ==============================================================================
' This script launches the Python agent using pythonw.exe (windowless python)
' with no command prompt or terminal window visible to the user.
' ==============================================================================

Option Explicit

Dim objShell, objFSO, strScriptDir, strCommand

Set objShell = CreateObject("WScript.Shell")
Set objFSO = CreateObject("Scripting.FileSystemObject")

' Get the absolute path to the directory containing this script
strScriptDir = objFSO.GetParentFolderName(WScript.ScriptFullName)

' Change the working directory so agent.py can resolve .env and relative paths
objShell.CurrentDirectory = strScriptDir

' Command to execute pythonw.exe silently
' pythonw.exe executes Python scripts without opening a console window
strCommand = "cmd.exe /c pythonw agent.py"

' Run the command hidden (0 = hide window, False = don't wait for completion)
objShell.Run strCommand, 0, False

Set objShell = Nothing
Set objFSO = Nothing
