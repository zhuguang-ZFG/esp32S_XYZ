/**
 * LiMa Device Client for Wokwi Simulation
 *
 * Connects to LiMa Device Gateway via WiFi, receives motion tasks,
 * simulates stepper motor execution, and reports lifecycle events.
 *
 * Target: ESP32-S3 (matches U1 MOTOR_MCU hardware)
 * Framework: Arduino
 *
 * Protocol: lima-device-v1 (HTTP fallback mode)
 *   POST /device/v1/events  — send events to server
 *   POST /device/v1/tasks   — poll for pending tasks (debug inject)
 *
 * Wokwi peripherals used:
 *   - WiFi: connects to wokwi-gateway (simulated internet)
 *   - LED (GPIO2): status indicator
 *   - Stepper X (GPIO12/14/27): X-axis motion simulation
 *   - Stepper Y (GPIO26/16/17): Y-axis motion simulation
 *   - Serial: debug output
 */

#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>

// ── Configuration ──
const char* WIFI_SSID     = "Wokwi-GUEST";
const char* WIFI_PASSWORD = "";
const char* LIMA_HOST     = "chat.donglicao.com";
const int   LIMA_PORT     = 443;
const char* DEVICE_ID     = "wokwi-u1-sim";
const char* DEVICE_TOKEN  = "lima-local";
const char* FW_REV        = "wokwi-sim-1.0";

// ── Pins ──
const int LED_PIN       = 2;
const int X_STEP_PIN    = 12;
const int X_DIR_PIN     = 14;
const int X_ENABLE_PIN  = 27;
const int Y_STEP_PIN    = 26;
const int Y_DIR_PIN     = 16;
const int Y_ENABLE_PIN  = 17;

// ── State ──
unsigned long last_heartbeat = 0;
unsigned long boot_time      = 0;
bool wifi_connected          = false;
bool server_registered       = false;
String current_task_id       = "";
int    event_count           = 0;

// ── Forward declarations ──
void send_hello();
void send_heartbeat();
void send_motion_event(const char* task_id, const char* phase, int progress, const char* error);
void poll_tasks();
void simulate_motion(const char* capability, int duration_ms);
void step_motor(int step_pin, int dir_pin, int enable_pin, int steps, int delay_us);

// ── Setup ──
void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.println("\n[LiMa-Wokwi] Booting...");

  pinMode(LED_PIN, OUTPUT);
  pinMode(X_STEP_PIN, OUTPUT);
  pinMode(X_DIR_PIN, OUTPUT);
  pinMode(X_ENABLE_PIN, OUTPUT);
  pinMode(Y_STEP_PIN, OUTPUT);
  pinMode(Y_DIR_PIN, OUTPUT);
  pinMode(Y_ENABLE_PIN, OUTPUT);

  digitalWrite(X_ENABLE_PIN, HIGH);
  digitalWrite(Y_ENABLE_PIN, HIGH);

  // Blink LED to show boot
  for (int i = 0; i < 3; i++) {
    digitalWrite(LED_PIN, HIGH); delay(200);
    digitalWrite(LED_PIN, LOW);  delay(200);
  }

  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("[WiFi] Connecting");
  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 30) {
    delay(1000);
    Serial.print(".");
    attempts++;
  }

  if (WiFi.status() == WL_CONNECTED) {
    wifi_connected = true;
    digitalWrite(LED_PIN, HIGH);
    Serial.println("\n[WiFi] Connected: " + WiFi.localIP().toString());
  } else {
    Serial.println("\n[WiFi] Failed. Running offline.");
  }

  boot_time = millis();
  if (wifi_connected) {
    send_hello();
  }
}

// ── Loop ──
void loop() {
  unsigned long now = millis();

  // Heartbeat every 30s
  if (wifi_connected && server_registered && now - last_heartbeat > 30000) {
    send_heartbeat();
    last_heartbeat = now;
  }

  // Poll for tasks every 2s
  if (wifi_connected && server_registered && (now % 2000 < 10)) {
    poll_tasks();
  }

  // Status LED: slow blink = connected, fast blink = running task
  if (!wifi_connected) {
    digitalWrite(LED_PIN, (now / 500) % 2);
  } else if (current_task_id.length() > 0) {
    digitalWrite(LED_PIN, (now / 100) % 2);
  }

  delay(100);
}

