# Developer tool: drive the running app through Windows UI Automation to check screens hands-free.
# Not part of the app. Usage (PowerShell, while the app runs via "mvnw.cmd javafx:run"):
#   . scripts\dev\ui-automation.ps1
#   $main = Find-Window "Virpe Mart Billing 0"
#   Focus-Window $main; Send-Keys "^f"; Send-Keys "sugar"
#   $dlg = Find-Window "Categories"; Invoke-Button $dlg "Close"   # buttons are found by their text
#   Capture-Window $main "C:\temp\screen.png"     # screenshot of any app window, including dialogs
# Buttons that open a dialog: use $b = Find-Element ...; $b.SetFocus(); Send-Keys " " (Invoke would wait for the dialog).
Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes
Add-Type -AssemblyName System.Windows.Forms
Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class Win32Focus {
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
}
"@

$script:Root = [System.Windows.Automation.AutomationElement]::RootElement
$script:TreeScope = [System.Windows.Automation.TreeScope]

function Find-Window([string]$titlePrefix, [int]$timeoutSec = 20) {
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        # Dialogs are nested under their owner window, so search every level.
        $cond = New-Object System.Windows.Automation.PropertyCondition(
            [System.Windows.Automation.AutomationElement]::ControlTypeProperty, [System.Windows.Automation.ControlType]::Window)
        $all = $script:Root.FindAll($script:TreeScope::Descendants, $cond)
        foreach ($w in $all) {
            if ($w.Current.Name.StartsWith($titlePrefix, [StringComparison]::Ordinal)) { return $w }
        }
        Start-Sleep -Milliseconds 300
    }
    throw "Window '$titlePrefix' not found"
}

function Find-Element($parent, [string]$name, [int]$timeoutSec = 10) {
    $cond = New-Object System.Windows.Automation.PropertyCondition([System.Windows.Automation.AutomationElement]::NameProperty, $name)
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        $e = $parent.FindFirst($script:TreeScope::Descendants, $cond)
        if ($e) { return $e }
        Start-Sleep -Milliseconds 300
    }
    throw "Element '$name' not found"
}

function Invoke-Button($parent, [string]$name) {
    $b = Find-Element $parent $name
    $b.GetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern).Invoke()
    Start-Sleep -Milliseconds 700
}

function Focus-Window($window) {
    [Win32Focus]::SetForegroundWindow([IntPtr]$window.Current.NativeWindowHandle) | Out-Null
    Start-Sleep -Milliseconds 400
}

Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class Win32Shot {
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT rect);
    [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr hWnd, IntPtr hdc, uint flags);
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
}
"@
[Win32Shot]::SetProcessDPIAware() | Out-Null

function Capture-Window($window, [string]$outFile) {
    $h = [IntPtr]$window.Current.NativeWindowHandle
    $r = New-Object Win32Shot+RECT
    [Win32Shot]::GetWindowRect($h, [ref]$r) | Out-Null
    $bmp = New-Object System.Drawing.Bitmap ($r.Right - $r.Left), ($r.Bottom - $r.Top)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $hdc = $g.GetHdc()
    [Win32Shot]::PrintWindow($h, $hdc, 2) | Out-Null
    $g.ReleaseHdc($hdc); $g.Dispose()
    $bmp.Save($outFile, [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()
    "Saved $outFile"
}

function Send-Keys([string]$keys) {
    [System.Windows.Forms.SendKeys]::SendWait($keys)
    Start-Sleep -Milliseconds 500
}
