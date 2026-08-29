import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { fileURLToPath } from "node:url";
import { WebSocketServer } from "ws";

const here = path.dirname(fileURLToPath(import.meta.url));
const port = Number(process.env.PORT || 9842);
const pin = process.env.PIN || String(1000 + Math.floor(Math.random() * 9000));

const types = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".svg": "image/svg+xml",
};

const server = http.createServer((req, res) => {
  const url = new URL(req.url, `http://127.0.0.1:${port}`);
  if (url.pathname === "/pin") {
    res.writeHead(200, { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" });
    res.end(JSON.stringify({ pin, port }));
    return;
  }
  let file = url.pathname === "/" ? "/index.html" : url.pathname;
  const full = path.normalize(path.join(here, file));
  if (!full.startsWith(here)) {
    res.writeHead(403);
    res.end("forbidden");
    return;
  }
  fs.readFile(full, (error, data) => {
    if (error) {
      res.writeHead(404, { "Content-Type": "text/plain" });
      res.end("Not found");
      return;
    }
    res.writeHead(200, { "Content-Type": types[path.extname(full)] || "application/octet-stream" });
    res.end(data);
  });
});

const sockets = new Set();
const wss = new WebSocketServer({ server, path: "/controller" });
wss.on("connection", (socket) => {
  sockets.add(socket);
  socket.send(JSON.stringify({ type: "welcome", pinRequired: true, serverName: "Playground TV", protocol: 1 }));
  socket.on("message", (data, isBinary) => {
    if (!isBinary) {
      const msg = JSON.parse(String(data));
      if (msg.type === "auth") {
        if (String(msg.pin) === pin || pin === "0000") {
          socket.send(JSON.stringify({ type: "ready", serverName: "Playground TV" }));
        } else {
          socket.send(JSON.stringify({ type: "error", message: "Wrong PIN" }));
        }
      }
      if (msg.type === "state") {
        for (const peer of sockets) {
          if (peer !== socket && peer.readyState === 1) peer.send(String(data));
        }
      }
    } else {
      for (const peer of sockets) {
        if (peer !== socket && peer.readyState === 1) peer.send(data);
      }
    }
  });
  socket.on("close", () => sockets.delete(socket));
});

server.listen(port, "0.0.0.0", () => {
  console.log(`TV Gamepad playground on http://127.0.0.1:${port}/`);
  console.log(`TV view:          http://127.0.0.1:${port}/tv.html`);
  console.log(`PIN: ${pin}`);
});
