# Casdoor Local Runtime

This directory contains the local Docker runtime for the `xyyx` Casdoor SSO acceptance environment.

Start:

```powershell
cd C:\Users\Administrator\Desktop\xingyan\deploy\casdoor
docker compose up -d
```

Stop:

```powershell
cd C:\Users\Administrator\Desktop\xingyan\deploy\casdoor
docker compose down
```

Runtime data:

- `conf/casdoor.db` is the local SQLite database and must stay out of Git.
- `logs/` is runtime output and must stay out of Git.

For phone testing, use the workstation LAN URL instead of `localhost`, for example:

```text
http://192.168.1.29:8000
```

Always re-check discovery after changing host/IP:

```powershell
Invoke-WebRequest http://<LAN_IP>:8000/.well-known/openid-configuration -UseBasicParsing
```
