# fake U1

`app.py` is the current lightweight fake U1 entry point for M0c/M1 software tests.

## Protocol

- Transport: TCP, default `127.0.0.1:7799`
- Frame: `@{json}\n`
- Commands: current Edge-D `cmd.schema.json`
- Response types: `ack`, `status`, `result`, `error`

`ack`, `device_info`, `status`, `result`, and `error` responses are covered by JSON Schema assertions in `tools/fake_u1/tests/test_app.py`.
`GET_DEVICE_INFO` returns a schema-locked result frame with `model`, `hw_rev`, `fw_rev`, and `workspace_mm`.

## Commands

```bash
rtk python tools/fake_u1/app.py --help
rtk python tools/fake_u1/app.py --port 7799
rtk python tools/fake_u1/app.py --inject E005
rtk python tools/fake_u1/app.py --latency-ms 20
```

Supported injected errors:

- `E001`: home required
- `E005`: limit triggered
- `E006`: homing failed
- `E008`: emergency stop

## Tests

```bash
rtk python -m unittest tools.fake_u1.tests.test_app -v
rtk python tools/fake_u1/test_fake_u1.py -v
```

`fake_u1.py` is the older async simulator kept for compatibility with legacy tests. New integration work should use `app.py`.
