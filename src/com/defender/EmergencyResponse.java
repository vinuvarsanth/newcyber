package com.defender;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Emergency response system that executes defensive actions
 * when ransomware is detected.
 */
public class EmergencyResponse {
    private static final Logger LOGGER = Logger.getLogger(EmergencyResponse.class.getName());

    private final EmergencyCallback callback;
    private volatile boolean emergencyMode = false;

    public interface EmergencyCallback {
        void onEmergencyStarted();

        void onNetworkDisconnected(boolean success);

        void onSystemShutdownInitiated();

        void onEmergencyComplete();

        void onEmergencyError(Exception e);
    }

    public EmergencyResponse(EmergencyCallback callback) {
        this.callback = callback;
    }

    /**
     * Execute emergency response sequence
     */
    public void executeEmergencyResponse() {
        if (emergencyMode) {
            LOGGER.warning("Emergency response already in progress");
            return;
        }

        emergencyMode = true;
        callback.onEmergencyStarted();
        LOGGER.severe("EXECUTING EMERGENCY RESPONSE - RANSOMWARE DETECTED!");

        CompletableFuture.runAsync(() -> {
            try {
                boolean networkDisconnected = disconnectNetwork();
                callback.onNetworkDisconnected(networkDisconnected);

                if (networkDisconnected) {
                    LOGGER.info("Network disconnected successfully");
                } else {
                    LOGGER.warning("Failed to disconnect network");
                }

                // Wait a moment to let network drop propagate
                Thread.sleep(1000);

                callback.onSystemShutdownInitiated();
                initiateSystemShutdown();

                callback.onEmergencyComplete();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error during emergency response", e);
                callback.onEmergencyError(e);
            } finally {
                emergencyMode = false;
            }
        });
    }

