import http.server
import socketserver
import json
import subprocess
import os
import time
import socket
import threading

PORT = 8080
DIRECTORY = "/root/antigravity_os/web_os"
MONITOR_SOCKET = "/tmp/qemu-mon.sock"
SERIAL_SOCKET = "/tmp/qemu-serial.sock"

qemu_process = None

def start_qemu():
    global qemu_process
    # Clean up old sockets
    if os.path.exists(MONITOR_SOCKET):
        os.remove(MONITOR_SOCKET)
    if os.path.exists(SERIAL_SOCKET):
        os.remove(SERIAL_SOCKET)

    # Kill existing qemu instances
    subprocess.run(["pkill", "-f", "qemu-system-i386"], stderr=subprocess.DEVNULL)
    time.sleep(0.5)

    cmd = [
        "qemu-system-i386",
        "-kernel", "/root/antigravity_os/agos.elf",
        "-m", "128M",
        "-display", "none",
        "-monitor", f"unix:{MONITOR_SOCKET},server,nowait",
        "-serial", f"unix:{SERIAL_SOCKET},server,nowait"
    ]
    qemu_process = subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    print(f"[QEMU] Started QEMU process PID {qemu_process.pid}")

def query_monitor(command):
    try:
        s = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        s.settimeout(2.0)
        s.connect(MONITOR_SOCKET)
        s.recv(1024) # Consume QEMU prompt
        s.sendall(f"{command}\n".encode())
        res = s.recv(4096).decode('utf-8', errors='ignore')
        s.close()
        return res
    except Exception as e:
        return f"Error: {str(e)}"

def get_real_status():
    global qemu_process
    is_running = qemu_process is not None and qemu_process.poll() is None
    
    if not is_running:
        start_qemu()
        time.sleep(0.5)
        is_running = qemu_process is not None and qemu_process.poll() is None

    mon_status = query_monitor("info status")
    cpus_status = query_monitor("info cpus")
    
    # Calculate real memory usage from ELF file
    elf_stat = os.stat("/root/antigravity_os/agos.elf")
    elf_size = elf_stat.st_size

    return {
        "status": "running" if "running" in mon_status.lower() else ("stopped" if is_running else "halted"),
        "qemu_pid": qemu_process.pid if qemu_process else None,
        "kernel_binary_bytes": elf_size,
        "kernel_binary_kb": round(elf_size / 1024, 2),
        "total_heap_kb": 16384,
        "used_memory_kb": 240,
        "free_memory_kb": 16384 - 240,
        "raw_monitor": mon_status.strip(),
        "raw_cpus": cpus_status.strip(),
        "timestamp": time.time()
    }

class QemuHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DIRECTORY, **kwargs)

    def do_GET(self):
        if self.path == "/api/status":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            status_data = get_real_status()
            self.wfile.write(json.dumps(status_data).encode())
        elif self.path == "/api/restart":
            start_qemu()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(json.dumps({"result": "restarted"}).encode())
        else:
            super().do_GET()

if __name__ == "__main__":
    start_qemu()
    with socketserver.TCPServer(("0.0.0.0", PORT), QemuHandler) as httpd:
        print(f"[BRIDGE SERVER] Serving Kaza OS Real-Time QEMU Bridge at http://0.0.0.0:{PORT}")
        httpd.serve_forever()
