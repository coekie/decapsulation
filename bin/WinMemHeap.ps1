# Powershell/C# script that manipulates pointers a JVM process to cause type confusion,
# to bypass JDK encapsulation.
# This script is invoked by decapsulation/WinMemHeap.java

param(
    [Parameter(Mandatory=$true)][UInt32]$processId=0,
    [Parameter(Mandatory=$true)][UInt64]$heapStart=0,
    [Parameter(Mandatory=$true)][UInt64]$heapEnd=0,
    [switch]$useCompressedOops
)


$typeDefinition = @"
using System;
using System.Runtime.InteropServices;

public static partial class WinMemHeap {
    [DllImport("kernel32.dll")]
    public static extern uint GetLastError();

    [DllImport("kernel32.dll")]
    public static extern IntPtr OpenProcess(
        uint dwAccess, bool bInheritHandle, uint dwProcId
    );

    [DllImport("kernel32.dll", SetLastError=true)]
    public static extern Boolean ReadProcessMemory(
        IntPtr hProc, ulong lpBaseAddr,
        byte[] lpBuf, ulong nSize,
        ref UInt32 lpNBytes);

    [DllImport("kernel32.dll")]
    static extern bool WriteProcessMemory(IntPtr hProcess, ulong lpBaseAddress,
        byte[] lpBuffer, UInt32 dwSize, IntPtr lpNBytes);

    /// Returns the index into buf right after the a+b pattern
    private static int FindVictim(byte[] buf) {
        for (int j = 0; j <= buf.Length - 16; j += 8) {
            if (BitConverter.ToUInt64(buf, j) == 0xF108F703F405F602UL
                    && BitConverter.ToUInt64(buf, j + 8) == 0xF207F170E3457833UL) {
                return j + 16;
            }
        }
        throw new Exception("Failed to find victim in memory");
    }

    public static void Go(uint processId, ulong heapStart, ulong heapEnd, bool useCompressedOops) {
        IntPtr hProc = OpenProcess(0x1F0FFF /* PROCESS_ALL_ACCESS */, false, processId);

        uint read = 0;
        ulong heapSize = heapEnd - heapStart;
        // It would have been _nicer_ to split this into smaller reads
        byte[] buf = new byte[heapSize];
        ReadProcessMemory(hProc, heapStart, buf, heapSize, ref read);
        if (read == 0) {
            throw new Exception("ReadProcessMemory failed: " + GetLastError());
        }
        // Trim to what was actually read (heap may contain uncommitted pages at the end)
        Array.Resize(ref buf, (int)read);

        int s = FindVictim(buf);

        if (useCompressedOops) {
            if (BitConverter.ToInt32(buf, s) == 0) s += 4;
            byte[] pointer = new byte[] {buf[s], buf[s+1], buf[s+2], buf[s+3]};
            WriteProcessMemory(hProc, heapStart + (ulong)s + 4, pointer, 4, IntPtr.Zero);
        } else {
            if (BitConverter.ToInt64(buf, s) == 0) s += 8;
            byte[] pointer = new byte[] {buf[s], buf[s+1], buf[s+2], buf[s+3],
                    buf[s+4], buf[s+5], buf[s+6], buf[s+7]};
            WriteProcessMemory(hProc, heapStart + (ulong)s + 8, pointer, 8, IntPtr.Zero);
        }
   }
}
"@

Add-Type -TypeDefinition $typeDefinition -Language CSharp
[WinMemHeap]::Go($processId, $heapStart, $heapEnd, $useCompressedOops)
