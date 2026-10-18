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

## Known design limitation: no authentication

The embedded Ktor server binds to `0.0.0.0:8080` and has **no authentication or
authorization**. Any host that can reach the phone's IP address can:

- Read live camera streams and sensor data
- Send data through UART, Bluetooth, and TCP sessions
- Toggle the flashlight
- Read battery, thermal, CPU, and memory telemetry

This is a **known limitation, not an accidental oversight**. CommandHub is designed for
use on trusted local networks — a home lab, workshop, or robotics bench — and is not
meant to be exposed to the public internet.

### Mitigations you should take

- Run it on a **trusted, password-protected LAN**; avoid public or guest Wi-Fi.
- Do **not** port-forward `8080` to the internet.
- Consider a network that isolates IoT and development devices from your main LAN.
- On Android, revoke permissions the app does not need if you are not using that
  subsystem.

### Planned work

Adding authentication and access control is tracked in
[issue #4](https://github.com/Jedimig08/CommandHub/issues/4). Contributions toward it
are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md).
