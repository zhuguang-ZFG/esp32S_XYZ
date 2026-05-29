# esp32S_XYZ — Top-level build & test automation
# Usage: make <target>
# Run `make help` for available targets

.PHONY: help build-u1 build-u8 flash-u1 flash-u8 test lint clean

# ── Default ──────────────────────────────────────────────
help: ## Show available targets
	@echo "esp32S_XYZ Build System"
	@echo "======================="
	@echo ""
	@echo "Build:"
	@echo "  make build-u1     Build U1 firmware (PlatformIO)"
	@echo "  make build-u8     Build U8 firmware (ESP-IDF)"
	@echo "  make build-server Build Java BusinessServer (Maven)"
	@echo ""
	@echo "Flash:"
	@echo "  make flash-u1     Flash U1 (auto-detect port)"
	@echo "  make flash-u1 PORT=COM3    Flash U1 on specific port"
	@echo "  make flash-u8     Flash U8 (auto-detect port)"
	@echo "  make flash-u8 PORT=COM4    Flash U8 on specific port"
	@echo ""
	@echo "Test:"
	@echo "  make test         Run all tests"
	@echo "  make test-schema  Schema validation"
	@echo "  make test-gpio    GPIO static check"
	@echo "  make test-python  Python unit tests"
	@echo "  make test-fake    Fake integration tests"
	@echo "  make test-java    Java unit tests"
	@echo ""
	@echo "Tools:"
	@echo "  make lint         Lint Python code"
	@echo "  make clean        Clean build artifacts"

# ── Build ────────────────────────────────────────────────
U1_DIR := firmware/u1-grbl
U8_DIR := firmware/u8-xiaozhi
SERVER_DIR := server/xiaozhi-esp32-server/main
TESTS_DIR := tests/ci

build-u1: ## Build U1 firmware
	cd $(U1_DIR) && pio run

build-u8: ## Build U8 firmware (requires ESP-IDF environment)
	cd $(U8_DIR) && idf.py build

build-server: ## Build Java BusinessServer
	cd $(SERVER_DIR)/manager-api && mvn clean package -DskipTests

# ── Flash ────────────────────────────────────────────────
PORT ?=

flash-u1: ## Flash U1 firmware
ifdef PORT
	cd $(U1_DIR) && pio run -t upload --upload-port $(PORT)
else
	cd $(U1_DIR) && pio run -t upload
endif

flash-u8: ## Flash U8 firmware
ifdef PORT
	cd $(U8_DIR) && idf.py -p $(PORT) flash
else
	cd $(U8_DIR) && idf.py flash
endif

monitor-u1: ## Monitor U1 serial output
	cd $(U1_DIR) && pio device monitor -b 115200

monitor-u8: ## Monitor U8 serial output
	cd $(U8_DIR) && idf.py monitor

# ── Test ─────────────────────────────────────────────────
test: test-schema test-gpio test-python test-fake ## Run all tests

test-schema: ## Schema validation
	python tools/validate_schemas.py

test-gpio: ## GPIO static check
	python tools/check_gpio.py

test-python: ## Python unit tests
	python -m pytest $(TESTS_DIR)/ -v --tb=short

test-fake: ## Fake integration tests
	python -m pytest $(TESTS_DIR)/test_fake_integration.py -v

test-java: ## Java unit tests
	cd $(SERVER_DIR)/manager-api && mvn test

# ── Tools ────────────────────────────────────────────────
lint: ## Lint Python code
	ruff check tools/ tests/ --fix
	ruff format tools/ tests/

clean: ## Clean build artifacts
	cd $(U1_DIR) && pio run -t clean 2>/dev/null || true
	rm -rf $(U8_DIR)/build 2>/dev/null || true
	cd $(SERVER_DIR)/manager-api && mvn clean 2>/dev/null || true

# ── Fake simulators ─────────────────────────────────────
fake-u1: ## Start fake U1 simulator
	cd tools/fake_u1 && python app.py

fake-ai: ## Start fake AI simulator
	cd tools/fake_ai && python app.py

fake-server: ## Start fake DeviceServer simulator
	cd tools/fake_device_server && python app.py
