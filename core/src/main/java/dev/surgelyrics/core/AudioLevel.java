package dev.surgelyrics.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Reads the loudness of whatever Windows is playing (the default output's peak meter, the same thing as the
 * volume mixer bars). One hidden PowerShell process prints a number about 50 times a second. Windows only;
 * it exits when Minecraft does, and only runs while the audio-reactive option is on.
 */
public final class AudioLevel {
    private static final String SCRIPT = """
            $ErrorActionPreference = 'Stop'
            $parentPid = %PID%
            [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
            Add-Type -TypeDefinition @"
            using System;
            using System.Runtime.InteropServices;
            [Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
            interface IMMDeviceEnumerator {
              int NotImpl1();
              [PreserveSig] int GetDefaultAudioEndpoint(int dataFlow, int role, out IMMDevice endpoint);
            }
            [Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
            interface IMMDevice {
              [PreserveSig] int Activate(ref Guid iid, int clsCtx, IntPtr activationParams, [MarshalAs(UnmanagedType.IUnknown)] out object iface);
            }
            [Guid("C02216F6-8C67-4B5B-9D00-D008E73E0064"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
            interface IAudioMeterInformation {
              [PreserveSig] int GetChannelsCount(out uint count);
              [PreserveSig] int GetPeakValue(out float peak);
            }
            [ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")] class MMDeviceEnumeratorComObject { }
            public class SurgeMeter {
              static IAudioMeterInformation meter;
              public static bool Init() {
                try {
                  IMMDeviceEnumerator en = (IMMDeviceEnumerator)new MMDeviceEnumeratorComObject();
                  IMMDevice dev; en.GetDefaultAudioEndpoint(0, 1, out dev);
                  Guid iid = new Guid("C02216F6-8C67-4B5B-9D00-D008E73E0064"); object o;
                  dev.Activate(ref iid, 23, IntPtr.Zero, out o);
                  meter = (IAudioMeterInformation)o;
                  return true;
                } catch { meter = null; return false; }
              }
              public static float Peak() {
                float p = 0;
                if (meter != null) { try { meter.GetPeakValue(out p); } catch { meter = null; } }
                return p;
              }
            }
            "@
            function Emit($s) { [Console]::Out.WriteLine($s); [Console]::Out.Flush() }
            if (-not [SurgeMeter]::Init()) { Emit 'err' }
            $n = 0
            $inv = [Globalization.CultureInfo]::InvariantCulture
            while ($true) {
              $n++
              if ($n % 50 -eq 0 -and -not (Get-Process -Id $parentPid -ErrorAction SilentlyContinue)) { break }
              if ($n % 250 -eq 0) { [void][SurgeMeter]::Init() }
              Emit (([SurgeMeter]::Peak()).ToString('0.000', $inv))
              Start-Sleep -Milliseconds 20
            }
            """;

    private volatile float raw;
    private volatile String status = "Off";
    private volatile Process proc;
    private volatile boolean running;
    private Thread thread;

    public static boolean supported() { return WindowsMedia.supported(); }

    public float raw() { return raw; }
    public String status() { return supported() ? status : "Only available on Windows"; }

    /** Start or stop to match the audio-reactive setting. */
    public synchronized void sync(boolean wanted) { if (wanted) start(); else stop(); }

    private void start() {
        if (!supported() || running) return;
        running = true;
        status = "Starting...";
        thread = new Thread(this::loop, "surgelyrics-audio");
        thread.setDaemon(true);
        thread.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { Process p = proc; if (p != null) p.destroy(); }));
    }

    public synchronized void stop() {
        running = false;
        Process p = proc; proc = null;
        if (p != null) p.destroy();
        raw = 0; status = "Off";
    }

    private void loop() {
        int fails = 0;
        while (running && fails < 4) {
            long started = System.nanoTime();
            try {
                String script = SCRIPT.replace("%PID%", String.valueOf(ProcessHandle.current().pid()));
                String enc = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
                Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
                        "-ExecutionPolicy", "Bypass", "-EncodedCommand", enc)
                        .redirectError(ProcessBuilder.Redirect.DISCARD).start();
                proc = p;
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (running && (line = r.readLine()) != null) {
                        Float v = parse(line);
                        if (v != null) { raw = v; status = "Connected"; }
                        else if (line.strip().equals("err")) status = "No audio device found";
                    }
                }
                p.destroy();
            } catch (IOException e) {
                status = "PowerShell not available";
            }
            if (!running) return;
            raw = 0;
            fails = (System.nanoTime() - started > 20_000_000_000L) ? 0 : fails + 1;
            try { Thread.sleep(3000); } catch (InterruptedException e) { return; }
        }
        if (running) status = "Audio meter unavailable";
    }

    /** @return a level 0..1, or null for anything that isn't a number. */
    static Float parse(String line) {
        if (line == null) return null;
        try {
            float v = Float.parseFloat(line.strip());
            return Float.isNaN(v) ? null : Math.max(0f, Math.min(1f, v));
        } catch (NumberFormatException e) { return null; }
    }
}
