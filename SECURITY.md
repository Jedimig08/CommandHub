# Security Policy

## Supported versions

CommandHub is a single-branch hobby project. Security fixes are applied to the latest
commit on [`main`](https://github.com/Jedimig08/CommandHub/tree/main). Older tags are not
patched separately.

## Reporting a vulnerability

Please **do not open a public GitHub issue** for security vulnerabilities.

Instead, report them privately:

- Open a **security advisory** on the repository: *Security → Report a vulnerability*, or
- Email the maintainer at **miguel.s.nadais@gmail.com** (the address on the commits).

Include as much as you can of:

- A description of the issue and its impact
- Steps to reproduce, or a proof of concept
- The affected version, commit, or release tag
- Any suggested fix

I will acknowledge the report, investigate it, and credit you in the fix unless you
prefer otherwise.

## Authentication and transport security

Authentication is currently **disabled** for development. The embedded Ktor server binds
to `0.0.0.0:8080` and does not require a token, password, or session. Any device that can
reach port `8080` on the phone can:

- Read live camera streams and sensor data
- Send data through UART, Bluetooth, and TCP sessions
- Toggle the flashlight
- Read battery, thermal, CPU, and memory telemetry
- Read and change Privacy & Access settings while in `ONLINE` mode

The only remaining access control is the Privacy & Access page:

| Mode         | On-device WebView | Other network clients |
| ------------ | ----------------- | --------------------- |
| `ONLINE`     | allowed           | allowed               |
| `LOCAL_ONLY` | allowed           | blocked               |
| `OFFLINE`    | allowed for `/privacy` only | blocked    |

Traffic is **plain HTTP**, so there is no confidentiality or integrity protection either.

### Mitigations you should take

- Run it on a **trusted, password-protected LAN**; avoid public or guest Wi-Fi.
- Do **not** port-forward `8080` to the internet.
- Use the Privacy & Access page to switch to `LOCAL_ONLY` or `OFFLINE` when remote access
  is not needed. Remember that a remote client in `ONLINE` mode can change that setting.
- Consider a network that isolates IoT and development devices from your main LAN.
- On Android, revoke permissions the app does not need if you are not using that
  subsystem.

### Planned work

Re-introducing authentication and adding TLS/HTTPS are planned before any public or
untrusted-network use. See [COMMANDHUB_PROBLEMS_PRIORITY.md](COMMANDHUB_PROBLEMS_PRIORITY.md)
for the current security backlog.