// ── HTTP Helpers ──
String lima_post(const char* path, const String& body) {
  if (!wifi_connected) return "";

  HTTPClient http;
  String url = "https://" + String(LIMA_HOST) + path;
  http.begin(url);
  http.addHeader("Content-Type", "application/json");
  http.addHeader("Authorization", "Bearer " + String(DEVICE_TOKEN));

  int code = http.POST(body);
  String response = http.getString();
  http.end();

  Serial.printf("[HTTP] POST %s -> %d (%d bytes)\n", path, code, response.length());
  return response;
}

// ── Protocol Messages ──
void send_hello() {
  StaticJsonDocument<512> doc;
  doc["type"] = "hello";
  doc["device_id"] = DEVICE_ID;
  doc["protocol"] = "lima-device-v1";
  doc["fw_rev"] = FW_REV;

  JsonArray caps = doc.createNestedArray("capabilities");
  caps.add("write_text");
  caps.add("run_path");
  caps.add("home");
  caps.add("stop");
  caps.add("get_device_info");

  String body;
  serializeJson(doc, body);
  String resp = lima_post("/device/v1/events", body);

  if (resp.length() > 0) {
    server_registered = true;
    Serial.println("[LiMa] Registered with server");
  }
}

void send_heartbeat() {
  StaticJsonDocument<256> doc;
  doc["type"] = "heartbeat";
  doc["device_id"] = DEVICE_ID;
  doc["uptime_ms"] = millis() - boot_time;

  String body;
  serializeJson(doc, body);
  lima_post("/device/v1/events", body);
}

void send_motion_event(const char* task_id, const char* phase, int progress, const char* error) {
  StaticJsonDocument<512> doc;
  doc["type"] = "motion_event";
  doc["device_id"] = DEVICE_ID;
  doc["task_id"] = task_id;
  doc["phase"] = phase;
  if (progress >= 0) doc["progress"]["percent"] = progress;
  if (error && strlen(error) > 0) doc["error"] = error;

  event_count++;

  String body;
  serializeJson(doc, body);
  lima_post("/device/v1/events", body);

  Serial.printf("[Event #%d] task=%s phase=%s progress=%d\n",
                event_count, task_id, phase, progress);
}

void poll_tasks() {
  // In HTTP fallback mode, poll the task debug endpoint
  // In production WebSocket mode, tasks arrive via push
  // For Wokwi simulation, we use a simple polling approach
  StaticJsonDocument<256> doc;
  doc["device_id"] = DEVICE_ID;

  String body;
  serializeJson(doc, body);
  String resp = lima_post("/device/v1/tasks", body);

  if (resp.length() < 10) return;

  StaticJsonDocument<1024> result;
  DeserializationError err = deserializeJson(result, resp);
  if (err || !result.containsKey("task_id")) return;

  String task_id = result["task_id"].as<String>();
  String capability = result["capability"] | "run_path";
  String source = result["source"] | "";

  if (task_id.length() == 0) return;

  Serial.printf("[Task] Received: %s (%s)\n", task_id.c_str(), capability.c_str());

  // Phase: accepted
  send_motion_event(task_id.c_str(), "accepted", 0, "");
  delay(200);

  // Phase: running
  current_task_id = task_id;
  send_motion_event(task_id.c_str(), "running", 0, "");
  delay(200);

  // Simulate execution
  simulate_motion(capability.c_str(), 3000);

  // Phase: done
  send_motion_event(task_id.c_str(), "done", 100, "");
  current_task_id = "";
}

void simulate_motion(const char* capability, int duration_ms) {
  int steps = duration_ms / 10;  // 10ms per step
  int progress_step = 100 / 10;  // Report every 10%

  for (int i = 0; i < steps; i++) {
    // X-axis
    digitalWrite(X_DIR_PIN, HIGH);
    digitalWrite(X_ENABLE_PIN, LOW);
    digitalWrite(X_STEP_PIN, HIGH); delayMicroseconds(500);
    digitalWrite(X_STEP_PIN, LOW);  delayMicroseconds(500);

    // Y-axis (alternating)
    if (i % 3 == 0) {
      digitalWrite(Y_DIR_PIN, HIGH);
      digitalWrite(Y_ENABLE_PIN, LOW);
      digitalWrite(Y_STEP_PIN, HIGH); delayMicroseconds(500);
      digitalWrite(Y_STEP_PIN, LOW);  delayMicroseconds(500);
    }

    delay(10);

    // Progress report
    if (i % (steps / 10) == 0 && i > 0) {
      int pct = (i * 100) / steps;
      send_motion_event(current_task_id.c_str(), "progress", pct, "");
    }
  }

  digitalWrite(X_ENABLE_PIN, HIGH);
  digitalWrite(Y_ENABLE_PIN, HIGH);
}
