# EV07B TCP Server

This is a Java (Netty) implementation of the EV07B protocol server.

## Build
```bash
mvn clean package
```

## Run locally
```bash
java -jar target/ev07b-server-1.0.0.jar
```

## Deploy to Railway (no Docker)
1. Push this repo to GitHub (pom.xml at root).
2. Create new project in Railway → Deploy from GitHub.
3. Railway builds with Maven (detected via pom.xml).
4. Set start command in Railway (Settings → Start Command):
   ```
   java -jar target/ev07b-server-1.0.0.jar
   ```
5. Ensure `PORT` env variable is set (Railway provides automatically).  
   The server listens on `0.0.0.0:$PORT` (default 5050).
6. Add TCP Proxy in Railway Networking with internal port `5050`.  
   Railway provides a public domain:port for devices.

## Test
Use `nc` or the included test client to connect and send sample messages.
