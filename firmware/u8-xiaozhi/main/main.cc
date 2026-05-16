#include <esp_log.h>
#include <esp_err.h>
#include <nvs.h>
#include <nvs_flash.h>
#include <esp_partition.h>
#include <string.h>
#include <driver/gpio.h>
#include <esp_event.h>
#include <freertos/FreeRTOS.h>
#include <freertos/task.h>

#include "application.h"

#define TAG "main"

static esp_err_t InitNvsStorage()
{
#ifdef CONFIG_NVS_ENCRYPTION
    const esp_partition_t* key_partition = esp_partition_find_first(
        ESP_PARTITION_TYPE_DATA,
        ESP_PARTITION_SUBTYPE_DATA_NVS_KEYS,
        nullptr);
    if (key_partition == nullptr) {
        ESP_LOGE(TAG, "NVS encryption is enabled but nvs_keys partition is missing");
        return ESP_ERR_NOT_FOUND;
    }

    nvs_sec_cfg_t cfg = {};
    esp_err_t ret = nvs_flash_read_security_cfg(key_partition, &cfg);
    if (ret == ESP_ERR_NVS_KEYS_NOT_INITIALIZED || ret == ESP_ERR_NVS_CORRUPT_KEY_PART) {
        ESP_LOGW(TAG, "Generating NVS encryption keys");
        ret = nvs_flash_generate_keys(key_partition, &cfg);
    }
    if (ret == ESP_OK) {
        ret = nvs_flash_secure_init(&cfg);
    }
    memset(&cfg, 0, sizeof(cfg));
    return ret;
#else
    return nvs_flash_init();
#endif
}

extern "C" void app_main(void)
{
    // Initialize NVS flash for WiFi credentials and device secrets.
    esp_err_t ret = InitNvsStorage();
    if (ret == ESP_ERR_NVS_NO_FREE_PAGES || ret == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_LOGW(TAG, "Erasing NVS flash to fix corruption");
        ESP_ERROR_CHECK(nvs_flash_erase());
        ret = InitNvsStorage();
    }
    ESP_ERROR_CHECK(ret);

    // Initialize and run the application
    auto& app = Application::GetInstance();
    app.Initialize();
    app.Run();  // This function runs the main event loop and never returns
}
