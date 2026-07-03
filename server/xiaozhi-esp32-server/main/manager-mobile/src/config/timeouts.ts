/**
 * 统一的超时与冷却时间常量（毫秒）。
 *
 * P3.3：此前 timeout 魔法数字散落在 alova 实例、chat API、配网组件与 hooks 中，
 * 数值含义靠上下文推断、难以统一调优。集中到此文件后，按「用途」而非「数字」引用，
 * 修改一处即可全局生效，也便于代码审查时判断某个超时是否合理。
 *
 * 命名约定：
 * - `*_TIMEOUT_MS`：单次请求/操作的最长等待时间。
 * - `*_COOLDOWN_MS`：两次操作之间要求的最小间隔（非超时）。
 */

/** alova 默认 API 请求超时：常规 CRUD/查询接口。 */
export const API_DEFAULT_TIMEOUT_MS = 15_000

/** LLM 对话补全超时：流式/非流式均可能长时间生成，需放宽。 */
export const CHAT_COMPLETION_TIMEOUT_MS = 120_000

/** 微信一键登录超时：覆盖 jscode2session 偶发慢响应 + 网络抖动。 */
export const LOGIN_TIMEOUT_MS = 30_000

/** token 静默刷新冷却：两次刷新间隔小于此值视为刷新无效，回退登录页以打破死循环。 */
export const REFRESH_COOLDOWN_MS = 30_000

/** 服务端地址健康探测超时：/health 应快速响应，短超时避免卡住 UI。 */
export const HEALTH_CHECK_TIMEOUT_MS = 3_000

/** BLE 设备连接超时：createBLEConnection 建链最长等待。 */
export const BLE_CONNECT_TIMEOUT_MS = 10_000

/** SoftAP 配网连通性探测超时：ESP32 热点下 /scan 探活。 */
export const SOFTAP_PROBE_TIMEOUT_MS = 3_000

/** SoftAP 配网提交/退出超时：下发 WiFi 凭据或退出配网模式。 */
export const SOFTAP_SUBMIT_TIMEOUT_MS = 15_000

/** SoftAP WiFi 扫描超时：ESP32 热点下 /scan 返回附近网络列表，较探活放宽。 */
export const SOFTAP_SCAN_TIMEOUT_MS = 10_000