    /**
     * Disconnect from network based on operating system
     */
    private boolean disconnectNetwork() {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("windows")) {
                return disconnectWindowsNetwork();
            } else if (os.contains("linux") || os.contains("unix")) {
                return disconnectLinuxNetwork();
            } else {
                LOGGER.warning("Unsupported operating system for network disconnection: " + os);
                return false;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to disconnect network", e);
            return false;
        }
    }

    /**
     * Disconnect network on Windows.
     * Strategy:
     * 1) ipconfig /release to drop DHCP leases (quick drop on typical setups).
     * 2) If not successful, enumerate interfaces and disable them via netsh.
     */
    private boolean disconnectWindowsNetwork() throws IOException, InterruptedException {
        LOGGER.info("Disconnecting Windows network...");

        // 1) Try ipconfig /release (Windows official)
        ProcessResult release = runCommand(Duration.ofSeconds(10), "ipconfig", "/release");
        if (release.finished && release.exitCode == 0) {
            LOGGER.info("Windows network disconnected via ipconfig /release");
            return true;
        } else {
            LOGGER.warning("ipconfig /release did not succeed or timed out; attempting to disable adapters via netsh");
        }

        // 2) Fallback: disable all non-loopback adapters using netsh
        ProcessResult show = runCommand(Duration.ofSeconds(10), "netsh", "interface", "show", "interface");
        if (!show.finished || show.exitCode != 0) {
            LOGGER.warning("Failed to enumerate interfaces via netsh");
            return false;
        }

        List<String> names = parseInterfaceNamesFromNetsh(show.stdout);
        boolean allOk = true;
        for (String name : names) {
            // Try to disable each interface
            ProcessResult disable = runCommand(Duration.ofSeconds(8),
                    "netsh", "interface", "set", "interface", "name=" + name, "admin=disabled");
            if (!disable.finished || disable.exitCode != 0) {
                LOGGER.warning("Failed to disable interface via netsh: " + name);
                allOk = false;
            } else {
                LOGGER.info("Disabled interface: " + name);
            }
        }
        return allOk && !names.isEmpty();
    }

    /**
     * Extract interface names from 'netsh interface show interface' output by
     * column start of "Interface Name".
     */
    private List<String> parseInterfaceNamesFromNetsh(String output) {
        List<String> names = new ArrayList<>();
        if (output == null)
            return names;
        String[] lines = output.split("\\R");
        int nameStartIdx = -1;
        boolean headerPassed = false;

        for (String line : lines) {
            if (line == null || line.trim().isEmpty())
                continue;
            if (!headerPassed) {
                int idx = line.indexOf("Interface Name");
                if (idx >= 0) {
                    nameStartIdx = idx;
                    headerPassed = true;
                }
                continue;
            }
            // Skip dashed separator line
            if (line.trim().startsWith("---"))
                continue;

            if (nameStartIdx >= 0 && line.length() > nameStartIdx) {
                String name = line.substring(nameStartIdx).trim();
                if (name.isEmpty())
                    continue;
                // Skip loopback pseudo interfaces if present
                if (name.toLowerCase().contains("loopback"))
                    continue;
                names.add(name);
            }
        }
        return names;
    }

    /**
     * Disconnect network on Linux/Kali.
     * Strategy:
     * 1) If nmcli exists, use: nmcli networking off
     * 2) Else, bring down all non-loopback interfaces via ip link set dev <iface>
     * down
     * 3) Else, fallback to ifconfig <iface> down
     */
    private boolean disconnectLinuxNetwork() throws IOException, InterruptedException {
        LOGGER.info("Disconnecting Linux network...");

        // 1) Prefer NetworkManager control if available
        if (commandExists("nmcli")) {
            ProcessResult nm = runCommand(Duration.ofSeconds(10), "nmcli", "networking", "off");
            if (nm.finished && nm.exitCode == 0) {
                LOGGER.info("Linux network disconnected using nmcli networking off");
                return true;
            } else {
                LOGGER.warning("nmcli networking off failed or timed out, will try interface-level down");
            }
        }

        // 2) Try iproute2: ip -o link show + ip link set dev <iface> down
        String ipCmd = resolveLinuxCmd("ip");
        if (ipCmd != null) {
            ProcessResult list = runCommand(Duration.ofSeconds(8), ipCmd, "-o", "link", "show");
            if (list.finished && list.exitCode == 0) {
                List<String> ifaces = parseInterfacesFromIpLink(list.stdout);
                boolean allOk = bringDownInterfacesWithIp(ipCmd, ifaces);
                if (allOk && !ifaces.isEmpty()) {
                    LOGGER.info("Linux network interfaces brought down via ip link");
                    return true;
                }
            } else {
                LOGGER.warning("Failed to list interfaces with ip link");
            }
        }

        // 3) Fallback to ifconfig
        String ifconfigCmd = resolveLinuxCmd("ifconfig");
        if (ifconfigCmd != null) {
            ProcessResult list = runCommand(Duration.ofSeconds(8), ifconfigCmd, "-a");
            if (list.finished && list.exitCode == 0) {
                List<String> ifaces = parseInterfacesFromIfconfig(list.stdout);
                boolean allOk = bringDownInterfacesWithIfconfig(ifconfigCmd, ifaces);
                if (allOk && !ifaces.isEmpty()) {
                    LOGGER.info("Linux network interfaces brought down via ifconfig");
                    return true;
                }
            } else {
                LOGGER.warning("Failed to list interfaces with ifconfig -a");
            }
        }

        LOGGER.warning("Unsupported Linux environment for network disconnect (no nmcli/ip/ifconfig usable)");
        return false;
    }

    private boolean bringDownInterfacesWithIp(String ipCmd, List<String> ifaces)
            throws IOException, InterruptedException {
        boolean allOk = true;
        for (String iface : ifaces) {
            if ("lo".equals(iface))
                continue;
            ProcessResult down = runCommand(Duration.ofSeconds(5), ipCmd, "link", "set", "dev", iface, "down");
            if (!down.finished || down.exitCode != 0) {
                LOGGER.warning("Failed to bring down iface via ip: " + iface);
                allOk = false;
            } else {
                LOGGER.info("Interface down (ip): " + iface);
            }
        }
        return allOk;
    }

    private boolean bringDownInterfacesWithIfconfig(String ifconfigCmd, List<String> ifaces)
            throws IOException, InterruptedException {
        boolean allOk = true;
        for (String iface : ifaces) {
            if ("lo".equals(iface))
                continue;
            ProcessResult down = runCommand(Duration.ofSeconds(5), ifconfigCmd, iface, "down");
            if (!down.finished || down.exitCode != 0) {
                LOGGER.warning("Failed to bring down iface via ifconfig: " + iface);
                allOk = false;
            } else {
                LOGGER.info("Interface down (ifconfig): " + iface);
            }
        }
        return allOk;
    }

    private List<String> parseInterfacesFromIpLink(String output) {
        List<String> names = new ArrayList<>();
        if (output == null)
            return names;
        String[] lines = output.split("\\R");
        for (String line : lines) {
            // Format: "2: eth0: <...>"
            int firstColon = line.indexOf(':');
            if (firstColon < 0)
                continue;
            int secondColon = line.indexOf(':', firstColon + 1);
            if (secondColon < 0)
                continue;
            String name = line.substring(firstColon + 1, secondColon).trim();
            // After first colon there's a space then ifname; sometimes includes '@'
            // Split on spaces then take first token
            String[] parts = name.split("\\s+");
            if (parts.length > 0) {
                String iface = parts[0];
                if (iface.contains("@"))
                    iface = iface.substring(0, iface.indexOf('@'));
                if (!iface.isEmpty())
                    names.add(iface);
            }
        }
        return names;
    }

    private List<String> parseInterfacesFromIfconfig(String output) {
        List<String> names = new ArrayList<>();
        if (output == null)
            return names;
        String[] lines = output.split("\\R");
        for (String line : lines) {
            if (line.isEmpty())
                continue;
            // Interface lines typically start at column 0 with "eth0: flags=..." or "wlan0:
            // ..."
            if (!Character.isWhitespace(line.charAt(0))) {
                String[] tokens = line.split(":");
                if (tokens.length > 0) {
                    String firstField = tokens[0].trim();
                    if (!firstField.isEmpty()) {
                        names.add(firstField);
                    }
                }
            }
        }
        return names;
    }

    private String resolveLinuxCmd(String cmd) throws IOException, InterruptedException {
        // Try plain name
        if (commandExists(cmd))
            return cmd;
        // Try /usr/sbin and /sbin for administrative tools
        String abs1 = "/usr/sbin/" + cmd;
        String abs2 = "/sbin/" + cmd;
        if (fileExists(abs1))
            return abs1;
        if (fileExists(abs2))
            return abs2;
        return null;
    }

    private boolean commandExists(String cmd) throws IOException, InterruptedException {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("windows")) {
            ProcessResult r = runCommand(Duration.ofSeconds(5), "where", cmd);
            return r.finished && r.exitCode == 0 && r.stdout != null && !r.stdout.trim().isEmpty();
        } else {
            ProcessResult r = runCommand(Duration.ofSeconds(5), "which", cmd);
            return r.finished && r.exitCode == 0;
        }
    }

    private boolean fileExists(String absolutePath) throws IOException, InterruptedException {
        ProcessResult r = runCommand(Duration.ofSeconds(3), "test", "-x", absolutePath);
        return r.finished && r.exitCode == 0;
    }

    /**
     * Initiate system shutdown
     */
    private void initiateSystemShutdown() throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        LOGGER.severe("INITIATING EMERGENCY SYSTEM SHUTDOWN");

        List<String> cmd = new ArrayList<>();
        if (os.contains("windows")) {
            // Immediate forced shutdown with comment
            cmd.add("shutdown");
            cmd.add("/s");
            cmd.add("/t");
            cmd.add("0");
            cmd.add("/f");
            cmd.add("/c");
            cmd.add("Emergency shutdown - Ransomware detected");
        } else if (os.contains("linux") || os.contains("unix")) {
            // Immediate shutdown with a WALL message
            cmd.add("shutdown");
            cmd.add("now");
            cmd.add("Emergency shutdown - Ransomware detected");
        } else {
            LOGGER.severe("Unsupported operating system for shutdown: " + os);
            return;
        }

        try {
            new ProcessBuilder(cmd).start();
            LOGGER.info("System shutdown command executed");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to execute shutdown command", e);
            throw e;
        }
    }

    /**
     * Test network disconnection capability without actually disconnecting
     */
    public boolean testNetworkDisconnection() {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("windows")) {
                ProcessResult r = runCommand(Duration.ofSeconds(5), "ipconfig", "/?");
                return r.finished && r.exitCode == 0;
            } else if (os.contains("linux") || os.contains("unix")) {
                if (commandExists("nmcli"))
                    return true;
                if (commandExists("ip"))
                    return true;
                return commandExists("ifconfig");
            } else {
                return false;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to test network disconnection capabilities", e);
            return false;
        }
    }

    public boolean isEmergencyMode() {
        return emergencyMode;
    }

    // -------- Helper to run commands with timeout and capture output --------

    private static class ProcessResult {
        final boolean finished;
        final int exitCode;
        final String stdout;
        final String stderr;

        ProcessResult(boolean finished, int exitCode, String stdout, String stderr) {
            this.finished = finished;
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }

    private ProcessResult runCommand(Duration timeout, String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        Process p = pb.start();

        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();

        Thread tout = new Thread(() -> readStream(p.getInputStream(), out));
        Thread terr = new Thread(() -> readStream(p.getErrorStream(), err));
        tout.start();
        terr.start();

        boolean finished = p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            p.destroyForcibly();
        }
        // Ensure we consumed streams
        try {
            tout.join(100);
        } catch (InterruptedException ignored) {
        }
        try {
            terr.join(100);
        } catch (InterruptedException ignored) {
        }

        int code = finished ? p.exitValue() : -1;
        return new ProcessResult(finished, code, out.toString(), err.toString());
    }

    private void readStream(java.io.InputStream is, StringBuilder sb) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append(System.lineSeparator());
            }
        } catch (IOException ignored) {
        }
    }
}
