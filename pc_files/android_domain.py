from zeroconf import Zeroconf, ServiceBrowser
import webbrowser
import time

opened = False

class MyListener:
    def add_service(self, zc, type_, name):
        global opened

        info = zc.get_service_info(type_, name)

        if info:
            addresses = info.parsed_addresses()

            if addresses:
                ip = addresses[0]
                url = f"http://{ip}:{info.port}"

                print(f"Found service: {name}")
                print(f"Opening: {url}")

                # prevent opening multiple tabs
                if not opened:
                    opened = True
                    webbrowser.open(url)

    def update_service(self, zc, type_, name):
        pass

    def remove_service(self, zc, type_, name):
        print(f"Service removed: {name}")

zeroconf = Zeroconf()

browser = ServiceBrowser(
    zeroconf,
    "_http._tcp.local.",
    MyListener()
)

print("Searching for local HTTP services...")

try:
    while True:
        time.sleep(1)
except KeyboardInterrupt:
    zeroconf.close()
