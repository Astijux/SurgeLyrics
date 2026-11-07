package dev.surgelyrics.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Reads whatever Windows says is playing (Spotify, browsers, VLC, ...) from the system media session,
 * the same source as the volume-flyout media card. One long-lived PowerShell process prints one JSON
 * line per second. The script is passed as an encoded command, so no script file is written and the
 * execution policy never applies. It exits by itself if Minecraft dies.
 */
public final class WindowsMedia {
    private static final String SCRIPT = """
            $ErrorActionPreference = 'Stop'
            $parentPid = %PID%
            [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
            function Emit($s) { [Console]::Out.WriteLine($s); [Console]::Out.Flush() }
            try {
              Add-Type -AssemblyName System.Runtime.WindowsRuntime
              $asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
              function Await($op, $type) { $t = $asTask.MakeGenericMethod($type).Invoke($null, @($op)); $t.Wait(-1) | Out-Null; $t.Result }
              $mgrType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
              $propType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime]
              $mgr = Await ($mgrType::RequestAsync()) $mgrType
            } catch { Emit '{"err":"init"}'; exit }
            while (Get-Process -Id $parentPid -ErrorAction SilentlyContinue) {
              try {
                $s = $mgr.GetCurrentSession()
                if ($s) {
                  $p = Await ($s.TryGetMediaPropertiesAsync()) $propType
                  $tl = $s.GetTimelineProperties()
                  $playing = ($s.GetPlaybackInfo().PlaybackStatus -eq 'Playing')
                  $pos = $tl.Position.TotalMilliseconds
                  if ($playing) {
                    $el = ([DateTimeOffset]::UtcNow - $tl.LastUpdatedTime).TotalMilliseconds
                    if ($el -ge 0 -and $el -lt 86400000) { $pos += $el }
                  }
                  $dur = ($tl.EndTime - $tl.StartTime).TotalMilliseconds
                  $o = @{ app = [string]$s.SourceAppUserModelId; title = [string]$p.Title; artist = [string]$p.Artist; album = [string]$p.AlbumTitle; pos = [long]$pos; dur = [long]$dur; playing = [bool]$playing }
                  Emit ($o | ConvertTo-Json -Compress)
                } else { Emit '{}' }
              } catch { Emit '{}' }
              Start-Sleep -Milliseconds 1000
            }
            """;

    private volatile Track track;
    private volatile String status = "Off";
    private volatile Process proc;
    private volatile boolean running;
    private Thread thread;

    public static boolean supported() { return System.getProperty("os.name", "").toLowerCase().contains("win"); }

    public Track track() { return track; }
    public String status() { return status; }
    void injectTrackForTest(Track t) { track = t; }

    public synchronized void start() {
        if (!supported() || running) return;
        running = true;
        thread = new Thread(this::loop, "surgelyrics-winmedia");
        thread.setDaemon(true);
        thread.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { Process p = proc; if (p != null) p.destroy(); }));
    }

    public synchronized void stop() {
        running = false;
        Process p = proc; proc = null;
        if (p != null) p.destroy();
        track = null; status = "Off";
    }

    private void loop() {
        int fails = 0;
        while (running && fails < 5) {
            long started = System.nanoTime();
            try {
                String script = SCRIPT.replace("%PID%", String.valueOf(ProcessHandle.current().pid()));
                String enc = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
                Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
                        "-ExecutionPolicy", "Bypass", "-EncodedCommand", enc)
                        .redirectError(ProcessBuilder.Redirect.DISCARD).start();
                proc = p;
                status = "Starting...";
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (running && (line = r.readLine()) != null) handle(line);
                }
                p.destroy();
            } catch (IOException e) {
                status = "PowerShell not available";
            }
            if (!running) return;
            fails = (System.nanoTime() - started > 20_000_000_000L) ? 0 : fails + 1;
            if (status.equals("Starting...") || status.equals("Connected")) status = "Windows media stopped, retrying";
            try { Thread.sleep(3000); } catch (InterruptedException e) { return; }
        }
        if (running) status = "Windows media unavailable";
    }

    private void handle(String line) {
        try {
            if (line.contains("\"err\"")) { status = "Windows media API unavailable"; track = null; return; }
            Track t = parseLine(line);
            status = "Connected";
            track = t == null ? null : Track.merge(track, t);
        } catch (RuntimeException ignored) { /* garbage line: skip it */ }
    }

    /** @return a Track, or null when nothing (usable) is playing. */
    static Track parseLine(String line) {
        Map<String, Object> m = Json.obj(Json.parse(line.strip()));
        String title = Json.str(m.get("title"), "").strip();
        if (title.isEmpty()) return null;
        String artist = Json.str(m.get("artist"), "").strip();
        return Track.of("win:" + (title + "|" + artist).toLowerCase(), title, artist,
                Json.str(m.get("album"), "").strip(), (long) Json.num(m.get("dur"), 0),
                (long) Json.num(m.get("pos"), 0), Json.bool(m.get("playing"), false));
    }
}
