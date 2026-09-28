# Powershell/C# script that scans a JVM process's memory for bytecode of a known method
# and patches it to bypass JDK encapsulation.
# This script is invoked by decapsulation/WinMemBytecode.java

param(
    [Parameter(Mandatory=$true)][UInt32]$processId=0
)


$typeDefinition = @"
using System;
using System.Runtime.InteropServices;
using System.Collections.Generic;

public static partial class WinMemBytecode {
    [StructLayout(LayoutKind.Sequential)]
    public struct MEMORY_BASIC_INFORMATION64 {
        public ulong BaseAddress;
        public ulong AllocationBase;
        public int AllocationProtect;
        public int __alignment1;
        public ulong RegionSize;
        public int State;
        public int Protect;
        public int Type;
        public int __alignment2;
    }

    [DllImport("kernel32.dll")]
    public static extern IntPtr OpenProcess(
        uint dwAccess, bool bInheritHandle, uint dwProcId);

    [DllImport("kernel32.dll")]
    public static extern int VirtualQueryEx(
        IntPtr hProc, ulong lpAddr, out MEMORY_BASIC_INFORMATION64 lpBuf, int dwLen);

    [DllImport("kernel32.dll")]
    public static extern Boolean ReadProcessMemory(
        IntPtr hProc, ulong lpBaseAddr, byte[] lpBuf, UInt32 nSize, ref UInt32 lpNBytes);

    [DllImport("kernel32.dll")]
    static extern bool WriteProcessMemory(IntPtr hProcess, ulong lpBaseAddress,
        byte[] lpBuffer, UInt32 dwSize, IntPtr lpNBytes);

    private static List<MEMORY_BASIC_INFORMATION64> ListMemoryRegions(IntPtr hProc) {
        ulong maxAddr = 0x00007FFFFFFFFFFF;
        ulong addr = 0;
        List<MEMORY_BASIC_INFORMATION64> regions = new List<MEMORY_BASIC_INFORMATION64>();

        do
        {
            MEMORY_BASIC_INFORMATION64 m;
            if (VirtualQueryEx(hProc, addr, out m, Marshal.SizeOf<MEMORY_BASIC_INFORMATION64>()) == 0) {
                break;
            }
            addr = m.BaseAddress + m.RegionSize;
            regions.Add(m);
        } while (addr <= maxAddr);
        return regions;
    }

    public static void Go(uint processId) {
        IntPtr hProc = OpenProcess(0x1F0FFF /* PROCESS_ALL_ACCESS */, false, processId);
        List<MEMORY_BASIC_INFORMATION64> regions = ListMemoryRegions(hProc);

        // Implementation of setAllowedModes(), in bytecode format (see WinMemBytecode.java)
        byte[] search = new byte[] {
            0x05, 0x3d, // x=2 (iconst_2, istore_2)
            0x1c, 0x1c, 0x68, // x*x (iload_2, iload_2, imul)
            0x1c, 0x82, // ^x (iload_2, ixor)
            0x1c, 0x80, // |x (iload_2, ior)
            0x1c, 0x60, // +x (iload_2, iadd)
            0x1c, 0x6c, // /x (iload_2, idiv)
            0x3d, // x=... (istore_2)
            0x2a  // dontLookup (aload_0)
        };

        foreach (MEMORY_BASIC_INFORMATION64 region in regions) {
            // only consider committed read-write private regions (metaspace is MEM_PRIVATE)
            if (region.State != 0x1000 /* MEM_COMMIT */ ||
                region.Protect != 0x04 /* PAGE_READWRITE */ ||
                region.Type != 0x20000 /* MEM_PRIVATE */ ||
                region.RegionSize >= 32000000) { // optimization: code lives in small metaspace chunks
                continue;
            }

            uint read = 0;
            byte[] buf = new byte[region.RegionSize];
            ReadProcessMemory(hProc, region.BaseAddress, buf, (uint)buf.Length, ref read);
            if (read == 0) continue;

            int searchPos = 0;
            for (int i = 0; i < buf.Length; i++) {
                if (buf[i] == search[searchPos]) {
                    searchPos++;
                    if (searchPos == search.Length) {
                        // found a match: patch aload_0 (0x2a) -> aload_1 (0x2b)
                        WriteProcessMemory(hProc, region.BaseAddress + (ulong)i, new byte[] { 0x2b }, 1, IntPtr.Zero);
                        searchPos = 0;
                    }
                } else {
                    searchPos = (buf[i] == search[0]) ? 1 : 0;
                }
            }
        }
    }
}
"@

Add-Type -TypeDefinition $typeDefinition -Language CSharp
[WinMemBytecode]::Go($processId)
